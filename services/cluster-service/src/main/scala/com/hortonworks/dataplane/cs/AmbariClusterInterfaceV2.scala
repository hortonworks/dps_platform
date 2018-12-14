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
import com.hortonworks.dataplane.commons.domain.Entities._
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

    val tokenAsString = hJwtToken.flatMap { t =>
      Some(t.token)
    }

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
    val tokenAsString = hJwtToken.flatMap { t =>
      Some(t.token)
    }

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

  override def getServiceInformation(service: String, component: String)(implicit hJwtToken: Option[HJwtToken]): Future[ServiceInfo] = {

    val serviceConfigurations = s"${cluster.clusterUrl.get}/configurations/service_config_versions?service_name=$service&is_current=true"
    val hostInformation = s"${cluster.clusterUrl.get}/host_components?HostRoles/component_name=$component"

    val tokenAsString = hJwtToken.map(_.token)

    (for {
      crr <- getWrappedRequest(serviceConfigurations, hJwtToken)
      cr <- knoxApiExecutor.execute(KnoxApiRequest(crr, { r => r.get }, tokenAsString))
      hir <- getWrappedRequest(hostInformation, hJwtToken)
      hi <- knoxApiExecutor.execute(KnoxApiRequest(hir, { r => r.get }, tokenAsString))
    } yield (cr.json, hi.json))
    .map { case (cr, hi) =>

      val configs: JsArray = (cr \ "items").asOpt[JsArray]
        .flatMap(_.value.headOption)
        .flatMap(item => (item \ "configurations").asOpt[JsArray])
        .getOrElse(Json.arr())

      val hosts: Seq[String] = (hi \ "items").asOpt[JsArray]
        .map {
          _
            .value
            .map(cItem => (cItem \ "HostRoles").asOpt[JsObject])
            .collect { case Some(cHostRole) => cHostRole }
            .map(cHostRole => (cHostRole \ "host_name").asOpt[String])
            .collect { case Some(cHostName) => cHostName }
        }
        .getOrElse(Nil)

      configs.value.size match {
        case 0 =>
          logger.info(s"Failed to get information for $service for cluster ${cluster.id}")
          throw WrappedErrorException(Error(500, s"Failed to get infirmation for $service for cluster ${cluster.id}", "cluster.sync.failed"))
        case _ => ServiceInfo(hosts.map(ServiceHost), Some(Json.obj("stats" -> Json.obj(), "properties" -> configs)))
      }
    }

  }
}
