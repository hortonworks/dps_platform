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

import com.hortonworks.dataplane.commons.domain.Atlas.{AssetProperties, AtlasAttribute, AtlasEntities, AtlasSearchQuery, BodyToModifyAtlasClassification}
import com.hortonworks.dataplane.commons.domain.Entities.{Errors, HJwtToken}
import com.hortonworks.dataplane.cs.Webservice.AtlasService
import com.typesafe.config.Config
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AtlasServiceImpl(val config: Config)(implicit ws: ClusterWsClient)
    extends AtlasService {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  private def mapToAttributes(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[Seq[AtlasAttribute]](res, r =>
            (r.json \ "results" \ "data").validate[Seq[AtlasAttribute]].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToResults(res: WSResponse) = {
    res.status match {
      case 200 => extractEntity[AtlasEntities](res, r => (r.json \ "results" \ "data").validate[AtlasEntities].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToAtlasEntities(res: WSResponse) = {
    res.status match {
      case 200 => extractEntity[AtlasEntities](res, r => {
        val entities = (r.json \ "results" \ "data" \ "entities").validate[Seq[AssetProperties]].get.map(_.getEntity())
        AtlasEntities(Some(entities.toList))
      })
      case _ => mapErrors(res)
    }
  }

  private def mapToProperties(res: WSResponse) = {
    res.status match {
      case 200 => extractEntity[Seq[AssetProperties]](res, r =>
        (r.json \ "results" \ "data" \ "entities").validate[Seq[AssetProperties]].get
      )
      case _ => mapErrors(res)
    }
  }

  private def mapResultsGeneric(res: WSResponse) : Either[Errors,JsObject]= {
    res.status match {
      case 200 =>  Right((res.json \ "results" \ "data").as[JsObject])
      case _ => mapErrors(res)
    }
  }

  private def mapToResultsGeneric(res: WSResponse): JsObject = {
    res.status match {
      case 200 => (res.json \ "results").as[JsObject]
      case _ => {
        val logMsg = s"Cs-Client AtlasServiceImpl: In mapToResultsGeneric method, result status ${res.status} and result body ${res.body}"
        mapResponseToError(res,Option(logMsg))
      }
    }
  }


  override def listQueryAttributes(clusterId: String)(implicit token:Option[HJwtToken]): Future[Either[Errors, Seq[AtlasAttribute]]] = {
    ws.url(s"$url/cluster/$clusterId/atlas/hive/attributes")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToAttributes)
  }

  override def searchQueryAssets(clusterId: String, filters: AtlasSearchQuery)(implicit token:Option[HJwtToken]): Future[Either[Errors, AtlasEntities]] = {
    ws.url(s"$url/cluster/$clusterId/atlas/hive/search")
      .withToken(token)
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .post(Json.toJson(filters))
      .map(mapToResults)
  }

  override def getAssetDetails(clusterId: String, atlasGuid: String)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/atlas/guid/$atlasGuid")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  def getAssetsDetails(clusterId: String, guids: Seq[String])(implicit token:Option[HJwtToken]): Future[Either[Errors, AtlasEntities]] = {
    ws.url(s"$url/cluster/$clusterId/atlas/guid")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .withQueryString(guids.map(guid => ("query", guid)): _*)
      .get()
      .map(mapToAtlasEntities)
  }

  override def getTypeDefs(clusterId: String, defType:String) (implicit token:Option[HJwtToken]): Future[Either[Errors,JsObject]] = {
    ws.url(s"$url/cluster/$clusterId/atlas/typedefs/type/$defType")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def getLineage(clusterId: String, atlasGuid: String, depth: Option[String]) (implicit token:Option[HJwtToken]): Future[Either[Errors,JsObject]] = {
    var lineageUrl = s"$url/cluster/$clusterId/atlas/$atlasGuid/lineage"
    if(depth.isDefined){
      lineageUrl = lineageUrl + s"?depth=${depth.get}"
    }
    ws.url(lineageUrl)
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def postClassifications(clusterId: String, atlasGuid: String, body: BodyToModifyAtlasClassification)(implicit token:Option[HJwtToken]): Future[JsObject] = {
    ws.url(s"$url/cluster/$clusterId/atlas/entity/guid/$atlasGuid/classifications")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(body))
      .map(mapToResultsGeneric)
  }


}
