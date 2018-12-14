/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.hortonworks.dataplane.knoxagent

import java.io.{File, FileWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.hortonworks.datapalane.consul.{Gateway, GatewayHook, ZuulServer}
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import akka.event.Logging

import scala.util.{Failure, Success, Try}
import sys.process._

object SecureStatus extends Enumeration {
  val IS_SECURE, IS_NOT_SECURE = Value
}

object KnoxAgentMain {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  private val wsClient = AhcWSClient()
  private val dpappDelegate = new DpAppDelegate(wsClient, system)
  private val logger = Logging(system, "KnoxAgent")

  def main(args: Array[String]): Unit = {
    logger.info("knox agent main started")
    process
      .onComplete { result =>
        logger.info(s"processing status=${result.toString}")
        result match {
          case Success(result) if (result == SecureStatus.IS_SECURE) =>
            wsClient.close()
            logger.info("done knox agent. Would need to update truststore and restart gateway.")
            materializer.shutdown()
            system.terminate()
            sys.exit(128 + 1);
          case Success(result) if (result == SecureStatus.IS_NOT_SECURE) =>
            wsClient.close()
            logger.info("done knox agent")
            materializer.shutdown()
            system.terminate()
            sys.exit();
          case Failure(th) =>
            wsClient.close()
            logger.info("done knox agent with error {}", th)
            materializer.shutdown()
            system.terminate()
            sys.exit(1);
        }
      }
  }
  private def process: Future[SecureStatus.Value] = {
    val config = agentConfig
    val gateway: Gateway = new Gateway(config, null, null)
    getGatewayService(gateway)
      .flatMap { gatewayService =>
        val gatewayUrl = s"http://${gatewayService.getIp}:${gatewayService.getPort}"

        processConfiguration(gatewayUrl, config)
          .flatMap {
            case SecureStatus.IS_SECURE => processTrust(gatewayUrl, config).map(_ => SecureStatus.IS_SECURE)
            case SecureStatus.IS_NOT_SECURE => Future.successful(SecureStatus.IS_NOT_SECURE)
          }
      }
  }

  private def processTrust(gatewayUrl: String, config: Config): Future[Seq[Certificate]] = {
    val pathString = config.getString("system.trust.directory")
    dpappDelegate
      .getCertificates(serviceUrl=gatewayUrl)
      .map { certs =>
        certs.foreach(cCert => {
          val name = s"${pathString.stripSuffix("/")}/${Instant.now().toString.replaceAll(":", "_")}-dp-${cCert.id.get}-${cCert.name.replaceAll("\\W","")}.crt"
          logger.info(s"Generating file ${name}")
          Files.write(Paths.get(name), cCert.data.getBytes(StandardCharsets.UTF_8))
        })
        certs
      }
      .recover {
        case th: Throwable =>
          logger.error("Certificate generation failed with {}", th)
          throw th
      }
  }

  private def processConfiguration(gatewayUrl: String, config: Config): Future[SecureStatus.Value] = {
    dpappDelegate
      .getLdapConfiguration(gatewayUrl)
      .map {
        case Some(knoxConfig) => {
          try {
            val ssoTopologyPath = config.getString("sso.topology.path")
            logger.info(s"filepath==$ssoTopologyPath")
            val result = (
              knoxConfig.useAnonymous.getOrElse(false) match {
                case true => Success(true)
                case false => updateBindPassword(config, knoxConfig)
              }
            )
              .flatMap { result =>
                val knoxSsoTopologyXml = TopologyGenerator.configure(knoxConfig)
                writeTopologyToFile(knoxSsoTopologyXml, ssoTopologyPath)
              }

            result match {
              case Success(_) => knoxConfig.ldapUrl.map { url =>
                logger.info(s"ldap url is $url")
                url.startsWith("ldaps://") match {
                  case true => SecureStatus.IS_SECURE
                  case false => SecureStatus.IS_NOT_SECURE
                }
              }.getOrElse(SecureStatus.IS_NOT_SECURE)
              case Failure(th) => throw th
            }
          } catch {
            case ex: Exception => {
              logger.error("Updating bind password failed with {}", ex)
              throw ex
            }
          }
        }
        case None => {
          logger.error("No knox configuration received.")
          throw new Exception("Knox configuration not received.")
        }
      }
  }

  private def updateBindPassword(config: Config, knoxConfig:KnoxConfig): Try[Boolean] = {
    val p = Promise[Boolean]
    val args=s" create-alias ldcSystemPassword --cluster knoxsso --value ${knoxConfig.password.get}"
    val knoxServerPath=config.getString("knox.server.path").trim
    val knoxClicommand=config.getString("knox.cli.cmd").trim
    val setPassWordCmd=s"${knoxServerPath}${knoxClicommand} ${args}"
    Try(setPassWordCmd.!)
      .map {
        case 0 => true
        case x => throw new Exception(s"'${setPassWordCmd}' failed with status $x")
      }
  }

  private def getGatewayService(gateway: Gateway): Future[ZuulServer] = {
    val p = Promise[ZuulServer]
    gateway.getGatewayService(new GatewayHook {
      override def gatewayDiscovered(zuulServer: ZuulServer): Unit = {
        logger.info(s"Received Gateway address. ${zuulServer.toString}")
        p.trySuccess(zuulServer)
      }
      override def gatewayDiscoverFailure(message: String): Unit = {
        logger.info(s"Failed to receive Gateway address with message $message")
        p.tryFailure(new Exception("could not discover gateway"))

      }
    })
    p.future
  }

  private def writeTopologyToFile(knoxSsoTopologyXml: String, filePath: String): Try[Boolean] = {
    try {
      val file = new File(filePath)
      new FileWriter(file) {
        write(knoxSsoTopologyXml)
        close()
      }
      Success(true)
    } catch {
      case ex: Throwable => {
        logger.error(ex, "error while writing topology file")
        Failure(ex)
      }
    }
  }
}

