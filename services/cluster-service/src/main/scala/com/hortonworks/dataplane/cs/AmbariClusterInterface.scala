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

import java.net.{MalformedURLException, URL}

import com.hortonworks.dataplane.commons.domain.Entities.Cluster
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class AmbariClusterInterface(
    private val cluster: Cluster,
    private val credentials: Credentials,
    private val appConfig: Config,
    private val ws: WSClient)
    extends AmbariInterface {

  val logger = Logger(classOf[AmbariClusterInterface])

  override def ambariConnectionCheck: Future[AmbariConnection] = {
    // use the cluster definition to get Ambari
    // hit Ambari API clusters interface to check connectivity
    logger.info("Starting Ambari connection check")
    // preconditions
    require(cluster.clusterUrl.isDefined, "No Ambari URL defined")
    require(credentials.user.isDefined, "No Ambari user defined")
    require(credentials.pass.isDefined, "No Ambari password defined")
    require(
      if (isClusterKerberized)
        cluster.kerberosuser.isDefined && cluster.kerberosticketLocation.isDefined
      else true,
      "Secure cluster added but Kerberos user/ticket not defined"
    )
    val url = Try(new URL(cluster.clusterUrl.get))
    require(url.isSuccess, "registered Ambari url is invalid")

    //Hit ambari URL
    ws.url(s"${url.get.toString}")
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()
      .map { res =>
        logger.info(s"Successfully connected to Ambari $res")
        if (res.status != 200)
          logger.warn(
            s"Ambari connection works but received a ${res.status} as response" +
              s"This may cause future operations to fail")

        val kerberos =
          if (isClusterKerberized)
            Some(
              Kerberos(cluster.kerberosuser.get,
                       cluster.kerberosticketLocation.get))
          else None

        AmbariConnection(status = true, url.get, kerberos, None)
      }
      .recoverWith {
        case e: Exception =>
          logger.error(s"Could not connect to Ambari, reason: ${e.getMessage}",
                       e)
          Future.successful(
            AmbariConnection(status = false, url.get, None, Some(e)))
      }

  }

  private def isClusterKerberized =
    cluster.secured.isDefined && cluster.secured.get

  override def getAtlas: Future[Either[Throwable, Atlas]] = {
    logger.info("Trying to get data from Atlas")

    val serviceSuffix =
      "/configurations/service_config_versions?service_name=ATLAS&is_current=true"
    ws.url(s"${cluster.clusterUrl.get}$serviceSuffix")
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()
      .map { res =>
        val json = res.json
        val configurations = json \ "items" \\ "configurations"
        val configs: JsValue = configurations.head
        Right(Atlas(Json.stringify(Json.obj("stats" -> Json.obj(), "properties" -> configs))))
      }
      .recoverWith {
        case e: Exception =>
          logger.error("Cannot get Atlas info")
          Future.successful(Left(e))
      }
  }

  override def getNameNodeStats: Future[Either[Throwable, NameNode]] = {

    val nameNodeResponse = ws
      .url(s"${cluster.clusterUrl.get}/components/NAMENODE")
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()

    val nameNodeHostsApi =
      s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=NAMENODE"

    val hostInfo = ws
      .url(nameNodeHostsApi)
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()

    val nameNodePropertiesResponse = ws
      .url(
        s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=HDFS&is_current=true")
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()

    val nameNodeInfo = for {
      hi <- hostInfo
      nnr <- nameNodeResponse
      nnpr <- nameNodePropertiesResponse
    } yield {

      val serviceComponent = nnr.json \ "ServiceComponentInfo"
      val hostItems =
        (hi.json \ "items").as[JsArray].validate[List[JsObject]].get

      val hosts = hostItems.map { h =>
        val host = (h \ "HostRoles" \ "host_name").validate[String]
        host.get
      }

      val metrics = (nnr.json \ "metrics").getOrElse(Json.obj())

      val items = (nnpr.json \ "items").as[JsArray]
      val configs =
        (items(0) \ "configurations").as[JsArray]

      NameNode(
        hosts.map(ServiceHost),
        Some(
          Json.obj("stats" -> serviceComponent.get,"metrics" -> metrics, "properties" -> configs)))
    }

    nameNodeInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Namenode info")
        Future.successful(Left(e))
    }
  }

  override def getGetHostInfo: Future[Either[Throwable, Seq[HostInformation]]] = {

    val hostsApi = "/hosts"

    val hostsPath =
      s"${cluster.clusterUrl.get}${hostsApi}"

    val hostsResponse: Future[WSResponse] = ws
      .url(hostsPath)
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()
    //Load Cluster Info
    hostsResponse
      .flatMap { hres =>
        val hostsList = (hres.json \ "items" \\ "Hosts").map(
          _.as[JsObject]
            .validate[Map[String, String]]
            .map(m => Some(m))
            .getOrElse(None))
        val listHosts = hostsList.map { hostOpt =>
          val opt: Option[Future[HostInformation]] = hostOpt.map { host =>
            // for each host get host and disk info
            val hostInfoResponse: Future[WSResponse] = ws
              .url(s"${hostsPath}/${host.get("host_name").get}")
              .withAuth(credentials.user.get,
                        credentials.pass.get,
                        WSAuthScheme.BASIC)
              .get()

            val futureHost: Future[HostInformation] = hostInfoResponse.map {
              hir =>
                val hostNode = hir.json \ "Hosts"
                val state = (hostNode \ "host_state")
                  .validate[String]
                  .map(s => s)
                  .getOrElse("")
                val status = (hostNode \ "host_status")
                  .validate[String]
                  .map(s => s)
                  .getOrElse("")
                val hostName =
                  (hostNode \ "host_name")
                    .validate[String]
                    .map(s => s)
                    .getOrElse("")
                val ip =
                  (hostNode \ "ip").validate[String].map(s => s).getOrElse("")
                HostInformation(state,
                                s"$status/$state",
                                hostName,
                                ip,
                                hostNode.toOption)
            }
            // the host info
            futureHost
          }

          // the optional wrap
          opt
        }
        // get Rid of invalid cases
        val toReturn = listHosts.collect {
          case s: Some[Future[HostInformation]] => s.get
        }

        // Invert the futures
        val sequenced = Future.sequence(toReturn)

        // Build the response
        sequenced.map(Right(_))
      }
      .recoverWith {
        case e: Exception =>
          logger.error("Cannot get host or disk info")
          Future.successful(Left(e))
      }
  }

  override def getHs2Info: Future[Either[Throwable, HiveServer]] = {
    // Get HS2 properties first
    val hiveAPI =
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=HIVE&is_current=true"
    val hiveHostApi =
      s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=HIVE_SERVER"

    val configResponse = ws
      .url(hiveAPI)
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()
    val hostInfo = ws
      .url(hiveHostApi)
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()

    val hiveInfo = for {
      cr <- configResponse
      hi <- hostInfo
    } yield {

      val hostItems =
        (hi.json \ "items").as[JsArray].validate[List[JsObject]].get

      val hosts = hostItems.map { h =>
        val host = (h \ "HostRoles" \ "host_name").validate[String]
        host.get
      }

      val items = (cr.json \ "items").as[JsArray]
      val configs =
        (items(0) \ "configurations").as[JsArray]

      HiveServer(hosts.map(ServiceHost), Some(Json.obj("stats" -> Json.obj(), "properties" -> configs)))
    }

    hiveInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Hive information")
        Future.successful(Left(e))
    }
  }

  override def getKnoxInfo: Future[Either[Throwable, KnoxInfo]] = {
    // Dump knox properties
    val knoxApi =
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=KNOX&is_current=true"
    ws.url(knoxApi)
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()
      .map(
        res =>
          res.json
            .validate[JsValue]
            .map { js =>
              Right(KnoxInfo(None))
            }
            .getOrElse(Left(new Exception("Could not load Knox properties"))))
      .recoverWith {
        case e: Exception =>
          logger.error("Cannot get Knox information")
          Future.successful(Left(e))
      }

  }

  override def getBeacon: Future[Either[Throwable, BeaconInfo]] = {
    // Get Beaon properties first
    val beaconApi =
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=BEACON&is_current=true"
    val beaconHostApi =
      s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=BEACON_SERVER"

    val configResponse = ws
      .url(beaconApi)
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()
    val hostInfo = ws
      .url(beaconHostApi)
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()

    val beaconInfo = for {
      cr <- configResponse
      hi <- hostInfo
    } yield {

      val items = (cr.json \ "items").as[JsArray]
      val configs =
        (items(0) \ "configurations").as[JsArray]

      val hostItems =
        (hi.json \ "items").as[JsArray].validate[List[JsObject]].get

      val hosts = hostItems.map { h =>
        val host = (h \ "HostRoles" \ "host_name").validate[String]
        host.get
      }

      BeaconInfo(
        Some(Json.obj("stats" -> Json.obj(), "properties" -> configs)),
        hosts.map(ServiceHost))
    }

    beaconInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Beacon information")
        Future.successful(Left(e))
    }
  }

  override def getHdfsInfo: Future[Either[Throwable, Hdfs]] = {

    val hdfsResponse = ws
      .url(
        s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=HDFS&is_current=true")
      .withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
      .get()

    val hdfsInfo = for {
      nnpr <- hdfsResponse
    } yield {
      val items = (nnpr.json \ "items").as[JsArray]
      val configs =
        (items(0) \ "configurations").as[JsArray]

      Hdfs(Seq(), Some(Json.obj("stats" -> Json.obj(), "properties" -> configs)))
    }

    hdfsInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Namenode info")
        Future.successful(Left(e))
    }

  }

}
