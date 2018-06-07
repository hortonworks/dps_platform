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

import com.hortonworks.dataplane.commons.domain.Entities.{DpConfig, Errors}
import com.hortonworks.dataplane.db.Webservice.ConfigService
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ConfigServiceImpl(config: Config)(implicit ws: WSClient)
    extends ConfigService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))
  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  val logger = Logger(classOf[ConfigServiceImpl])

  override def getConfig(key: String): Future[Option[DpConfig]] = {
    ws.url(s"$url/configurations/$key")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToConfig)
      .recoverWith {
        case e: Exception =>
          logger.error(
            s"Error while loading config key $key, will return none",e)
          Future.successful(None)
      }
  }

  override def addConfig(dpConfig: DpConfig): Future[Either[Errors, DpConfig]] = {
    ws.url(s"$url/configurations")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(dpConfig))
      .map(mapToConfigWithError)
  }
  override def setConfig(key: String,value:String): Future[Either[Errors, DpConfig]] = {
    val dpConfig=DpConfig(id=None,configKey = key,configValue = value) //Note if configkey is not present it will insert.
    ws.url(s"$url/configurations")
      .withHeaders("Accept" -> "application/json")
      .put(Json.toJson(dpConfig))
      .map(mapToConfigWithError)
  }

  private def mapToConfig(res: WSResponse) = {
    res.status match {
      case 200 => (res.json \ "results").validate[DpConfig].asOpt
      case _ => None
    }
  }

  private def mapToConfigWithError(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[DpConfig].get)
      case _ => mapErrors(res)
    }
  }

}
