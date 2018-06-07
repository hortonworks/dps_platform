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

import java.io.IOException
import java.net._
import java.util.Collections
import javax.inject.Inject
import javax.net.ssl.SSLException

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import com.google.common.annotations.VisibleForTesting
import com.google.common.net.HttpHeaders
import com.hortonworks.dataplane.CSConstants
import com.hortonworks.dataplane.commons.domain.Constants
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.commons.metrics.{MetricsRegistry, MetricsReporter}
import com.hortonworks.dataplane.cs.sync.DpClusterSync
import com.hortonworks.dataplane.cs.tls.SslContextManager
import com.hortonworks.dataplane.cs.{ClusterSync, CredentialInterface, StorageInterface}
import com.hortonworks.dataplane.http.BaseRoute
import com.hortonworks.dataplane.knox.Knox.{KnoxApiRequest, KnoxConfig}
import com.hortonworks.dataplane.knox.KnoxApiExecutor
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.apache.commons.lang3.exception.ExceptionUtils
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class ClusterRegistrationRoute @Inject()(storageInterface: StorageInterface,
                                         val credentialInterface: CredentialInterface,
                                         val config: Config,
                                         clusterSync: ClusterSync,
                                         dpClusterSync: DpClusterSync,
                                         metricsRegistry: MetricsRegistry,
                                         sslContextManager: SslContextManager)
    extends BaseRoute {

  import com.hortonworks.dataplane.commons.domain.Ambari._
  import com.hortonworks.dataplane.http.JsonSupport._
  import scala.concurrent.duration._

  val logger = Logger(classOf[ClusterRegistrationRoute])

  val TOKEN_TOPOLOGY_NAME = Try(config.getString("dp.services.knox.token.topology")).getOrElse("token")

  val FLAG_INFER_GATEWAY_SUFFIX_FROM_URL = Try(config.getBoolean("dp.services.knox.infer.gateway.name")).getOrElse(false)

  val DEFAULT_GATEWAY_SUFFIX = "gateway"

  val API_ENDPOINT = Try(config.getString("dp.service.ambari.cluster.api.prefix")).getOrElse("/api/v1/clusters")
  val NETWORK_TIMEOUT = Try(config.getInt("dp.service.ambari.status.check.timeout.secs")).getOrElse(20)

  val HD_INSIGHT_HTTP_STATUS = config.getInt("dp.service.hdinsight.auth.status")
  val HD_INSIGHT_CONTENT_TYPE = config.getString("dp.service.hdinsight.auth.challenge.contentType")
  val HD_INSIGHT_AUTH_CHALLENGE = config.getString("dp.service.hdinsight.auth.challenge.wwwAuthenticate")


  @VisibleForTesting
  def getKnoxUrlWithGatewaySuffix(uri: URL): String = {
    val suffix = FLAG_INFER_GATEWAY_SUFFIX_FROM_URL match {
      case true => Try(uri.getPath.split("/").filterNot(_.trim.isEmpty)(0)).getOrElse(DEFAULT_GATEWAY_SUFFIX)
      case false => DEFAULT_GATEWAY_SUFFIX
    }
    s"${uri.getProtocol}://${uri.getHost}:${uri.getPort}/$suffix"
  }

  /**
    *
    * @param urlString
    * @return
    *
    *  1. check if is valid uri
    */
  private def constructUrl(urlString: String): Try[URL] =
    Try(new URL(urlString))
      .recoverWith {
        case ex: MalformedURLException => Failure(WrappedErrorException(Error(500, "Host is unreachable.", "cluster.ambari.status.unreachable-host-error", trace = Some(ExceptionUtils.getStackTrace(ex)))))
      }

  /**
    * Method to check network for a host
    * @param url
    * @return true if reachable, exception otherwise
    *
    *  2. get machine address object
    *  3. check if dns resolves
    *  4. check if address is reachable
    */
  private def probeNetwork(url: URL): Try[InetAddress] = {
    Try(InetAddress.getByName(url.getHost))
      .recoverWith {
        case ex: UnknownHostException => Failure(WrappedErrorException(Error(500, "Unable to resolve host.", "cluster.ambari.status.dns-error", trace = Some(ExceptionUtils.getStackTrace(ex)))))
        case ex: IOException => Failure(WrappedErrorException(Error(500, "Host is unreachable.", "cluster.ambari.status.unreachable-host-error", trace = Some(ExceptionUtils.getStackTrace(ex)))))
      }
  }

  /**
    *
    * @param endpoint
    * @return knox login url if available
    *
    * 5. make initial call without auth to probe if cluster is secured by Knox
    *
    */
  private def probeCluster(endpoint: String, allowUntrusted: Boolean): Future[Option[String]] = {
    val ws = sslContextManager.getWSClient(allowUntrusted)

    ws.url(endpoint)
      .withRequestTimeout(NETWORK_TIMEOUT seconds)
      .get()
      .map { response =>
        response.status match {
          case 403 => {
            // This could be Knox or Ambari
            response.json.validate[AmbariForbiddenResponse] match {
              case ambariResponse: JsSuccess[AmbariForbiddenResponse] => ambariResponse.get.jwtProviderUrl
              case _: JsError => throw WrappedErrorException(Error(500, "Unable to deserialize JSON. Is there a non-transparent proxy in front of Ambari/Knox?.", "cluster.ambari.status.invalid-json-response"))
            }
          }
          case 401 => {
            // this could be HD Insight
            val authChallenge = response.header(HttpHeaders.WWW_AUTHENTICATE)
            val contentType = response.header(HttpHeaders.CONTENT_TYPE)

            (contentType, authChallenge) match {
              case (Some(HD_INSIGHT_CONTENT_TYPE), Some(HD_INSIGHT_AUTH_CHALLENGE)) => {
                logger.info(s"$endpoint is an HD Insight cluster. We would be using basic authentication with this.")
                None
              }
              case _ => throw WrappedErrorException(Error(500, "We recieved an unexpected response from cluster. The cluster returned a 401 and hence should have been an HD Insight cluster, but was not.", "cluster.ambari.status.unexpected-response-hd-insight"))
            }
          }
          case _ => throw WrappedErrorException(Error(500, s"Unexpected Response from Ambari, expected 403 or 401, actual ${response.status}", "cluster.ambari.status.unexpected-response-for-prereq-call"))
        }
      }
      .recoverWith {
        case ex: ConnectException => throw WrappedErrorException(Error(500, "Connection to remote address was refused.", "cluster.ambari.status.connection-refused"))
      }
  }

  /**
    *
    * @param endpoint
    * @param knoxUrl
    * @param token
    * @return
    *
    * 6A. if response indicates Knox
    * *A7. repeat steps 1-4 to confirm reachability
    * *A8. make Knox calls to check condition of Knox
    *
    */
  private def probeKnox(endpoint: String, knoxUrl: String, token: String, allowUntrusted: Boolean): Future[JsValue] = {
    val ws = sslContextManager.getWSClient(allowUntrusted)

    val delegatedRequest = ws.url(endpoint).withRequestTimeout(NETWORK_TIMEOUT seconds)

    val response =
      KnoxApiExecutor.withExceptionHandling(KnoxConfig(TOKEN_TOPOLOGY_NAME, Some(knoxUrl)), ws)
        .execute(KnoxApiRequest(delegatedRequest, (req => req.get), Some(token)))

    response
      .map { res =>
        res.status match {
          case 200 => res.json
          case 302 => throw WrappedErrorException(Error(500, "Knox token or the certificate on cluster might be corrupted.", "cluster.ambari.status.knox.public-key-corrupted-or-bad-auth"))
          case 403 => throw WrappedErrorException(Error(403, "User does not have required rights. Please disable or configure Ranger to add roles or log-in as another user.", "cluster.ambari.status.knox.ranger-rights-unavailable"))
          case 404 => throw WrappedErrorException(Error(500, "Knox token topology is not validated and deployment descriptor is not created.", "cluster.ambari.status.knox.configuration-error"))
          case 500 => throw WrappedErrorException(Error(500, "Knox certificate on cluster might be corrupted.", "cluster.ambari.status.knox.public-key-corrupted"))
          case _ => throw WrappedErrorException(Error(500, s"Unknown error. Server returned ${res.status}", "cluster.ambari.status.knox.genric"))
        }
      }
      .recoverWith {
        case ex: SSLException => throw WrappedErrorException(Error(500, "TLS error. Please ensure your Knox server has been configured with a correct keystore. If you are using self-signed certificates, you would need to get it added to Dataplane truststore.", "cluster.ambari.status.knox.tls-error"))
        case ex: ConnectException if ExceptionUtils.indexOfThrowable(ex, classOf[SSLException]) >= 0 => throw WrappedErrorException(Error(500, "TLS error. Please ensure your Knox server has been configured with a correct keystore. If you are using self-signed certificates, you would need to get it added to Dataplane truststore.", "cluster.ambari.status.knox.tls-error"))
        case ex: ConnectException => throw WrappedErrorException(Error(500, "Connection to remote address was refused.", "cluster.ambari.status.knox.connection-refused"))
      }
  }

  /**
    *
    * @param endpoint
    * @return
    *
    * 6B. if response indicates absence of Knox (naked Ambari)
    * *B7. check keystore for seeded user credentials
    * *B8. attempt ambari connection
    */
  private def probeAmbari(endpoint: String, allowUntrusted: Boolean): Future[JsValue] = {
    credentialInterface.getCredential(CSConstants.AMBARI_CREDENTIAL_KEY)
      .map { credential =>
        (credential.user, credential.pass) match {
          case (Some(username), Some(password)) => (username, password)
          case _ => throw new WrappedErrorException(Error(500, "There is no credential configured for Ambari default user", "cluster.ambari.status.raw.credential-not-found"))
        }
      }
      .flatMap { case (username, password) =>
        val ws = sslContextManager.getWSClient(allowUntrusted)

        ws.url(endpoint)
          .withAuth(username, password, WSAuthScheme.BASIC)
          .withRequestTimeout(NETWORK_TIMEOUT seconds)
          .get()
      }
      .map { response =>
        response.status match {
          case 200 => response.json
          case 401 => throw WrappedErrorException(Error(401, "User needs to be authenticated.", "cluster.ambari.status.raw.unauthorized"))
          case 403 => throw WrappedErrorException(Error(403, "User does not have required rights. Please log-in as another user.", "cluster.ambari.status.raw.forbidden"))
          case 404 => throw WrappedErrorException(Error(404, "We were unable to find the enpoint you were querying.", "cluster.ambari.status.raw.not-found"))
          case 500 => throw WrappedErrorException(Error(500, "An internal server error occurred.", "cluster.ambari.status.raw.server-error"))
          case _ => throw WrappedErrorException(Error(500, "Unknown error.", "cluster.ambari.status.raw.generic"))
        }
      }
      .recoverWith {
        case ex: SSLException => throw WrappedErrorException(Error(500, "TLS error. Please ensure your Ambari server has been configured with a correct keystore. If you are using self-signed certificates, you would need to get it added to Dataplane truststore.", "cluster.ambari.status.raw.tls-error"))
        case ex: ConnectException if ExceptionUtils.indexOfThrowable(ex, classOf[SSLException]) >= 0 => throw WrappedErrorException(Error(500, "TLS error. Please ensure your Knox server has been configured with a correct keystore. If you are using self-signed certificates, you would need to get it added to Dataplane truststore.", "cluster.ambari.status.knox.tls-error"))
        case ex: ConnectException => throw WrappedErrorException(Error(500, "Connection to remote address was refused.", "cluster.ambari.status.raw.connection-refused"))
      }
  }


  private def probeAccessViaKnox(href: String, knoxUrl: String, token: String, allowUntrusted: Boolean): Future[WSResponse] = {
    val ws = sslContextManager.getWSClient(allowUntrusted)

    val delegatedRequest = ws.url(href).withRequestTimeout(NETWORK_TIMEOUT seconds)

    KnoxApiExecutor.withExceptionHandling(KnoxConfig(TOKEN_TOPOLOGY_NAME, Some(knoxUrl)), ws)
      .execute(KnoxApiRequest(delegatedRequest, (req => req.get), Some(token)))
  }


  private def probeAccessViaAmbari(href: String, allowUntrusted: Boolean): Future[WSResponse] = {
    credentialInterface.getCredential(CSConstants.AMBARI_CREDENTIAL_KEY)
      .map { credential =>
        (credential.user, credential.pass) match {
          case (Some(username), Some(password)) => (username, password)
          case _ => throw new WrappedErrorException(Error(500, "There is no credential configured for Ambari default user", "cluster.ambari.status.raw.credential-not-found"))
        }
      }
      .flatMap { case (username, password) =>
        val ws = sslContextManager.getWSClient(allowUntrusted)

        ws.url(href)
          .withAuth(username, password, WSAuthScheme.BASIC)
          .withRequestTimeout(NETWORK_TIMEOUT seconds)
          .get()
      }
  }


  private def probeAccess(clusterHrefs: Seq[String], knoxUrl: Option[String] = None, token: Option[String] = None, allowUntrusted: Boolean): Future[Unit] = {
    Future
      .fromTry(clusterHrefs.size match {
        case 0 => Failure(WrappedErrorException(Error(404, "There are no clusters attached to this Ambari instance.", "cluster.ambari.status.detail.0-clusters-on-ambari")))
        case 1 => Success(clusterHrefs.head)
        case _ => Failure(WrappedErrorException(Error(404, "There are more than one clusters attached to this Ambari instance. This should not happen.", "cluster.ambari.status.detail.more-than-1-clusters-on-ambari")))
      })
      .flatMap { href =>
        (knoxUrl, token) match {
          case (Some(knoxUrl), Some(token)) => probeAccessViaKnox(href, knoxUrl, token, allowUntrusted)
          case (None, None) => probeAccessViaAmbari(href, allowUntrusted)
          case (_, _) => Future.failed(WrappedErrorException(Error(500, "Unknown error: One of Knox URL or Token is unavailable. This should not happen.", "cluster.ambari.status.detail.missing-data")))
        }
      }
      .flatMap { res =>
        res.status match {
          case 403 => Future.failed(WrappedErrorException(Error(403, "User does not have required rights to access this cluster. Please ask your Ambari administrator to grant required rights.", "cluster.ambari.status.detail.access-detail-rights-unavailable")))
          case _ => Future.successful()
        }
      }
      .recoverWith {
        case ex: WrappedErrorException => Future.failed(ex)
        case ex: Exception => Future.successful()
      }
  }

  private def probeStatus(knoxUrlAsOptional: Option[String], endpoint: String, request: HttpRequest, allowUntrusted: Boolean): Future[AmbariCheckResponse] = {
    knoxUrlAsOptional match {
      case Some(knoxUrl) => {
        //        knox
        val tokenTryable = Try(request.getHeader(Constants.DPTOKEN).get.value)
          .recoverWith {
            case ex: NoSuchElementException => throw WrappedErrorException(Error(400, "Ambari was Knox protected but no JWT token was sent with request", "cluster.ambari.status.knox.jwt-token-missing"))
          }

        for {
          url <- Future.fromTry(constructUrl(knoxUrl))
          inet <- Future.fromTry(probeNetwork(url))
          knoxUrl <- Future.successful(getKnoxUrlWithGatewaySuffix(url))
          token <- Future.fromTry(tokenTryable)
          json <- probeKnox(endpoint, knoxUrl, token, allowUntrusted)
          hrefs <- Future.successful((json \ "items" \\ "href").map(_.asOpt[String]).toList.filter(_.isDefined).map(_.get))
          _ <- probeAccess(hrefs, Some(knoxUrl), Some(token), allowUntrusted)
        } yield AmbariCheckResponse(ambariApiCheck = true,
          knoxDetected = true,
          ambariApiStatus = 200,
          knoxUrl = Some(knoxUrl),
          ambariIpAddress = inet.getHostAddress,
          ambariApiResponseBody = json)
      }
      case None => {
        //        ambari
        for {
          url <- Future.fromTry(constructUrl(endpoint))
          inet <- Future.fromTry(probeNetwork(url))
          json <- probeAmbari(endpoint, allowUntrusted)
          hrefs <- Future.successful((json \ "items" \\ "href").map(_.asOpt[String]).toList.filter(_.isDefined).map(_.get))
          _ <- probeAccess(hrefs, allowUntrusted = allowUntrusted)
        } yield AmbariCheckResponse(ambariApiCheck = true,
          knoxDetected = false,
          ambariApiStatus = 200,
          knoxUrl = None,
          ambariIpAddress = inet.getHostAddress,
          ambariApiResponseBody = json)
      }
    }
  }

  /**
    *
    * @param urlString
    * @return
    *
    *  1. check if is valid uri
    *  2. get machine address object
    *  3. check if dns resolves
    *  4. check if address is reachable
    *  5. make initial call without auth
    *  6.
    *  6A. if response indicates Knox
    *  *A7. repeat steps 1-4 to confirm reachability
    *  *A8. make Knox calls to check condition of Knox
    *  6B. if response indicates absence of Knox (naked Ambari)
    *  *B7. check keystore for seeded user credentials
    *  *B8. attempt ambari connection
    *
    */
  private def probe(urlString: String, request: HttpRequest, allowUntrusted: Boolean): Future[AmbariCheckResponse] = {
    for {
      url <- Future.fromTry(constructUrl(urlString))
      inet <- Future.fromTry(probeNetwork(url))
      endpoint <- Future.successful(s"${url.toString}$API_ENDPOINT")
      knoxUrlAsOptional <- probeCluster(endpoint, allowUntrusted)
      response <- probeStatus(knoxUrlAsOptional, endpoint, request, allowUntrusted)
      //    TODO: remove this hack
      transformed <- Future.successful(response.copy(ambariIpAddress = new URL(url.getProtocol, inet.getHostAddress, url.getPort, url.getFile).toString))
    } yield transformed
  }

  lazy val ambariConnectTimer =
    metricsRegistry.newTimer("ambari.status.request.time")

  val route =
    path("ambari" / "status") {
      val context = ambariConnectTimer.time()
      extractRequest { request =>
        get {
          parameters("url".as[String], "allowUntrusted".as[Boolean], "behindGateway".as[Boolean]) {
            (url, allowUntrusted, behindGateway) =>
              onComplete(probe(url, request, allowUntrusted)) {
                case Success(res) =>
                  context.stop()
                  complete(success(Json.toJson(res)))
                case Failure(ex) =>
                  context.stop()
                  logger.info("ex", ex)
                  ex match {
                    case ex: WrappedErrorException => complete(ex.error.status -> Json.toJson(Errors(Seq(ex.error))))
                    case ex: Exception => complete(StatusCodes.InternalServerError, errors(500, "cluster.ambari.status.generic", "Generic error while communicating with Ambari.", ex))
                  }
              }
          }
        }
      }
    }

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  val sync =
    path("cluster" / "sync") {
      extractRequest { request =>
        post {
          entity(as[DataplaneClusterIdentifier]) { dl =>
            val header = request.getHeader(Constants.DPTOKEN)
            val token =
              if (header.isPresent) Some(HJwtToken(header.get().value()))
              else None
            onComplete(dpClusterSync.triggerSync(dl.id, token)) { response =>
              complete(success(Map("status" -> 200)))
            }
          }
        }
      }
    }

  val health = path("health") {
    pathEndOrSingleSlash {
      get {
        complete(success(Map("status" -> 200)))
      }
    }
  }

  val status = path("status") {
    pathEndOrSingleSlash {
      get {
        complete(success(Map("status" -> 200)))
      }
    }
  }

import scala.collection.JavaConverters._

  val metrics = path("metrics") {
    implicit val mr: MetricsRegistry = metricsRegistry
    pathEndOrSingleSlash {
      parameterMultiMap { params =>
          get {
            val paramsSet = params.get(MetricsReporter.prometheusNameParam).map(_.toSet.asJava).getOrElse(Collections.emptySet())
            val exported = params.get(MetricsReporter.exportParam).getOrElse(Seq())
            if (!exported.isEmpty && exported.head == MetricsReporter.prometheus) {
              complete(MetricsReporter.asPrometheusTextExport(paramsSet))
            } else
              complete(MetricsReporter.asJson)
          }
      }
    }
  }

}
