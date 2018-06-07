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

package com.hortonworks.dataplane.db

import com.hortonworks.dataplane.commons.domain.Entities.{
  EnabledSku,
  Errors,
  Sku
}
import com.hortonworks.dataplane.db.Webservice.SkuService
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import scala.concurrent.Future
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import scala.concurrent.ExecutionContext.Implicits.global

class SkuServiceImpl(config: Config)(implicit ws: WSClient)
    extends SkuService {
  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  override def getAllSkus(): Future[Either[Errors, Seq[Sku]]] = {
    ws.url(s"$url/skus")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        mapToSkus(res)
      }
  }
  override def getSku(name: String): Future[Either[Errors, Sku]] = {
    ws.url(s"$url/skus/byName/$name")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        mapToSku(res)
      }
  }

  def getEnabledSkus(): Future[Either[Errors, Seq[EnabledSku]]] = {
    ws.url(s"$url/enabledskus")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        mapToEnabledSkus(res)
      }
  }

  def enableSku(enabledSku: EnabledSku): Future[Either[Errors, EnabledSku]] = {
    ws.url(s"$url/enabledskus")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(enabledSku))
      .map { res =>
        mapToEnabledSku(res)
      }
  }

  private def mapToSkus(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[Seq[Sku]].get)
      case _ => mapErrors(res)
    }
  }
  private def mapToEnabledSkus(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[Seq[EnabledSku]].get)
      case _ => mapErrors(res)
    }
  }
  private def mapToSku(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[Sku].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToEnabledSku(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[EnabledSku].get)
      case _ => mapErrors(res)
    }
  }

}
