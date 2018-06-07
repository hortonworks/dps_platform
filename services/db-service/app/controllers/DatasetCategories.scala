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

import com.hortonworks.dataplane.commons.domain.Entities.{CategoryCount, DatasetCategory}
import domain.{DatasetCategoryRepo, DatasetRepo}
import play.api.libs.json.Json
import play.api.mvc.Action

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DatasetCategories @Inject()(datasetCategoryRepo: DatasetCategoryRepo,
                                  datasetRepo: DatasetRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def allWithCategoryId(categoryId: Long) = Action.async {
    datasetCategoryRepo.allWithCategoryId(categoryId).map(dc => success(dc)).recoverWith(apiError)
  }

  def allWithDatasetId(datasetId: Long) = Action.async {
    datasetCategoryRepo.allWithDatasetId(datasetId).map(dc => success(dc)).recoverWith(apiError)
  }

  def categoriesCount(searchText: Option[String], filterParam: Option[String],userId: Long) = Action.async { req =>
    (for (
      categoriesCount <- datasetRepo.getCategoriesCount(searchText, userId, filterParam);
      totalDataset <- datasetRepo.count(searchText, Some(userId), filterParam)
    ) yield {
      val list = Seq(CategoryCount("ALL", totalDataset)) ++ categoriesCount
      success(list)
    }).recoverWith(apiError)
  }

  def categoriesCountByName(categoryName: String) = Action.async {
    (if (categoryName.equals("All")) {
      datasetRepo.count(None,None, None).map(i => CategoryCount(categoryName, i))
    } else datasetCategoryRepo.getCategoryCount(categoryName)
      ).map(dc => success(Json.toJson(dc))).recoverWith(apiError)
  }

  def add = Action.async(parse.json) { req =>
    req.body
      .validate[DatasetCategory]
      .map { dc =>
        datasetCategoryRepo
          .insert(dc)
          .map { u =>
            success(u)
          }.recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def delete(categoryId: Long, datasetId: Long) = Action.async {
    val deleteFuture = datasetCategoryRepo.deleteById(categoryId, datasetId)
    deleteFuture.map(i => success(i)).recoverWith(apiError)
  }
}