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

import com.hortonworks.dataplane.CSConstants
import com.hortonworks.dataplane.commons.domain.Entities.{Cluster, DataplaneCluster, HJwtToken}
import com.hortonworks.dataplane.cs.tls.SslContextManager
import com.hortonworks.dataplane.knox.Knox.KnoxApiRequest
import com.hortonworks.dataplane.knox.KnoxApiExecutor
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSRequest, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AmbariClusterInterfaceV2(
    private val cluster: Cluster,
    private val dataplaneCluster: DataplaneCluster,
    private val appConfig: Config,
    private val credentialInterface: CredentialInterface,
    private val knoxApiExecutor: KnoxApiExecutor,
    private val ws: WSClient)
    extends AmbariInterfaceV2 {

  val logger = Logger(classOf[AmbariClusterInterfaceV2])

  val credentials: Future[Credentials] = credentialInterface.getCredential(CSConstants.AMBARI_CREDENTIAL_KEY)

  override def getAtlas(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, Atlas]] = {
    logger.info("Fetching Atlas information")

    val serviceSuffix =
      "/configurations/service_config_versions?service_name=ATLAS&is_current=true"

    val request =
      getWrappedRequest(s"${cluster.clusterUrl.get}$serviceSuffix", hJwtToken)
    val response =
      for {
        req <- request
        res <- knoxApiExecutor.execute(
          KnoxApiRequest(req, { req =>
                           req.get()
                         },
                         hJwtToken
                           .map { t =>
                             Some(t.token)
                           }
                           .getOrElse(None)))
      } yield res

    response
      .map { res =>
        val json = res.json
        val configurations = json \ "items" \\ "configurations"
        val configs: JsValue = configurations.head
        Right(
          Atlas(Json.stringify(
            Json.obj("stats" -> Json.obj(), "properties" -> configs))))
      }
      .recoverWith {
        case e: Exception =>
          logger.error("Cannot get Atlas info")
          Future.successful(Left(e))
      }
  }

  override def getRanger(implicit hJwtToken: Option[HJwtToken])
  : Future[Either[Throwable, Ranger]] = {
    logger.info("Fetching Ranger information")

    val hostInfoUrl = s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=RANGER_ADMIN"
    logger.info(s"$hostInfoUrl")
    val hostInfo = getWrappedRequest(hostInfoUrl, hJwtToken)

    val serviceSuffix = "/configurations/service_config_versions?service_name=RANGER&is_current=true"
    val request = getWrappedRequest(s"${cluster.clusterUrl.get}$serviceSuffix", hJwtToken)

    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val rangerInfo =
      for {
        hir <- hostInfo
        hi <- knoxApiExecutor.execute(KnoxApiRequest(hir, { r => r.get() }, tokenAsString))
        req <- request
        res <- knoxApiExecutor.execute(KnoxApiRequest(req, { req => req.get() }, tokenAsString))
      } yield {

        val hostItems = (hi.json \ "items").as[JsArray].validate[List[JsObject]].get

        val hosts = hostItems.map { h =>
          val host = (h \ "HostRoles" \ "host_name").validate[String]
          host.get
        }

        val json = res.json
        val configurations = json \ "items" \\ "configurations"
        val configs: JsValue = configurations.head

        Ranger(hosts.map(ServiceHost),
          Some(Json.obj("stats" -> Json.obj(), "properties" -> configs))
        )

      }
    rangerInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Ranger info")
        Future.successful(Left(e))
    }
  }

  override def getDpProfiler(implicit hJwtToken: Option[HJwtToken])
  : Future[Either[Throwable, DpProfiler]] = {
    logger.info("Fetching DpProfiler information")

    val hostInfoUrl = s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=DP_PROFILER_AGENT"
    logger.info(s"$hostInfoUrl")
    val hostInfo = getWrappedRequest(hostInfoUrl, hJwtToken)

    val serviceSuffix = "/configurations/service_config_versions?service_name=DPPROFILER&is_current=true"
    val request = getWrappedRequest(s"${cluster.clusterUrl.get}$serviceSuffix", hJwtToken)

    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val dpProfilerInfo =
      for {
        hir <- hostInfo
        hi <- knoxApiExecutor.execute(KnoxApiRequest(hir, { r => r.get() }, tokenAsString))
        req <- request
        res <- knoxApiExecutor.execute(KnoxApiRequest(req, { req => req.get() }, tokenAsString))
      } yield {

        val hostItems = (hi.json \ "items").as[JsArray].validate[List[JsObject]].get

        val hosts = hostItems.map { h =>
          val host = (h \ "HostRoles" \ "host_name").validate[String]
          host.get
        }

        val json = res.json
        val configurations = json \ "items" \\ "configurations"
        val configs: JsValue = configurations.head

        DpProfiler(hosts.map(ServiceHost),
          Some(Json.obj("stats" -> Json.obj(), "properties" -> configs))
        )

      }
    dpProfilerInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get DpProfiler info")
        Future.successful(Left(e))
    }
  }

  private def getWrappedRequest(
      url: String,
      hJwtToken: Option[HJwtToken]): Future[WSRequest] = {

    val baseReq = ws.url(url)
    if (hJwtToken.isDefined && dataplaneCluster.knoxEnabled.isDefined && dataplaneCluster.knoxEnabled.get && dataplaneCluster.knoxUrl.isDefined)
      Future.successful(baseReq)
    else {
      for {
        creds <- credentials
        req <- Future.successful(
          baseReq.withAuth(creds.user.get, creds.pass.get, WSAuthScheme.BASIC))
      } yield req
    }
  }

  override def getNameNodeStats(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, NameNode]] = {

    val nameNodeRequest = getWrappedRequest(
      s"${cluster.clusterUrl.get}/components/NAMENODE",
      hJwtToken)

    val nameNodeHostsApi =
      s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=NAMENODE"

    val hostInfo = getWrappedRequest(nameNodeHostsApi, hJwtToken)

    val nameNodePropertiesRequest = getWrappedRequest(
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=HDFS&is_current=true",
      hJwtToken)

    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val nameNodeInfo = for {
      hir <- hostInfo
      hi <- knoxApiExecutor.execute(KnoxApiRequest(hir, { r =>
        r.get()
      }, tokenAsString))
      nnrr <- nameNodeRequest
      nnr <- knoxApiExecutor.execute(KnoxApiRequest(nnrr, { r =>
        r.get()
      }, tokenAsString))
      nnprr <- nameNodePropertiesRequest
      nnpr <- knoxApiExecutor.execute(KnoxApiRequest(nnprr, { r =>
        r.get()
      }, tokenAsString))
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

      NameNode(hosts.map(ServiceHost),
               Some(
                 Json.obj("stats" -> serviceComponent.get,
                          "metrics" -> metrics,
                          "properties" -> configs)))
    }

    nameNodeInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Namenode info")
        Future.successful(Left(e))
    }
  }

  override def getGetHostInfo(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, Seq[HostInformation]]] = {

    val hostsApi = "/hosts"

    val hostsPath =
      s"${cluster.clusterUrl.get}$hostsApi"

    val request = getWrappedRequest(hostsPath, hJwtToken)
    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val hostsResponse: Future[WSResponse] = for {
      req <- request
      res <- knoxApiExecutor.execute(KnoxApiRequest(req, { r =>
        r.get()
      }, tokenAsString))
    } yield res

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
            val hostInfoResponse: Future[WSResponse] = for {
              req <- getWrappedRequest(s"$hostsPath/${host("host_name")}",
                                       hJwtToken)
              resp <- knoxApiExecutor.execute(KnoxApiRequest(req, { r =>
                r.get()
              }, tokenAsString))
            } yield resp

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

  override def getHs2Info(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, HiveServer]] = {
    // Get HS2 properties first
    val hiveAPI = getWrappedRequest(
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=HIVE&is_current=true",
      hJwtToken)
    val hiveHostApi = getWrappedRequest(
      s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=HIVE_SERVER",
      hJwtToken)

    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val hiveInfo = for {
      har <- hiveAPI
      hhar <- hiveHostApi
      cr <- knoxApiExecutor.execute(KnoxApiRequest(har, { r =>
        r.get()
      }, tokenAsString))
      hi <- knoxApiExecutor.execute(KnoxApiRequest(hhar, { r =>
        r.get()
      }, tokenAsString))
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

      HiveServer(
        hosts.map(ServiceHost),
        Some(Json.obj("stats" -> Json.obj(), "properties" -> configs)))
    }

    hiveInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Hive information")
        Future.successful(Left(e))
    }
  }

  override def getKnoxInfo(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, KnoxInfo]] = {
    // Dump knox properties
    val knoxApi =
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=KNOX&is_current=true"

    val request = getWrappedRequest(knoxApi, hJwtToken)
    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)
    val response = for {
      req <- request
      resp <- knoxApiExecutor.execute(KnoxApiRequest(req, { r =>
        r.get()
      }, tokenAsString))
    } yield resp

    response
      .map(
        res =>
          res.json
            .validate[JsValue]
            .map { value =>
              Right(KnoxInfo(Some(value)))
            }
            .getOrElse(Left(new Exception("Could not load Knox properties"))))
      .recoverWith {
        case e: Exception =>
          logger.error("Cannot get Knox information")
          Future.successful(Left(e))
      }

  }

  override def getBeacon(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, BeaconInfo]] = {
    // Get Beaon properties first
    val beaconApi =
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=BEACON&is_current=true"
    val beaconHostApi =
      s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=BEACON_SERVER"

    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val beaconInfo = for {
      crr <- getWrappedRequest(beaconApi, hJwtToken)
      cr <- knoxApiExecutor.execute(KnoxApiRequest(crr, { r =>
        r.get()
      }, tokenAsString))
      hir <- getWrappedRequest(beaconHostApi, hJwtToken)
      hi <- knoxApiExecutor.execute(KnoxApiRequest(hir, { r =>
        r.get()
      }, tokenAsString))
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

  override def getHdfsInfo(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, Hdfs]] = {

    val hdfsRequest = getWrappedRequest(
      s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=HDFS&is_current=true",
      hJwtToken)
    val tokenAsString = hJwtToken
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val hdfsInfo = for {
      req <- hdfsRequest
      hdfsRes <- knoxApiExecutor.execute(KnoxApiRequest(req, { r =>
        r.get()
      }, tokenAsString))
    } yield {
      val items = (hdfsRes.json \ "items").as[JsArray]
      val configs =
        (items(0) \ "configurations").as[JsArray]

      Hdfs(Seq(),
           Some(Json.obj("stats" -> Json.obj(), "properties" -> configs)))
    }

    hdfsInfo.map(Right(_)).recoverWith {
      case e: Exception =>
        logger.error("Cannot get Namenode info")
        Future.successful(Left(e))
    }

  }

}
