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
import java.time.Instant
import javax.inject.Inject

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import com.hortonworks.dataplane.commons.domain.Constants.{PROFILER, DPTOKEN}
import akka.http.scaladsl.server.Directives.{as, _}
import com.hortonworks.dataplane.commons.domain.Ambari.{ClusterServiceWithConfigs, ConfigType}
import com.hortonworks.dataplane.commons.domain.Entities.BodyToModifyAssetColumnTags
import com.hortonworks.dataplane.commons.service.api.ServiceNotFound
import com.hortonworks.dataplane.cs.{ClusterDataApi, KnoxProxyWsClient, StorageInterface}
import com.hortonworks.dataplane.db.Webservice.{ClusterComponentService, ClusterHostsService}
import com.hortonworks.dataplane.http.BaseRoute

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import play.api.libs.json._
import com.hortonworks.dataplane.http.JsonSupport._
import com.typesafe.config.Config
import play.api.libs.ws.WSResponse
import com.hortonworks.dataplane.commons.domain.profiler.models.Requests.ProfilerMetricRequest
import com.hortonworks.dataplane.commons.domain.profiler.parsers.RequestParser._
import com.hortonworks.dataplane.commons.domain.profiler.parsers.ResponseParser._
import com.hortonworks.dataplane.cs.profiler.{GlobalProfilerConfigs, MetricRetriever}

import scala.concurrent.ExecutionContext.Implicits.global


class DpProfilerRoute @Inject()(private val clusterComponentService: ClusterComponentService,
                                private val clusterHostsService: ClusterHostsService,
                                private val storageInterface: StorageInterface,
                                private val clusterDataApi: ClusterDataApi,
                                private val config:Config)(implicit val ws: KnoxProxyWsClient) extends BaseRoute {

  val startJob =
    path ("cluster" / LongNumber / "dp-profiler" / "start-job" / Segment / Segment) { (clusterId, dbName, tableName) =>
      extractRequest { request =>
        get {
          val token = extractToken(request)
          onComplete(postJob(clusterId, token, dbName, tableName)) {
            case Success(res) => res.status match {
              case 200 => complete(success(res.json))
              case 404 => complete(StatusCodes.NotFound, notFound)
            }
            case Failure(th) => th match {
              case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
              case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
            }
          }
        }
      }
    }

  val startAndScheduleJob =
    path("cluster" / LongNumber / "dp-profiler" / "start-schedule-job") { clusterId =>
      extractRequest { request =>
        post {
          entity(as[JsObject]) { js =>
            var list = (js \ "list").as[Seq[String]]
            var jobName = (js \ "jobName").as[String]

            val token = extractToken(request)
            onComplete(postAndScheduleJob(clusterId, token, jobName, list)) {
              case Success(res) => res.status match {
                case 200 => complete(success(res.json))
                case 404 => complete(StatusCodes.NotFound, notFound)
                case _ => complete(res.status)
              }
              case Failure(th) => th match {
                case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
                case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
              }
            }
          }
        }
      }
    }

  val datasetAssetMapping =
    path("cluster" / LongNumber / "dpprofiler" / "datasetasset" / Segment) { (clusterId,datasetname) =>
      extractRequest { request =>
        post {
          entity(as[JsObject]) { js =>
            var assetIds = (js \ "assetIds").as[Seq[String]]

            val token = extractToken(request)
            onComplete(doDatasetAssetMapping(clusterId, token, assetIds, datasetname)) {
              case Success(res) => res.status match {
                case 200 => complete(success(res.json))
                case 404 => complete(StatusCodes.NotFound, notFound)
                case _ => complete(res.status)
              }
              case Failure(th) => th match {
                case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
                case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
              }
            }
          }
        }
      }
    }

  val datasetProfiledAssetCount =
    path("cluster" / LongNumber / "dpprofiler" / "datasetasset" / Segment / "assetcount") { (clusterId,datasetname) =>
      extractRequest { request =>
        get {
          parameters("profilerInstanceName".as[String],
            "startTime".as[Long],
            "endTime".as[Long]) { (profilerInstanceName, startTime, endTime) =>

            val token = extractToken(request)
            onComplete(getDatasetProfiledAssetsCount(clusterId, token, profilerInstanceName, datasetname, startTime, endTime)) {
              case Success(res) => res.status match {
                case 200 => complete(success(res.json))
                case 404 => complete(StatusCodes.NotFound, notFound)
                case _ => complete(res.status)
              }
              case Failure(th) => th match {
                case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
                case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
              }
            }
          }
        }
      }
    }

  val getExistingProfiledAssetCount =
    path("cluster" / LongNumber / "dpprofiler" / "assets") { clusterId =>
      extractRequest { request =>
        get {
          val queryString = request.uri.queryString()

          val token = extractToken(request)
          onComplete(getFromProfiler(clusterId, token, "/assets", queryString.getOrElse(""))) {
            case res => mapResponse(res)
          }
        }
      }
    }

  val profilersLastRunInfoOnAsset =
    path("cluster" / LongNumber / "dpprofiler" / "assets" / Segment / "profilerslastrun") { (clusterId,assetId) =>
      extractRequest { request =>
        get {
          val token = extractToken(request)
          onComplete(getProfilersLastRunInfoOnAsset(clusterId, token, assetId)) {
            case Success(res) => res.status match {
              case 200 => complete(success(res.json))
              case 404 => complete(StatusCodes.NotFound, notFound)
              case _ => complete(res.status)
            }
            case Failure(th) => th match {
              case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
              case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
            }
          }
        }
      }
    }

  val jobStatus =
    path ("cluster" / LongNumber / "dp-profiler" / "job-status" / Segment / Segment) { (clusterId, dbName, tableName) =>
      extractRequest { request =>
        get {
          val token = extractToken(request)
          onComplete(getJobStatus(clusterId, token, dbName, tableName)) {
            case Success(res) => res.status match {
              case 200 => complete(success(res.json))
              case 404 => complete(StatusCodes.NotFound, notFound)
            }
            case Failure(th) => th match {
              case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
              case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
            }
          }
        }
      }
    }

  val scheduleInfo =
    path ("cluster" / LongNumber / "dp-profiler" / "schedule-info" / Segment) { (clusterId, taskName) =>
      extractRequest { request =>
        get {
          val token = extractToken(request)
          onComplete(getScheduleInfo(clusterId, token, taskName)) {
            case Success(res) => res.status match {
              case 200 => complete(success(res.json.as[Seq[JsObject]].headOption)) //TODO fix it once gaurav fixes profiler response
              case 404 => complete(StatusCodes.NotFound, notFound)
              case _ => complete(res.status)
            }
            case Failure(th) => th match {
              case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
              case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
            }
          }
        }
      }
    }

  val jobDelete =
    path ("cluster" / LongNumber / "dp-profiler" / "profilers") { clusterId: Long =>
      extractRequest { request =>
        delete {
          parameters('jobName.as[String]) { jobName =>

            val token = extractToken(request)
            onComplete(deleteProfilerByJobName(clusterId, token, jobName)) {
              case Success(res) => res.status match {
                case 200 => complete(success(res.json))
                case 404 => complete(StatusCodes.NotFound, notFound)
                case _ => complete(StatusCodes.InternalServerError, badRequest)
              }
              case Failure(th) => th match {
                case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
                case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
              }
            }
          }
        }
      }
    }


  val auditResults =
    path ("cluster" / LongNumber / "dp-profiler" / "audit-results" / Segment / Segment / Segment / Segment) { (clusterId, dbName, tableName, startDate, endDate) =>
      extractRequest { request =>
        get {
          parameters('userName.?) { userName =>

            val token = extractToken(request)
            onComplete(getAuditResults(clusterId, token, dbName, tableName, userName.get, startDate, endDate)) {
              case Success(res) => res.status match {
                case 202 => complete(success(res.json))
                case 404 => complete(StatusCodes.NotFound, notFound)
                case 503 => complete(StatusCodes.ServiceUnavailable, serverError)
                case _ => complete(StatusCodes.InternalServerError, serverError)
              }
              case Failure(th) => th match {
                case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
                case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
              }
            }
          }
        }
      }
    }

  val auditActions =
    path ("cluster" / LongNumber / "dp-profiler" / "audit-actions" / Segment / Segment / Segment / Segment) { (clusterId, dbName, tableName, startDate, endDate) =>
      extractRequest { request =>
        get {
          parameters('userName.?) { userName =>

            val token = extractToken(request)
            onComplete(getAuditActions(clusterId, token, dbName, tableName, userName.get, startDate, endDate)) {
              case Success(res) => res.status match {
                case 202 => complete(success(res.json))
                case 404 => complete(StatusCodes.NotFound, notFound)
                case 503 => complete(StatusCodes.ServiceUnavailable, serverError)
                case _ => complete(StatusCodes.InternalServerError, serverError)
              }
              case Failure(th) => th match {
                case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
                case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
              }
            }
          }
        }
      }
    }

  val postAssetColumnClassifications =
    path("cluster" / LongNumber / "dp-profiler" / "assets" / "classifications") { clusterId =>
      extractRequest { request =>
        post {
          entity(as[JsObject]) { body =>
            val token = extractToken(request)
            onComplete(postOnProfiler(clusterId, token, "/assets/tags", "", body)) {
              case res => mapResponse(res)
            }
          }
        }
      }
    }


  val putProfilerState =
    path("cluster" / LongNumber / "dp-profiler" / "profilerinstances" / "state" ) { (clusterId: Long) =>
      extractRequest { request =>
        put {
          val queryString = request.uri.queryString()

          val token = extractToken(request)
          onComplete(putOnProfiler(clusterId, token, "/profilerinstances/state", queryString.getOrElse(""), Json.obj())) {
            case res => mapResponse(res)
          }
        }
      }
    }

  val getProfilersStatusWithJobSummary =
    path("cluster" / LongNumber / "dp-profiler" / "status" / "jobs-summary") { clusterId =>
      extractRequest { request =>
        get {
          val queryString = request.uri.queryString()

          val token = extractToken(request)
          onComplete(getFromProfiler(clusterId, token, "/profilerjobs/jobscount", queryString.getOrElse(""))) {
            case res => mapResponse(res)
          }
        }
      }
    }

  val getProfilersStatusWithAssetsCount =
    path("cluster" / LongNumber / "dp-profiler" / "status" / "asset-count") { clusterId =>
      extractRequest { request =>
        get {
          val queryString = request.uri.queryString()

          val token = extractToken(request)
          onComplete(getFromProfiler(clusterId, token, "/profilerjobs/assetscount", queryString.getOrElse(""))) {
            case res => mapResponse(res)
          }
        }
      }
    }

  val getProfilersJobsStatus =
    path("cluster" / LongNumber / "dp-profiler" / "jobs") { clusterId =>
      extractRequest { request =>
        get {
          val queryString = request.uri.queryString()

          val token = extractToken(request)
          onComplete(getFromProfiler(clusterId, token, "/profilerjobs", queryString.getOrElse(""))) {
            case res => mapResponse(res)
          }
        }
      }
    }

  val getProfilersHistories =
    path("cluster" / LongNumber / "dp-profiler" / "histories") { clusterId =>
      extractRequest { request =>
        get {
          val queryString = request.uri.queryString()

          val token = extractToken(request)
          onComplete(getFromProfiler(clusterId, token, "/assetjobhistories/assetcounts", queryString.getOrElse(""))) {
            case res => mapResponse(res)
          }
        }
      }
    }

  val getProfilerInstanceByName =
    path("cluster" / LongNumber / "dp-profiler" / "profilerinstances" / Segment ) { (clusterId: Long, name: String) =>
      extractRequest { request =>
        get {
          val queryString = request.uri.queryString()

          val token = extractToken(request)
          onComplete(getFromProfiler(clusterId, token, s"/profilerinstances/$name", queryString.getOrElse(""))) {
            case res => mapResponse(res)
          }
        }
      }
    }

  val updateProfilerInstance =
    path("cluster" / LongNumber / "dp-profiler" / "profilerinstances") { (clusterId: Long) =>
      extractRequest { request =>
        put {
          entity(as[JsObject]) { body =>
            val token = extractToken(request)
            onComplete(putOnProfiler(clusterId, token, s"/profilerinstances", "", body)) {
              case res => mapResponse(res)
            }
          }
        }
      }
    }

  val updateProfilerSelector =
    path("cluster" / LongNumber / "dp-profiler" / "selectors" / Segment) { (clusterId: Long, name: String) =>
      extractRequest { request =>
        put {
          entity(as[JsObject]) { body =>
            val token = extractToken(request)
            onComplete(putOnProfiler(clusterId, token, s"/selectors/$name/config", "", body)) {
              case res => mapResponse(res)
            }
          }
        }
      }
    }

  val getProfilerSelectorsConfig =
    path("cluster" / LongNumber / "dp-profiler" / "selectors") { (clusterId: Long) =>
      extractRequest { request =>
        get {
          val token = extractToken(request)
          onComplete(getFromProfiler(clusterId, token, s"/selectors", "")) {
            case res => mapResponse(res)
          }
        }
      }
    }

  private def mapResponse(resposneTry: Try[WSResponse]) = {
    resposneTry match {
      case Success(res) => res.status match {
        case 200 => complete(success(res.json))
        case 404 => complete(StatusCodes.NotFound, notFound)
        case 503 => complete(StatusCodes.ServiceUnavailable, serverError)
        case _ => complete(StatusCodes.InternalServerError, serverError)
      }
      case Failure(th) => th match {
        case th: ServiceNotFound => complete(StatusCodes.MethodNotAllowed, errors(405, "cluster.profiler.service-not-found", "Unable to find Profiler configured for this cluster", th))
        case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "A generic error occured while communicating with Profiler.", th))
      }
    }

  }

  private def getFromProfiler(clusterId: Long, token: Option[String], uriPath:String, queryString: String): Future[WSResponse] = {
    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}${uriPath}?$queryString")
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json")
        .get()
    } yield {
      response
    }
  }

  private def putOnProfiler(clusterId: Long, token: Option[String], uriPath:String, queryString: String, body: JsValue): Future[WSResponse] = {
    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}${uriPath}?$queryString")
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json")
        .put(body)
    } yield {
      response
    }
  }

  private def postOnProfiler(clusterId: Long, token: Option[String], uriPath:String, queryString: String, body:JsValue): Future[WSResponse] = {
    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}${uriPath}?$queryString")
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json")
        .post(body)
    } yield {
      response
    }
  }

  private def getAuditResults(clusterId: Long, token: Option[String], dbName: String, tableName: String, userName: String, startDate: String, endDate: String): Future[WSResponse] = {
    val postData = Json.obj(
      "metrics" -> List(Map("metric" -> "hiveagg", "aggType" -> "Daily")),
      "sql" -> (if(userName == "")
                  s"SELECT date, data.`result` from hiveagg_daily where database='$dbName' and table='$tableName' and date >= cast('$startDate' as date) and date <= cast('$endDate' as date) order by date asc"
                else
                  s"SELECT date, `user`.`$userName`.`result` from hiveagg_daily where database='$dbName' and table='$tableName' and date >= cast('$startDate' as date) and date <= cast('$endDate' as date) order by date asc"
        )
    )

    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/assetmetrics")
      tmp <- Future.successful(println(urlToHit))
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json, text/javascript, */*; q=0.01")
        .post(postData)
    // Add 2 to current the minute(UTC) to make sure profiling starts within 2 minutes from now.
    } yield {
      response
    }
  }

  private def getAuditActions(clusterId: Long, token: Option[String], dbName: String, tableName: String, userName: String, startDate: String, endDate: String): Future[WSResponse] = {
    val postData = Json.obj(
      "metrics" -> List(Map("metric" -> "hiveagg", "aggType" -> "Daily")),
      "sql" -> (if(userName == "")
                  s"SELECT date, data.`action` from hiveagg_daily where database='$dbName' and table='$tableName' and date >= cast('$startDate' as date) and date <= cast('$endDate' as date) order by date asc"
              else
                  s"SELECT date, `user`.`$userName`.`action` from hiveagg_daily where database='$dbName' and table='$tableName' and date >= cast('$startDate' as date) and date <= cast('$endDate' as date) order by date asc"
        )
    )

    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/assetmetrics")
      tmp <- Future.successful(println(urlToHit))
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json, text/javascript, */*; q=0.01")
        .post(postData)
    // Add 2 to current the minute(UTC) to make sure profiling starts within 2 minutes from now.
    } yield {
      response
    }
  }

  val profilerMetrics = path("cluster" / "dp-profiler" / "metrics") {
    parameters('userName.?) { userNameOpt =>
      extractRequest { request =>
        post {
          val token = extractToken(request)
          entity(as[JsObject]) { request =>
            request.validate[ProfilerMetricRequest] match {
              case JsSuccess(metricRequest, _) =>
                userNameOpt.map(userName => {
                  onComplete(retrieveProfilerConfig(metricRequest.clusterId).flatMap(MetricRetriever.retrieveMetrics(ws, token, _, metricRequest, userName))) {
                    case Success(results) =>
                      complete(success(Json.toJson(results).as[JsObject]))
                    case Failure(error) =>
                      complete(StatusCodes.InternalServerError, errors(500, "cluster.profiler.generic", "An error occured while communicating with Profiler.", error))
                  }
                }).getOrElse(complete(StatusCodes.BadRequest, errors(405, "cluster.profiler.service-not-found", "Mandatory param userName is missing", new Exception("userName is missing"))))
              case error: JsError =>
                complete(StatusCodes.BadRequest, errors(405, "cluster.profiler.service-not-found", "Invalid payload", new Exception(JsError.toFlatForm(error).toString())))
            }
          }
        }
      }
    }
  }

  private def retrieveProfilerConfig(clusterId: Long): Future[GlobalProfilerConfigs] =
    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/assetmetrics")
    } yield GlobalProfilerConfigs(urlToHit)

  private def deleteProfilerByJobName(clusterId: Long, token: Option[String], jobName: String): Future[WSResponse] = {

      for {
        config <- getConfigOrThrowException(clusterId)
        url <- getUrlFromConfig(config)
        baseUrls <- extractUrlsWithIp(url, clusterId)
        urlToHit <- Future.successful(s"${baseUrls.head}/schedules/$jobName")
        response <- ws.url(urlToHit, clusterId, PROFILER)
          .withToken(token)
          .withHeaders("Accept" -> "application/json, text/javascript, */*; q=0.01")
          .delete()
      } yield {
        response
      }
    }

    private def getScheduleInfo(clusterId: Long, token: Option[String], taskName: String): Future[WSResponse] = {

      for {
        config <- getConfigOrThrowException(clusterId)
        url <- getUrlFromConfig(config)
        baseUrls <- extractUrlsWithIp(url, clusterId)
        urlToHit <- Future.successful(s"${baseUrls.head}/schedules/$taskName")
        response <- ws.url(urlToHit, clusterId, PROFILER)
          .withToken(token)
          .withHeaders("Accept" -> "application/json, text/javascript, */*; q=0.01")
          .get()
      } yield {
        response
      }
    }

    private def getJobStatus(clusterId: Long, token: Option[String], dbName: String, tableName: String): Future[WSResponse] = {

      for {
        config <- getConfigOrThrowException(clusterId)
        url <- getUrlFromConfig(config)
        baseUrls <- extractUrlsWithIp(url, clusterId)
        urlToHit <- Future.successful(s"${baseUrls.head}/jobs/assetjob?assetId=$dbName.$tableName&profilerName=hivecolumn")
        response <- ws.url(urlToHit, clusterId, PROFILER)
          .withToken(token)
          .withHeaders("Accept" -> "application/json, text/javascript, */*; q=0.01")
          .get()
      } yield {
        response
      }
    }

  private def getProfilersLastRunInfoOnAsset(clusterId: Long, token: Option[String], assetId: String ): Future[WSResponse] = {

    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/assetjobhistories/$assetId/profilerslastrun")
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json")
        .get()
    } yield {
      response
    }
  }

  private def getDatasetProfiledAssetsCount(clusterId: Long, token: Option[String], profilerInstanceName: String, datasetName: String, startTime: Long, endTime: Long ): Future[WSResponse] = {

    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/datasetasset/assetcount/$datasetName?profilerinstancename=$profilerInstanceName&startTime=$startTime&endTime=$endTime")
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json")
        .get()
    } yield {
      response
    }
  }

  private def postAndScheduleJob(clusterId: Long, token: Option[String], jobName: String, list: Seq[String]): Future[WSResponse] = {
    val postData = Json.obj(
      "profilerName" -> "hivecolumn", //"hivecolumnlive4",
      "conf" -> Json.obj(),
      "assets" -> list.map{ itm =>{
        var itmAr = itm.split('.')
        Json.obj(
          "id" -> itm,
          "assetType"  ->  "Hive",
          "data" -> Json.obj(
            "db" -> itmAr.head,
            "table" -> itmAr.last
          )
        )
      }}
    )
    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/schedules")
      tmp <- Future.successful(println(urlToHit))
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json, text/javascript, */*; q=0.01")
        .post(Json.obj("name" -> jobName, "cronExpr" -> s"0 ${(2+(Instant.now.getEpochSecond/60)%60)%60} * * * ?", "jobTask"->postData))
      // Add 2 to current the minute(UTC) to make sure profiling starts within 2 minutes from now.
    } yield {
      response
    }
  }

  private def doDatasetAssetMapping(clusterId: Long, token: Option[String], assetIds: Seq[String], datasetName: String): Future[WSResponse] = {
    val postData = Json.obj(
      "datasetName" -> datasetName,
      "assetIds" -> assetIds
    )
    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/datasetasset")
      echo <- Future.successful(println(s"url to hit for dataset-asset mapping $urlToHit"))
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json")
        .post(postData)
    } yield {
      response
    }
  }

  private def postJob(clusterId: Long, token: Option[String], dbName: String, tableName: String): Future[WSResponse] = {
    val postData = Json.obj(
      "profilerName" -> "hivecolumn", //"hivecolumnlive4",
      "conf" -> Json.obj(),
      "assets" -> Seq(
        Json.obj(
          "id" -> s"$dbName.$tableName",
          "assetType"  ->  "Hive",
          "data" -> Json.obj(
            "db" -> dbName,
            "table" -> tableName
          )
        )
      )
    )
    for {
      config <- getConfigOrThrowException(clusterId)
      url <- getUrlFromConfig(config)
      baseUrls <- extractUrlsWithIp(url, clusterId)
      urlToHit <- Future.successful(s"${baseUrls.head}/jobs")
      tmp <- Future.successful(println(urlToHit))
      response <- ws.url(urlToHit, clusterId, PROFILER)
        .withToken(token)
        .withHeaders("Accept" -> "application/json, text/javascript, */*; q=0.01")
        .post(postData)
    } yield {
      response
    }
  }

  private def getConfigOrThrowException(clusterId: Long) = {
    clusterComponentService.getEndpointsForCluster(clusterId, "DPPROFILER").map {
      case Right(endpoints) => endpoints
      case Left(errors) =>
        throw ServiceNotFound(
          s"Could not get the service Url from storage - $errors")
    }.recover{
      case e: Throwable =>
        throw ServiceNotFound(
          s"Could not get the service Url from storage - ${e.getMessage}")
    }
  }

  def getUrlFromConfig(service: ClusterServiceWithConfigs): Future[URL] = Future.successful {
    val host = service.servicehost
    val configsAsList = service.configProperties.get.properties
    val profilerConfig = configsAsList.find(obj =>
      obj.`type` == "dpprofiler-env")
    if (profilerConfig.isEmpty)
      throw ServiceNotFound("No properties found for DpProfiler")
    val properties = profilerConfig.get.properties
    val port = properties("dpprofiler.http.port")
    new URL(s"http://$host:$port")
  }

  def extractUrlsWithIp(urlObj: URL, clusterId: Long): Future[Seq[String]] = {
    clusterDataApi.getDataplaneCluster(clusterId)
      .flatMap { dpCluster =>
        clusterHostsService.getHostByClusterAndName(clusterId, urlObj.getHost)
            .map {
              case Right(host) => Seq(s"${urlObj.getProtocol}://${host.ipaddr}:${urlObj.getPort}")
              case Left(errors) => throw new Exception(s"Cannot translate the hostname into an IP address $errors")
            }
      }
  }

  private def extractToken(httpRequest: HttpRequest): Option[String] = {
    val tokenHeader = httpRequest.getHeader(DPTOKEN)
    if (tokenHeader.isPresent) Some(tokenHeader.get().value()) else None
  }

}
