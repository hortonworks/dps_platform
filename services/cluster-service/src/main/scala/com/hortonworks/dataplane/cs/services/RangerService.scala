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

package com.hortonworks.dataplane.cs.services

import javax.inject.Inject

import com.hortonworks.dataplane.commons.domain.Constants.RANGER
import com.hortonworks.dataplane.commons.domain.Entities.{Error, WrappedErrorException}
import com.hortonworks.dataplane.cs.{Credentials, KnoxProxyWsClient}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class RangerService @Inject()(val config: Config)(implicit ws: KnoxProxyWsClient) {
  val logger = Logger(classOf[RangerService])

  private def httpHandler(res: WSResponse): JsValue = {
    res.status match {
      case 200 => res.json
      case _ => throw WrappedErrorException(Error(500, "Unexpected error", "cluster.http.ranger.generic", target = Some(res.body)))
    }
  }

  private val defaultHeaders = "Accept" -> "application/json, text/javascript, */*; q=0.01"
  private val tokenTopologyName = Try(config.getString("dp.services.knox.token.topology")).getOrElse("token")

  def requestRangerForPolicies(clusterId: String, url: String, credential: Credentials, params: Map[String, String])(implicit token: Option[String]): Future[JsValue] = {
    val serviceType = params.filter(_._1 == "serviceType").head._2
    serviceType match {
      case "hive" => requestRangerForResourcePolicies(clusterId, url, credential, serviceType, params)
      case "tag" => requestRangerForTagPolicies(clusterId, url, credential, serviceType, params)
      case _ => throw WrappedErrorException(Error(500, "This is not a supported Ranger service.", "cluster.ranger.unsupported-service"))
    }
  }

  def requestRangerForAudit(clusterId: String, url: String, credential: Credentials, dbName: String, tableName: String, params: Map[String, String])(implicit token: Option[String]): Future[JsValue] = {
    for {
      repoType <- ws.url(s"$url/service/plugins/definitions?pageSource=Audit", clusterId.toLong, RANGER)
        .withToken(token)
        .withHeaders(defaultHeaders)
        .withAuth(credential.user.get, credential.pass.get, WSAuthScheme.BASIC)
        .get()
        .map(httpHandler)
        .map { json =>
          ((json \ "serviceDefs")
              .as[Seq[JsObject]]
              .filter(serviceDef => (serviceDef \ "name").as[String] == "hive")
              .head \ "id")
            .as[Long]
        }
      json <- ws.url(s"$url/service/assets/accessAudit", clusterId.toLong, RANGER)
        .withToken(token)
        .withHeaders(defaultHeaders)
        .withAuth(credential.user.get, credential.pass.get, WSAuthScheme.BASIC)
        .withQueryString((params + (
          "repoType" -> repoType.toString,
          "resourcePath" -> s"$dbName/$tableName",
          "sortBy" -> "eventTime"
        )).toList: _*)
        .get()
        .map(httpHandler)
    } yield json
  }

  private def requestRangerForTagPolicies(clusterId: String, url: String, credential: Credentials, serviceType: String, params: Map[String, String])(implicit token: Option[String]): Future[JsObject] = {
    val tags = params.filter(_._1 == "tags").headOption.getOrElse("" -> "")._2
    val startIndex = params.filter(_._1 == "startIndex").headOption.getOrElse("" -> "0")._2.toLong
    val pageSize = params.filter(_._1 == "pageSize").headOption.getOrElse("" -> "100")._2.toLong

    // assuming that no tag can have more than one policy
    val queries = getBuiltQueries(tags, startIndex, pageSize)
    for {
      serviceIdSeq <- getRangerServicesForType(clusterId, url, credential, serviceType)
      policies <- Future
        .sequence(serviceIdSeq.map(cServiceId => getRangerPoliciesByServiceIdAndQueries(clusterId, url, credential, cServiceId, queries)))
        .map(_.flatten)
    } yield {
      val _policies = policies.slice(startIndex.toInt, (startIndex + pageSize).toInt)
      Json.obj(
        "startIndex" -> startIndex,
        "pageSize" -> pageSize,
        "totalCount" -> policies.size,
        "resultSize" -> _policies.size,
        "policies" -> Json.toJson(_policies)
      )
    }
  }

  private def getBuiltQueries(tags: String, startIndex: Long, pageSize: Long): Seq[String] = {
    tags
      .trim
      .split(",")
      .map(_.trim)
      .filter(cTag => !cTag.isEmpty)
      .sorted
      .map(cTag => s"resource:tag=$cTag&startIndex=$startIndex&pageSize=$pageSize")
  }

  private def getRangerServicesForType(clusterId: String, uri: String, credential: Credentials, serviceType: String)(implicit token: Option[String]): Future[Seq[Long]] =
    ws.url(s"$uri/service/public/v2/api/service?serviceType=$serviceType", clusterId.toLong, RANGER)
      .withToken(token)
      .withHeaders(defaultHeaders)
      .withAuth(credential.user.get, credential.pass.get, WSAuthScheme.BASIC)
      .get()
      .map(httpHandler)
      .map { json => (json \\ "id").map { _.as[Long] } }

  private def getRangerPoliciesByServiceIdAndQuery(clusterId: String, uri: String, credential: Credentials, serviceId: Long, query: String)(implicit token: Option[String]): Future[Seq[JsObject]] =
    ws.url(s"$uri/service/plugins/policies/service/$serviceId?$query", clusterId.toLong, RANGER)
      .withToken(token)
      .withHeaders(defaultHeaders)
      .withAuth(credential.user.get, credential.pass.get, WSAuthScheme.BASIC)
      .get()
      .map(httpHandler)
      .map { json => (json \ "policies").as[Seq[JsObject]] }

  private def getRangerPoliciesByServiceIdAndQueries(clusterId: String, uri: String, credential: Credentials, serviceId: Long, queries: Seq[String])(implicit token: Option[String]): Future[Seq[JsObject]] = {
    val futures = queries.map(cQuery => getRangerPoliciesByServiceIdAndQuery(clusterId, uri, credential, serviceId, cQuery))
    Future.sequence(futures).map(_.flatten)
  }

  private def requestRangerForResourcePolicies(clusterId: String, url: String, credential: Credentials, serviceType: String, params: Map[String, String])(implicit token: Option[String]): Future[JsValue] = {
    val dbName = params.filter(_._1 == "dbName").headOption.getOrElse("" -> "")._2
    val tableName = params.filter(_._1 == "tableName").headOption.getOrElse("" -> "")._2
    val startIndex = params.filter(_._1 == "startIndex").headOption.getOrElse("" -> "0")._2.toLong
    val pageSize = params.filter(_._1 == "pageSize").headOption.getOrElse("" -> "100")._2.toLong
    val query = s"resource:database=$dbName&resource:table=$tableName&startIndex=$startIndex&pageSize=$pageSize"

    for {
      serviceIdSeq <- getRangerServicesForType(clusterId, url, credential, serviceType)
      response <- ws.url(s"$url/service/plugins/policies/service/${serviceIdSeq.head}?$query", clusterId.toLong, RANGER)
                     .withToken(token)
                     .withHeaders(defaultHeaders)
                     .withAuth(credential.user.get, credential.pass.get, WSAuthScheme.BASIC)
                     .get()
                     .map(httpHandler)
    } yield response
  }
}