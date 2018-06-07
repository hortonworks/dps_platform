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

import com.hortonworks.dataplane.commons.domain.profiler.models.Requests
import com.hortonworks.dataplane.commons.domain.Entities.{BodyToModifyAssetColumnTags, Error, Errors, HJwtToken}
import com.hortonworks.dataplane.cs.Webservice.DpProfilerService
import com.typesafe.config.Config
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.hortonworks.dataplane.commons.domain.profiler.parsers.RequestParser._

class DpProfilerServiceImpl (val config: Config)(implicit ws: ClusterWsClient) extends DpProfilerService{


  private def mapResultsGeneric(res: WSResponse) : Either[Errors,JsObject]= {
    res.status match {
      case 200 =>  Right((res.json \ "results" \ "data").as[JsObject])
      case 404 => Left(
        Errors(Seq(
          Error(404, "Not found"))))
      case 405 => Left(
        Errors(Seq(
          Error(405, (res.json \ "errors" \\ "code").head.toString()))))
      case 503 => Left(
        Errors(Seq(
          Error(503, (res.json \ "error" \ "message").toString()))))
      case _ => Left(
        Errors(Seq(
          Error(res.status, (res.json \ "error" \ "message").toString()))))

        //mapErrors(res)
    }
  }

  private def mapToResultsGeneric(res: WSResponse): JsObject = {
    res.status match {
      case 200 =>
        (res.json \ "results").as[JsObject]
      case _ => {
        val logMsg = s"Cs-Client DpProfilerServiceImpl: In mapToResultsGeneric method, result status ${res.status} and result body ${res.body}"
        mapResponseToError(res,Option(logMsg))
      }
    }
  }

  override def startProfilerJob(clusterId: String, dbName: String, tableName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/start-job/$dbName/$tableName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def getProfilerJobStatus(clusterId: String, dbName: String, tableName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/job-status/$dbName/$tableName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def deleteProfilerByJobName(clusterId: Long, jobName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/profilers?jobName=$jobName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .delete()
      .map(mapResultsGeneric)
  }

  override def startAndScheduleProfilerJob(clusterId: String, jobName: String, assets: Seq[String])(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/start-schedule-job")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .post(Json.obj("list" -> assets, "jobName" -> jobName))
      .map(mapResultsGeneric)

  }

  override def getScheduleInfo(clusterId: String, taskName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/schedule-info/$taskName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def getAuditResults(clusterId: String, dbName: String, tableName: String, userName: String, startDate: String, endDate: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/audit-results/$dbName/$tableName/$startDate/$endDate?userName=$userName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def getAuditActions(clusterId: String, dbName: String, tableName: String, userName: String, startDate: String, endDate: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/audit-actions/$dbName/$tableName/$startDate/$endDate?userName=$userName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def getMetrics(metricRequest: Requests.ProfilerMetricRequest, userName: String)(implicit token: Option[HJwtToken]): Future[Either[Errors, JsObject]] = {
    ws.url(s"$url/cluster/dp-profiler/metrics?userName=$userName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(metricRequest))
      .map(mapResultsGeneric)
  }
  override def datasetAssetMapping(clusterId: String, assetIds: Seq[String], datasetName: String)(implicit token: Option[HJwtToken]) = {
    ws.url(s"$url/cluster/$clusterId/dpprofiler/datasetasset/$datasetName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .post(Json.obj("assetIds" -> assetIds))
      .map(mapToResultsGeneric)
  }

  override def getExistingProfiledAssetCount(clusterId: String, profilerName: String)(implicit token: Option[HJwtToken]) = {
    ws.url(s"$url/cluster/$clusterId/dpprofiler/assets?profilerName=$profilerName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }

  override def getProfilersLastRunInfoOnAsset(clusterId: String, assetId: String)(implicit token: Option[HJwtToken]): Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dpprofiler/assets/$assetId/profilerslastrun")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }


  override def getDatasetProfiledAssetCount(clusterId: String, datasetName: String, profilerInstanceName: String, startTime: Long, endTime: Long)(implicit token:Option[HJwtToken]): Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dpprofiler/datasetasset/$datasetName/assetcount?profilerInstanceName=$profilerInstanceName&startTime=$startTime&endTime=$endTime")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }

  override def getProfilersStatusWithJobSummary (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/status/jobs-summary?$queryString")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }

  override def getProfilersStatusWithAssetsCount (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/status/asset-count?$queryString")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }

  override def getProfilersJobs (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/jobs?$queryString")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }

  override def putProfilerState (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/profilerinstances/state?$queryString")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .put(Json.obj())
      .map(mapToResultsGeneric)
  }

  override def getProfilersHistories (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/histories?$queryString")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }

  override def postAssetColumnClassifications (clusterId: String, body: JsValue) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/assets/classifications")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .post(body)
      .map(mapToResultsGeneric)
  }


  override def getProfilerInstanceByName (clusterId: String, name: String) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/profilerinstances/$name")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResultsGeneric)
  }

  override def updateProfilerInstance(clusterId: String, body: JsValue) (implicit token:Option[HJwtToken]) : Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/dp-profiler/profilerinstances")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .put(body)
      .map(mapToResultsGeneric)
  }
}
