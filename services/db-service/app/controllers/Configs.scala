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

package controllers

import javax.inject._

import com.hortonworks.dataplane.commons.domain.Entities.DpConfig
import domain.ConfigRepo
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Configs @Inject()(configRepo: ConfigRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def get(key: String) = Action.async {
    configRepo.findByKey(key).map { uo =>
      uo.map { u =>
        success(u)
      }
        .getOrElse(notFound)
    }.recoverWith(apiError)
  }

  def add() = Action.async(parse.json) { request =>
    request.body.validate[DpConfig].map { dpConfig =>
      configRepo.insert(dpConfig).map {
        config => success(config)
      }.recoverWith(apiError)
    }.getOrElse(Future.successful(BadRequest))
  }
  def addOrUpdate() = Action.async(parse.json) {request =>
    request.body.validate[DpConfig].map { dpConfig =>
      configRepo.findByKey(dpConfig.configKey).flatMap {
        case Some(conf) => {
          val dpConfigObj = DpConfig(id = conf.id, configKey = dpConfig.configKey, configValue = dpConfig.configValue, active = dpConfig.active, export = conf.export)
          configRepo.update(dpConfigObj).map { res =>
            success(dpConfigObj)
          }.recoverWith(apiError)
          Future.successful(success(conf))
        }
        case None => {
          val dpConfigObj = DpConfig(id = None, configKey = dpConfig.configKey, configValue = dpConfig.configValue, active = Some(true), export = Some(true))
          configRepo.insert(dpConfigObj).map {config =>
             success(config)
          }.recoverWith(apiError)
        }
      }.recoverWith(apiError)
    }.getOrElse(Future.successful(BadRequest))
  }
}
