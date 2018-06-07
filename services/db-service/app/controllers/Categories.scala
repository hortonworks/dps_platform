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

import domain.CategoryRepo
import com.hortonworks.dataplane.commons.domain.Entities.Category
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Categories @Inject()(categoryRepo: CategoryRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def all = Action.async {
    categoryRepo.all.map(categorys => success(categorys)).recoverWith(apiError)
  }

  def get(name: String) = Action.async {
    categoryRepo.findByName(name).map { uo =>
      uo.map { u =>
        success(u)
      }
        .getOrElse(notFound)
    }.recoverWith(apiError)
  }

  def search(searchText: String, size: Option[Long]) = Action.async {
    categoryRepo.searchByName(searchText, size.getOrElse(Long.MaxValue))
      .map(categorys => success(categorys)).recoverWith(apiError)
  }

  def insert = Action.async(parse.json) { req =>
    req.body
      .validate[Category]
      .map { category =>
        categoryRepo
          .insert(category)
          .map { c =>
            success(c)
          }.recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def load(categoryId: Long) = Action.async {
    categoryRepo.findById(categoryId).map { uo =>
      uo.map { u =>
        success(u)
      }
        .getOrElse(NotFound)
    }.recoverWith(apiError)
  }

  def delete(categoryId: Long) = Action.async { req =>
    val future = categoryRepo.deleteById(categoryId)
    future.map(i => success(i)).recoverWith(apiError)
  }

}
