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

import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.{CertificateService, SkuService}
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CertificateServiceImpl(config: Config)(implicit ws: WSClient)
    extends CertificateService {
  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  override def list(active: Option[Boolean] = None, name: Option[String]): Future[Seq[Certificate]] = {
    var params: Map[String, String] = Map.empty
    active.foreach(active => params = params + ("active" -> active.toString))
    name.foreach(name => params = params + ("name" -> name))

    ws.url(s"$url/certificates")
      .withHeaders("Accept" -> "application/json")
      .withQueryString(params.toList: _*)
      .get()
      .map { res =>
        res.status match {
          case 200 => (res.json \ "results").validate[Seq[Certificate]].get
          case _ => throw WrappedErrorException(Error(res.status, "", "core.api-error"))
        }
      }
  }

  override def create(certificate: Certificate): Future[Certificate] = {
    ws.url(s"$url/certificates")
      .withHeaders(
        "Accept" -> "application/json",
        "Content-Type" -> "application/json"
      )
      .post(Json.toJson(certificate))
      .map { res =>
        res.status match {
          case 200 => (res.json \ "results").validate[Certificate].get
          case _ => throw WrappedErrorException(Error(res.status, "", "core.api-error"))
        }
      }
  }

  def update(certificateId: String, certificate: Certificate): Future[Certificate] = {
    ws.url(s"$url/certificates/$certificateId")
      .withHeaders(
        "Accept" -> "application/json",
        "Content-Type" -> "application/json"
      )
      .put(Json.toJson(certificate))
      .map { res =>
        res.status match {
          case 200 => (res.json \ "results").validate[Certificate].get
          case _ => throw WrappedErrorException(Error(res.status, "", "core.api-error"))
        }
      }
  }

  override def delete(certificateId: String): Future[Long] = {
    ws.url(s"$url/certificates/$certificateId")
      .withHeaders("Accept" -> "application/json")
      .delete()
      .map { res =>
        res.status match {
          case 200 => (res.json \ "results").validate[Long].get
          case _ => throw WrappedErrorException(Error(res.status, "", "core.api-error"))
        }
      }
  }
}
