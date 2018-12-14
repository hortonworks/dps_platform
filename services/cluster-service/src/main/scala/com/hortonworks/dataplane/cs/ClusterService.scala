/*
 *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *
 *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *
 *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *   properly licensed third party, you do not have any rights to this code.
 *
 *   If this code is provided to you under the terms of the AGPLv3:
 *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *     FROM OR RELATED TO THE CODE; AND
 *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *     OR LOSS OR CORRUPTION OF DATA.
 */

package com.hortonworks.dataplane.cs

import java.util
import java.util.Optional

import com.google.inject.Guice
import com.hortonworks.datapalane.consul._
import com.hortonworks.dataplane.cs.tls.SslContextManager
import com.hortonworks.dataplane.http.{ProxyServer, Webserver}
import com.typesafe.config.{Config, ConfigFactory}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object ClusterService extends App {

  val logger = Logger("Cluster service")

  logger.info("Setting up Guice injector")
  private val injector = Guice.createInjector(AppModule)
  private val configuration = injector.getInstance(classOf[Config])
  private val sslContextManager = injector.getInstance(classOf[SslContextManager])

  private val server = injector.getInstance(classOf[Webserver])

  private val serverState = server.init

  private val proxy = injector.getInstance(classOf[ProxyServer])
  proxy.init.onComplete { _ =>
    logger.info("Proxy server started, Setting up service registry")
    // load the proxy configuration
    val proxyConfig = configuration.getConfig("dp.services.hdp_proxy")

    val registrar = new ApplicationRegistrar(
      proxyConfig,
      Optional.of(getProxyHook)
    )
    registrar.initialize()

  }

  serverState.onComplete { _ =>
    logger.info("Web service started, Setting up service registry")
    val hook = getHook
    val registrar = new ApplicationRegistrar(configuration, Optional.of(hook))
    registrar.initialize()
  }

  // This hook takes care of setting up the application correctly
  // when consul and ZUUL services are available
  // without them fallback configurations will be used
  private def getHook = {
    new ConsulHook {

      override def onServiceRegistration(dpService: DpService) = {
        logger.info(s"Registered service $dpService")
        // Service registered now, override the db service endpoint
        val map = new util.HashMap[String, String]()
        map.put("dp.services.db.service.uri", configuration.getString("dp.services.db.service.path"))
        map.put("dp.services.hdp_proxy.service.uri", configuration.getString("dp.services.hdp_proxy.service.path"))
        val gateway = new Gateway(configuration, map, Optional.of(this))
        gateway.initialize()

      }

      override def gatewayDiscovered(zuulServer: ZuulServer): Unit = {
        logger.info(s"Gateway Discovered - $zuulServer")
        sslContextManager.init()
      }


      override def gatewayDiscoverFailure(message: String,
                                          th: Throwable): Unit = {
        logger.warn(message, th)
      }

      override def serviceRegistrationFailure(serviceId: String,
                                              th: Throwable) = {
        logger.warn(s"Service registration failed for $serviceId", th)
      }

      override def onServiceDeRegister(serviceId: String): Unit =
        logger.info(s"Service removed from consul $serviceId")

      override def onRecoverableException(reason: String,
                                          th: Throwable): Unit =
        logger.warn(reason)

      override def onServiceCheck(serviceId: String): Unit =
        logger.debug(s"Running a service check for serviceId $serviceId")
    }
  }

  // Try to register the service on consul
  private def getProxyHook = {
    new ConsulHook {
      override def gatewayDiscoverFailure(message: String,
                                          th: Throwable): Unit = ???

      override def onServiceRegistration(dpService: DpService): Unit =
        logger.info(s"Service registered $dpService")

      override def onRecoverableException(reason: String,
                                          th: Throwable): Unit =
        logger.info(s"Recovered from $reason")

      override def onServiceDeRegister(serviceId: String): Unit =
        logger.info(s"Service removed $serviceId")

      override def gatewayDiscovered(zuulServer: ZuulServer): Unit = ???

      override def onServiceCheck(serviceId: String): Unit =
        logger.debug(s"Running a service check for serviceId $serviceId")

      override def serviceRegistrationFailure(serviceId: String,
                                              th: Throwable): Unit =
        logger.warn(s"Service registration failed for $serviceId", th)
    }
  }

}
