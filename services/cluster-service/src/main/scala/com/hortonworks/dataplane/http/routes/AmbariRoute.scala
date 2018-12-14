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

package com.hortonworks.dataplane.http.routes

import java.net.URL

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, entity, path, post, _}
import com.google.inject.Inject
import com.hortonworks.dataplane.CSConstants
import com.hortonworks.dataplane.commons.domain.Constants
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.cs.ClusterErrors.ClusterNotFound
import com.hortonworks.dataplane.cs._
import com.hortonworks.dataplane.cs.tls.SslContextManager
import com.hortonworks.dataplane.db.Webservice.{ClusterService, DpClusterService}
import com.hortonworks.dataplane.http.BaseRoute
import com.hortonworks.dataplane.knox.Knox.{KnoxApiRequest, KnoxConfig}
import com.hortonworks.dataplane.knox.KnoxApiExecutor
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSAuthScheme, WSClient, WSRequest, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class AmbariRoute @Inject()(
                             val storageInterface: StorageInterface,
                             val clusterService: ClusterService,
                             val credentialInterface: CredentialInterface,
                             val dpClusterService: DpClusterService,
                             val config: Config,
                             val sslContextManager: SslContextManager)
  extends BaseRoute {

  import com.hortonworks.dataplane.commons.domain.Ambari._
  import com.hortonworks.dataplane.commons.domain.Entities._
  import com.hortonworks.dataplane.http.JsonSupport._

  val logger = Logger(classOf[AmbariRoute])

  def mapToCluster(json: Option[JsValue],
                   cluster: String, dataplaneCluster: DataplaneCluster): Future[Option[AmbariCluster]] =
    Future.successful(
      json.flatMap { j =>
        val secured =
          (j \ "security_type").validate[String].getOrElse("NONE")
        val clusterVersion =
          (j \ "version").validate[String].getOrElse("HDP")
        val map = (j \ "desired_service_config_versions")
          .validate[Map[String, JsValue]]
          .getOrElse(Map())
        Some(
          AmbariCluster(security = secured,
            clusterName = cluster,
            services = map.keys.toSeq, knoxUrl = dataplaneCluster.knoxUrl, clusterType = extractVersion(clusterVersion)))
      })

  private def extractVersion(clusterVersion: String) = clusterVersion.substring(0,clusterVersion.indexOf("-"))


  private def getDetails(dataplaneCluster: DataplaneCluster, clusters: Seq[String],
                         dli: AmbariDataplaneClusterInterface)(implicit token: Option[HJwtToken])
  : Future[Seq[Option[AmbariCluster]]] = {
    val futures = clusters.map { c =>
      for {
        json <- dli.getClusterDetails(c)
        ambariCluster <- mapToCluster(json, c, dataplaneCluster)
      } yield ambariCluster
    }
    Future.sequence(futures)
  }

  def getAmbariDetails(ambariDetailRequest: AmbariDetailRequest,
                       request: HttpRequest): Future[Seq[AmbariCluster]] = {

    val header = request.getHeader(Constants.DPTOKEN)
    implicit val token =
      if (header.isPresent) Some(HJwtToken(header.get.value)) else None

    val dataplaneCluster = DataplaneCluster(
      id = None,
      name = "",
      dcName = "",
      description = "",
      ambariUrl = ambariDetailRequest.url,
      ambariIpAddress = "",
      createdBy = None,
      properties = None,
      location = None,
      knoxEnabled = Some(ambariDetailRequest.knoxDetected),
      allowUntrusted = ambariDetailRequest.allowUntrusted,
      behindGateway = false,
      knoxUrl = ambariDetailRequest.knoxUrl
    )

    val finalList = for {
      creds <- credentialInterface.getCredential(CSConstants.AMBARI_CREDENTIAL_KEY)
      dli <- Future.successful(
        AmbariDataplaneClusterInterfaceImpl(dataplaneCluster,
          sslContextManager.getWSClient(dataplaneCluster.allowUntrusted),
          config,
          creds))
      clusters <- dli.discoverClusters()
      details <- getDetails(dataplaneCluster, clusters, dli)
    } yield details

    finalList.map {
      _.collect { case o if o.isDefined => o.get }
    }

  }

  private def loadDefaultCluster(ambariDetailRequest: AmbariDetailRequest) = {
    val ws = sslContextManager.getWSClient(ambariDetailRequest.allowUntrusted)

    val clusters = ws.url(s"${ambariDetailRequest.url}/api/v1/clusters")
      .withAuth(ambariDetailRequest.ambariUser.get, ambariDetailRequest.ambariPass.get, WSAuthScheme.BASIC).get()
    clusters.map { cl =>
      val firstCluster = (cl.json \ "items").as[Seq[JsValue]].head
      (firstCluster \ "Clusters" \ "cluster_name").as[String]
    }
  }

  private def rewriteKnoxUrlFromConfigGroup(clusterName: String, ambariDetailRequest: AmbariDetailRequest, groups: WSResponse) = {
    val items = (groups.json \ "items").as[Seq[JsValue]]
    val groupIds = items.map(i => (i \ "ConfigGroup" \ "id").as[Int])
    val groupNames = groupIds.map { gid =>
      val ws = sslContextManager.getWSClient(ambariDetailRequest.allowUntrusted)

      ws.url(s"${ambariDetailRequest.url}/api/v1/clusters/$clusterName/config_groups/$gid")
        .withAuth(ambariDetailRequest.ambariUser.get, ambariDetailRequest.ambariPass.get, WSAuthScheme.BASIC).get().map { r =>
        val name = (r.json \ "ConfigGroup" \ "group_name").as[String]
        val hostName = ((r.json \ "ConfigGroup" \ "hosts").as[Seq[JsValue]].head \ "host_name").as[String]
        (name, hostName)
      }
    }

    val name = config.getString("dp.services.knox.token.config.group.name")
    val tokenHost = Future.sequence(groupNames).map { gn =>
      gn.find(_._1 == name)
    }.map { v =>
      assert(v.isDefined, s"Could not locate any config group with the name $name")
      v.get._2
    }

    //Create a new token URL using this host
    val jwtProviderUrl = new URL(ambariDetailRequest.knoxUrl.get)
    tokenHost.map(th => s"${jwtProviderUrl.getProtocol}://$th:" + jwtProviderUrl.getPort)
  }

  private def loadKnoxUrl(clusterName: String, ambariDetailRequest: AmbariDetailRequest, allowUntrusted: Boolean) = {
    // get all config group ids
    val ws = sslContextManager.getWSClient(allowUntrusted)

    val configGroups = ws.url(s"${ambariDetailRequest.url}/api/v1/clusters/$clusterName/config_groups")
      .withAuth(ambariDetailRequest.ambariUser.get, ambariDetailRequest.ambariPass.get, WSAuthScheme.BASIC).get()
    for {
      groups <- configGroups
      knoxUrl <- rewriteKnoxUrlFromConfigGroup(clusterName, ambariDetailRequest, groups)
    } yield knoxUrl

  }

  private def getTargetUrl(ambariDetailRequest: AmbariDetailRequest) = {
    for {
      clusterName <- loadDefaultCluster(ambariDetailRequest)
      newUrl <- loadKnoxUrl(clusterName, ambariDetailRequest, ambariDetailRequest.allowUntrusted)
    } yield newUrl
  }

  def getClusterData(clusterId: Long) = {
    for {
      c <- clusterService.retrieve(clusterId.toString)
      ci <- Future.successful {
        if (c.isLeft)
          throw new ClusterNotFound()
        else
          c.right.get
      }
      dpc <- dpClusterService.retrieve(ci.dataplaneClusterId.get.toString)
      dpci <- Future.successful {
        if (dpc.isLeft)
          throw new ClusterNotFound()
        else
          dpc.right.get
      }
    } yield (dpci, ci)

  }

  private def getWrappedRequest(
                                 url: String,
                                 dataplaneCluster: DataplaneCluster,
                                 hJwtToken: Option[HJwtToken]): Future[WSRequest] = {
    val ws = sslContextManager.getWSClient(dataplaneCluster.allowUntrusted)

    val baseReq = ws.url(url)
    if (knoxEnabledAndTokenPresent(dataplaneCluster, hJwtToken))
      Future.successful(baseReq)
    else {
      for {
        creds <- credentialInterface.getCredential(CSConstants.AMBARI_CREDENTIAL_KEY)
        req <- Future.successful(
          baseReq.withAuth(creds.user.get, creds.pass.get, WSAuthScheme.BASIC))
      } yield req
    }
  }

  private def knoxEnabledAndTokenPresent(dataplaneCluster: DataplaneCluster,
                                         hJwtToken: Option[HJwtToken]) = {
    logger.debug("Dump Knox data")
    logger.debug(s"Token - ${hJwtToken.isDefined}")
    logger.debug(s" Cluster - Knox enabled:${dataplaneCluster.knoxEnabled}, URL: ${dataplaneCluster.knoxUrl}")
    hJwtToken.isDefined && dataplaneCluster.knoxEnabled.isDefined && dataplaneCluster.knoxEnabled.get && dataplaneCluster.knoxUrl.isDefined
  }

  def callAmbariApi(cluster: Cluster,
                    dataplaneCluster: DataplaneCluster,
                    request: HttpRequest,
                    req: String,
                    clusterCall: Boolean = true): Future[(Int, JsValue)] = {
    logger.info("Calling ambari")
    // Prepare Knox
    val tokenInfoHeader = request.getHeader(Constants.DPTOKEN)
    val knoxConfig = KnoxConfig(
      Try(config.getString("dp.services.knox.token.topology"))
        .getOrElse("token"),
      dataplaneCluster.knoxUrl)
    val token =
      if (tokenInfoHeader.isPresent)
        Some(HJwtToken(tokenInfoHeader.get().value()))
      else None

    val ws = sslContextManager.getWSClient(dataplaneCluster.allowUntrusted)

    // Decide on the executor
    val executor =
      if (knoxEnabledAndTokenPresent(dataplaneCluster, token)) {
        logger.info(
          s"Knox was enabled and a token was detected, Ambari will be called through Knox at ${dataplaneCluster.knoxUrl.get}")
        KnoxApiExecutor(knoxConfig, ws)
      } else {
        logger.info(
          s"No knox detected/No token in context, calling Ambari with credentials")
        KnoxApiExecutor.withTokenDisabled(knoxConfig, ws)
      }

    val rest = if (req.startsWith("/")) req.substring(1) else req

    val wrappedRequest = getWrappedRequest(
      s"${getUrl(cluster, clusterCall)}/$rest",
      dataplaneCluster,
      token)

    val tokenAsString = token
      .map { t =>
        Some(t.token)
      }
      .getOrElse(None)

    val knoxResponse = for {
      wr <- wrappedRequest
      kr <- executor.execute(KnoxApiRequest(wr, { r =>
        r.get()
      }, tokenAsString))
    } yield kr

    knoxResponse.map(response => (response.status, response.json))
  }

  private def getUrl(cluster: Cluster, clusterCall: Boolean) = {
    val url = cluster.clusterUrl.get
    if (!clusterCall) {
      url.substring(0, url.indexOf("/clusters/"))
    } else
      url
  }

  private def issueAmbariCall(clusterId: Long,
                              request: HttpRequest,
                              req: String,
                              clusterCall: Boolean = true): Future[(Int, JsValue)] = {
    logger.debug(s"Ambari proxy request for $clusterId - $req")

    for {
      cluster <- getClusterData(clusterId)
      response <- callAmbariApi(cluster._2,
        cluster._1,
        request,
        req,
        clusterCall)
    } yield response
  }

  def mapToServiceInfo(json: Option[JsValue],
                       srvcName: String, srvcVersion: String): Future[Option[ServiceInfo]] =
    Future.successful(
      json
        .map { j =>
          val st =
            (j \ "state").validate[String].getOrElse("NONE")
          Some(
            ServiceInfo(serviceName = srvcName, state = st, serviceVersion = srvcVersion))
        }
        .getOrElse(None))

  def getServiceInfoSeq(services: Seq[String], dli: AmbariDataplaneClusterInterface, dataplaneCluster: DataplaneCluster, hdpVersion: String)(implicit token: Option[HJwtToken]): Future[Seq[Option[ServiceInfo]]] = {
    val stackNameVersionpair = hdpVersion.split("-")
    val stackName = stackNameVersionpair(0)
    val stackVersion = stackNameVersionpair(1)

    val list = services.map { srvc =>
      for {
        json <- dli.getServiceInfo(dataplaneCluster.name, srvc)
        serviceVersion <- dli.getServiceVersion(stackName, stackVersion, srvc)
        serviceInfo <- {
          mapToServiceInfo(json, srvc, serviceVersion)
        }
      } yield serviceInfo
    }
    Future.sequence(list)
  }

  def getAmbariServicesInfo(dpcwServices: DpClusterWithDpServices,
                            request: HttpRequest): Future[Seq[ServiceInfo]] = {

    val header = request.getHeader(Constants.DPTOKEN)
    val dataplaneCluster = dpcwServices.dataplaneCluster
    val dpServices = dpcwServices.dpServices
    implicit val token: Option[HJwtToken] =
      if (header.isPresent) Some(HJwtToken(header.get.value)) else None
    val list = for {
      creds <- credentialInterface.getCredential(CSConstants.AMBARI_CREDENTIAL_KEY)
      dli <- Future.successful(
        AmbariDataplaneClusterInterfaceImpl(dpcwServices.dataplaneCluster,
          sslContextManager.getWSClient(dataplaneCluster.allowUntrusted),
          config,
          creds))
      hdpVersion <- dli.getHdpVersion(dataplaneCluster.name)
      services <- dli.getServices(dataplaneCluster.name)
      availableDpServices <- Future.successful(services.intersect(dpServices))
      serviceInfoSeq <- getServiceInfoSeq(availableDpServices, dli, dataplaneCluster, hdpVersion.head)
    } yield serviceInfoSeq

    list.map { item =>
      item.collect { case o if o.isDefined => o.get }
    }
  }

  val ambariClusterProxy = path(LongNumber / "ambari" / "cluster") {
    clusterId =>
      pathEnd {
        extractRequest { request =>
          parameters("request") { req =>
            val ambariResponse = issueAmbariCall(clusterId, request, req)
            onComplete(ambariResponse) {
              case Success(res) =>
                complete(res._1 -> res._2)
              case Failure(th) =>
                th.getClass match {
                  case c if c == classOf[ClusterNotFound] =>
                    complete(StatusCodes.NotFound, notFound)
                  case _ =>
                    complete(StatusCodes.InternalServerError, errors(500, "cluster.ambari.generic", "A generic error occured while communicating with Ambari.", th))
                }
            }
          }
        }
      }
  }

  val ambariGenericProxy = path(LongNumber / "ambari") { clusterId =>
    pathEnd {
      extractRequest { request =>
        parameters("request") { req =>
          val ambariResponse = issueAmbariCall(clusterId, request, req, clusterCall = false)
          onComplete(ambariResponse) {
            case Success(res) =>
              complete(res._1 -> res._2)
            case Failure(th) =>
              th.getClass match {
                case c if c == classOf[ClusterNotFound] =>
                  complete(StatusCodes.NotFound, notFound)
                case _ =>
                  complete(StatusCodes.InternalServerError, errors(500, "cluster.ambari.generic", "A generic error occured while communicating with Ambari.", th))
              }
          }
        }
      }
    }
  }

  val route = path("ambari" / "details") {
    post {
      extractRequest { request =>
        entity(as[AmbariDetailRequest]) { adr =>
          val list = getAmbariDetails(adr, request)
          onComplete(list) {
            case Success(clusters) =>
              clusters.size match {
                case 0 => complete(StatusCodes.NotFound, notFound)
                case _ => complete(success(clusters))
              }
            case Failure(th) =>
              complete(StatusCodes.InternalServerError, errors(500, "cluster.ambari.generic", "A generic error occured while communicating with Ambari.", th))
          }
        }
      }
    }
  }

  val configRoute = path("ambari" / "config") {
    get {
      parameters('key, "boolean".?) { (key, dt) =>
        dt match {
          case Some("true") => if (config.getBoolean(key)) {
            complete(StatusCodes.NoContent)
          } else complete(StatusCodes.NotFound, notFound)
          case _ => complete(success(config.getString(key)))
        }
      }
    }
  }


  val serviceStateRoute = path("ambari" / "servicesInfo") {
    post {
      extractRequest { request =>
        entity(as[DpClusterWithDpServices]) { dcds =>
          val list = getAmbariServicesInfo(dcds, request)
          onComplete(list) {
            case Success(serviceInfoes) =>
              serviceInfoes.size match {
                case 0 =>
                  complete(StatusCodes.NotFound, notFound)
                case _ =>
                  complete(success(serviceInfoes))
              }
            case Failure(th) =>
              logger.error(s"Failed to get services info ", th)
              complete(StatusCodes.InternalServerError, errors(500, "cluster.ambari.generic", "A generic error occured while communicating with Ambari.", th))
          }
        }
      }
    }
  }


}
