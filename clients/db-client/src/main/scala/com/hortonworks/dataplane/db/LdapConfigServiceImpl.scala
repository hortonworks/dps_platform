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
import javax.inject.Singleton

import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors, LdapConfiguration, Role}
import com.hortonworks.dataplane.db.Webservice.LdapConfigService
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
@Singleton
class LdapConfigServiceImpl(config: Config)(implicit ws: WSClient)
    extends LdapConfigService {
  private def serviceUri = Option(System.getProperty("dp.services.db.service.uri"))
    .getOrElse(config.getString("dp.services.db.service.uri"))
  override def create(ldapConfig: LdapConfiguration)
    : Future[Either[Errors, LdapConfiguration]] = {
    ws.url(s"$serviceUri/ldapconfig")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json")
      .post(Json.toJson(ldapConfig))
      .map { res =>
        res.status match {
          case 200 => Right((res.json \ "results").validate[LdapConfiguration].get)
          case 404 => Left(Errors(Seq(Error(404, "API not found"))))
          case _ => mapErrors(res)
        }
      }
  }
  override def update(ldapConfig: LdapConfiguration): Future[Either[Errors, Boolean]] = {
    ws.url(s"$serviceUri/ldapconfig")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json")
      .put(Json.toJson(ldapConfig))
      .map { res =>
        res.status match {
          case 200 => Right(true)
          case 404 => Left(Errors(Seq(Error(404, "API not found"))))
          case _ => mapErrors(res)
        }
      }
  }
  override def get(): Future[Either[Errors, Seq[LdapConfiguration]]]={
    ws.url(s"$serviceUri/ldapconfig")
      .withHeaders(
        "Accept" -> "application/json")
      .get
      .map { res =>
        res.status match {
          case 200 => Right((res.json \ "results").validate[Seq[LdapConfiguration]].get)
          case 404 => Left(Errors(Seq(Error(404, "API not found"))))
          case _ => mapErrors(res)
        }
      }
  }
}
