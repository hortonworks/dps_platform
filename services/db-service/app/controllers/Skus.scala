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

import com.hortonworks.dataplane.commons.domain.Entities.Sku
import domain.SkuRepo
import play.api.mvc.Action

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Skus @Inject()(skuRepo: SkuRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def all = Action.async {
    skuRepo.all.map(skus => success(skus)).recoverWith(apiError)
  }

  def load(skuId:Long) = Action.async {
    skuRepo.findById(skuId).map { skuO =>
      skuO.map { u =>
        success(u)
      }
        .getOrElse(NotFound)
    }.recoverWith(apiError)
  }

  def findByName(name:String) = Action.async {
    skuRepo.findByName(name).map { skuO =>
      skuO.map { u =>
        success(u)
      }
        .getOrElse(NotFound)
    }.recoverWith(apiError)
  }

  def insert = Action.async(parse.json) { req =>
    req.body
      .validate[Sku]
      .map { sku =>
        skuRepo
          .insert(sku.name, sku.description)
          .map { u =>
            success(u)
          }.recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def delete(skuId: Long) = Action.async {
    val deleteFuture = skuRepo.deleteById(skuId)
    deleteFuture.map (i => success(i)).recoverWith(apiError)
  }
}