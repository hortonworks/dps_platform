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

import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors, HJwtToken}
import com.hortonworks.dataplane.cs.Webservice.RangerService
import com.typesafe.config.Config
import play.api.libs.json.JsValue
import play.api.libs.ws.WSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class RangerServiceImpl(val config: Config)(implicit ws: ClusterWsClient)
  extends RangerService {

  private def mapResultsGeneric(res: WSResponse) : Either[Errors,JsValue]= {
    res.status match {
      case 200 =>  Right((res.json \ "results" \ "data").as[JsValue])
      case 404 => Left(
        Errors(Seq(
          Error(404, (res.json \ "errors" \\ "code").head.toString()))))
      case _ => mapErrors(res)
    }
  }

  override def getAuditDetails(clusterId: String, dbName: String, tableName: String, offset: String, limit: String, accessType:String, accessResult:String)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsValue]] = {
    ws.url(s"$url/cluster/$clusterId/ranger/audit/$dbName/$tableName?pageSize=$limit&startIndex=$offset&accessType=$accessType&accessResult=$accessResult")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def getPolicyDetails(clusterId: String, dbName: String, tableName: String, offset: String, limit: String)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsValue]] = {
    ws.url(s"$url/cluster/$clusterId/ranger/policies?pageSize=$limit&startIndex=$offset&serviceType=hive&dbName=$dbName&tableName=$tableName")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def getPolicyDetailsByTagName(clusterId: Long, tags: String, offset: Long, limit: Long)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsValue]] = {
    ws.url(s"$url/cluster/$clusterId/ranger/policies?pageSize=$limit&startIndex=$offset&serviceType=tag&tags=$tags")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

}
