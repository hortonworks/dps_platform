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

import com.google.common.base.Strings
import com.hortonworks.dataplane.commons.domain.Ambari.{AmbariCheckResponse, AmbariCluster, AmbariDetailRequest, ServiceInfo}
import com.hortonworks.dataplane.commons.domain.Atlas.{AssetProperties, AtlasAttribute, AtlasEntities, AtlasSearchQuery, BodyToModifyAtlasClassification}
import com.hortonworks.dataplane.commons.domain.Entities.{ClusterService => ClusterData, _}
import com.hortonworks.dataplane.commons.domain.profiler.models.Requests.ProfilerMetricRequest
import com.typesafe.config.Config
import play.api.Logger
import play.api.libs.json.{JsObject, JsResult, JsSuccess, JsValue}
import play.api.libs.ws.{WSRequest, WSResponse}

import scala.concurrent.Future
import scala.util.{Success, Try}

object Webservice {

  trait CsClientService {
    val config:Config

    import com.hortonworks.dataplane.commons.domain.JsonFormatters._

    protected def url =
      Option(System.getProperty("dp.services.cluster.service.uri"))
        .getOrElse(config.getString("dp.services.cluster.service.uri"))

    protected def extractEntity[T](res: WSResponse,
                                   f: WSResponse => T): Either[Errors, T] = {
      Right(f(res))
    }

    protected def extractError(res: WSResponse,
                               f: WSResponse => JsResult[Errors]): Errors = {
      if (res.body.isEmpty)
        Errors()
      f(res).map(r => r).getOrElse(Errors())
    }

    protected def mapErrors(res: WSResponse) = {
      Left(extractError(res, r => r.json.validate[Errors]))
    }

    protected def mapResponseToError(res: WSResponse, loggerMsg: Option[String]= None) = {
      val errorsObj = Try(res.json.validate[Errors])

      errorsObj match {
        case Success(e :JsSuccess[Errors]) =>
          printLogs(res,loggerMsg)
          throw new WrappedErrorException(e.get.errors.head)
        case _ =>
          val msg = if(Strings.isNullOrEmpty(res.body)) res.statusText else  res.body
          val logMsg = loggerMsg.map { lmsg =>
            s"""$lmsg | $msg""".stripMargin
          }.getOrElse(s"In cs-client: Failed with $msg")
          printLogs(res,Option(logMsg))
          throw new WrappedErrorException(Error(res.status, msg, code = "cluster-service.generic"))
      }
    }

    private def printLogs(res: WSResponse,msg: Option[String]) ={
      val logMsg = msg.getOrElse(s"Could not get expected response status from service. Response status ${res.statusText}")
      Logger.warn(logMsg)
    }

  }

  @deprecated(since = "1.2.1.0", message = "Would be removed in 2.0. Please re-implement in App via HDP route.")
  trait AtlasService extends CsClientService {

    def listQueryAttributes(clusterId: String)(implicit token:Option[HJwtToken]): Future[Either[Errors, Seq[AtlasAttribute]]]

    def searchQueryAssets(clusterId: String, filters: AtlasSearchQuery)(implicit token:Option[HJwtToken]): Future[Either[Errors, AtlasEntities]]

    def getAssetDetails(clusterId: String, atlasGuid: String)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsObject]]

    def getAssetsDetails(clusterId: String, guids: Seq[String])(implicit token:Option[HJwtToken]): Future[Either[Errors, AtlasEntities]]

    def getTypeDefs(clusterId: String, defType: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def getLineage(clusterId: String, atlasGuid: String, depth: Option[String])(implicit token:Option[HJwtToken]): Future[Either[Errors,JsObject]]

    def postClassifications(clusterId: String, atlasGuid: String, body: BodyToModifyAtlasClassification)(implicit token:Option[HJwtToken]): Future[JsObject]
  }

  @deprecated(since = "1.2.1.0", message = "Would be removed in 2.0. Please re-implement in App via HDP route.")
  trait AmbariWebService extends CsClientService {

    @deprecated(message = "Single Node mode has been removed. Cluster Service now sniffs Knox URL from Ambari URL.")
    def isSingleNode:Future[Boolean]

    @deprecated("Please use HDP route instead")
    def requestAmbariApi(clusterId: Long, ambariUrl: String, addClusterIdToResponse: Boolean = false)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsValue]]

    @deprecated("Please use HDP route instead")
    def requestAmbariClusterApi(clusterId: Long, ambariUrl: String, addClusterIdToResponse: Boolean = false)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsValue]]

    def syncAmbari(dpCluster: DataplaneClusterIdentifier)(implicit token:Option[HJwtToken]):Future[Boolean]

    def checkAmbariStatus(ambariUrl: String, allowUntrusted: Boolean, behindGateway: Boolean)(implicit token:Option[HJwtToken]):Future[Either[Errors,AmbariCheckResponse]]

    def getAmbariDetails(ambariDetailRequest: AmbariDetailRequest)(implicit token:Option[HJwtToken]):Future[Either[Errors,Seq[AmbariCluster]]]

    def getAmbariServicesInfo(dpcwServices: DpClusterWithDpServices)(implicit token:Option[HJwtToken]):Future[Either[Errors,Seq[ServiceInfo]]]
  }

  @deprecated(since = "1.2.1.0", message = "Would be removed in 2.0. Please re-implement in App via HDP route.")
  trait ClusterClient extends CsClientService {
    def getKnoxToken(clusterId: String)(implicit token:Option[HJwtToken]): Future[KnoxData]

    case class KnoxData(knoxUrl: String, token: String)
  }

  @deprecated(since = "1.2.1.0", message = "Would be removed in 2.0. Please re-implement in App via HDP route.")
  trait KnoxProxyService extends CsClientService {

    def getProxyUrl:String

    def execute(request:WSRequest,call: WSRequest => Future[WSResponse],fallback:Option[String])(implicit token:Option[HJwtToken]) : Future[WSResponse]

  }

  @deprecated(since = "1.2.1.0", message = "Would be removed in 2.0. Please re-implement in App via HDP route.")
  trait RangerService extends CsClientService {

    def getAuditDetails(clusterId: String, dbName: String, tableName: String, offset: String, limit: String, accessType:String, accessResult:String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsValue]]

    def getPolicyDetails(clusterId: String, dbName: String, tableName: String, offset: String, limit: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsValue]]

    def getPolicyDetailsByTagName(clusterId: Long, tags: String, offset: Long, limit: Long)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsValue]]

  }

  @deprecated(since = "1.2.1.0", message = "Would be removed in 2.0. Please re-implement in App via HDP route.")
  trait DpProfilerService extends CsClientService {

    def startProfilerJob(clusterId: String, dbName: String, tableName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def getProfilerJobStatus(clusterId: String, dbName: String, tableName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def deleteProfilerByJobName(clusterId: Long, jobName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def startAndScheduleProfilerJob(clusterId: String, jobName: String, assets: Seq[String])(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def getScheduleInfo(clusterId: String, taskName: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def getAuditResults(clusterId: String, dbName: String, tableName: String, userName: String, startDate: String, endDate: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def getAuditActions(clusterId: String, dbName: String, tableName: String, userName: String, startDate: String, endDate: String)(implicit token:Option[HJwtToken]) : Future[Either[Errors,JsObject]]

    def getMetrics(metricRequest: ProfilerMetricRequest, userName: String)(implicit token: Option[HJwtToken]): Future[Either[Errors, JsObject]]

    def datasetAssetMapping(clusterId: String, assetIds: Seq[String], datasetName: String)(implicit token:Option[HJwtToken]) : Future[JsObject]

    def getExistingProfiledAssetCount(clusterId: String, profilerName: String)(implicit token: Option[HJwtToken]) : Future[JsObject]

    def getDatasetProfiledAssetCount(clusterId: String, datasetName: String, profilerInstanceName: String,startTime: Long, endTime: Long)(implicit token:Option[HJwtToken]) : Future[JsObject]

    def getProfilersStatusWithJobSummary (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def getProfilersStatusWithAssetsCount (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def getProfilersJobs (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def putProfilerState (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def getProfilersHistories (clusterId: String, queryString: String) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def getProfilersLastRunInfoOnAsset(clusterId: String, assetId: String)(implicit token: Option[HJwtToken]): Future[JsObject]

    def postAssetColumnClassifications (clusterId: String, body: JsValue) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def getProfilerInstanceByName (clusterId: String, name: String) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def updateProfilerInstance (clusterId: String, body: JsValue) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def getProfilerSelectorsConfig (clusterId: String) (implicit token:Option[HJwtToken]) : Future[JsObject]

    def updateProfilerSelector(clusterId: String, name: String, body: JsValue) (implicit token:Option[HJwtToken]) : Future[JsObject]

  }


  @deprecated(since = "1.2.1.0", message = "Would be removed in 2.0. Please re-implement in App via HDP route.")
  trait ConfigurationUtilityService extends CsClientService {
    def doReloadCertificates() : Future[JsValue]
  }
}
