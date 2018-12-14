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

import com.hortonworks.dataplane.commons.domain.Entities.{Dataset, DataAsset, DatasetAndTags, DatasetCreateRequest}
import domain.API.{dpClusters, users}
import domain.{DatasetRepo, PaginatedQuery, SortQuery}
import play.api.libs.json.{Json, __}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Datasets @Inject()(datasetRepo: DatasetRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def all(name: Option[String]) = Action.async {
    (name match {
      case Some(name) => datasetRepo.findByName(name)
      case None => datasetRepo.all
    }).map(dataset => success(dataset.map(c => linkData(c, makeLink(c))))).recoverWith(apiError)
  }

  private def getPaginatedQuery(req: Request[AnyContent]): Option[PaginatedQuery] = {
    val offset = req.getQueryString("offset")
    val size = req.getQueryString("size")
    val sortCol = req.getQueryString("sortBy")
    val sortOrder = req.getQueryString("sortOrder").getOrElse("asc")

    if (size.isDefined && offset.isDefined) {
      val sortQuery = sortCol.map(s => SortQuery(s, sortOrder))
      Some(PaginatedQuery(offset.get.toInt, size.get.toInt, sortQuery))
    } else None

  }

  private def isNumeric(str: String) = scala.util.Try(str.toLong).isSuccess

  def allRichDataset(userId: Long, filter: Option[String], searchText: Option[String]) = Action.async { req =>
    datasetRepo.getRichDataSet(searchText, getPaginatedQuery(req), userId, filter)
      .map(dc => success(dc.map(c => linkData(c, makeLink(c.dataset)))))
      .recoverWith(apiError)
  }

  def richDatasetByTag(tagName: String, userId: Long,filter: Option[String], searchText: Option[String] ) = Action.async { req =>
    datasetRepo.getRichDatasetByTag(tagName, searchText, getPaginatedQuery(req), userId, filter)
      .map(dc => success(dc.map(c => linkData(c, makeLink(c.dataset)))))
      .recoverWith(apiError)
  }

  def richDatasetById(id: Long) = Action.async { req=>
    val userId = req.getQueryString("userId")
    if(userId.isEmpty || !isNumeric(userId.get)) Future.successful(BadRequest)
    else{
      datasetRepo.getRichDatasetById(id,userId.get.toLong).map { co =>
        co.map { c =>
          success(linkData(c, makeLink(c.dataset)))
        }
          .getOrElse(NotFound)
      }.recoverWith(apiError)
    }
  }

  def datasetsByNames(names: String) = Action.async { req =>
    val namesAsArray = names.split(",")
    datasetRepo.findByNames(namesAsArray)
      .map(datasets => success(datasets.map(dataSet => linkData(dataSet, makeLink(dataSet)))))
      .recoverWith(apiError)
  }

  private def makeLink(c: Dataset) = {
    Map("datalake" -> s"${dpClusters}/${c.dpClusterId}",
      "users" -> s"${users}/${c.createdBy.get}")
  }

  def load(datasetId: Long) = Action.async {
    datasetRepo.findByIdWithCategories(datasetId).map { co =>
      co.map { c =>
        success(linkData(c, makeLink(c.dataset)))
      }
        .getOrElse(NotFound)
    }.recoverWith(apiError)
  }

  def delete(datasetId: Long) = Action.async { req =>
    val future = datasetRepo.archiveById(datasetId)
    future.map(i => success(i)).recoverWith(apiError)
  }

  def updateDatset(datasetId: String) = Action.async(parse.json) { req =>
    req.body
      .validate[Dataset]
      .map { dataset =>
        if(!isNumeric(datasetId)) Future.successful(BadRequest)
        else {
          datasetRepo.updateDatset(datasetId.toLong, dataset).map{ ds =>
            ds.map { d =>
              success(Json.toJson(d))
            }
              .getOrElse(NotFound)
          }.recoverWith(apiError)
        }
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def add = Action.async(parse.json) { req =>
    req.body
      .validate[DatasetAndTags]
      .map { cl =>
        val created = datasetRepo.insertWithCategories(cl)
        created.map(c => success(linkData(c)))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def addAssets (datasetId: Long) = Action.async(parse.json) { req =>
    req.body
      .validate[Seq[DataAsset]]
      .map { assets =>
        datasetRepo
          .addAssets(datasetId, assets)
          .map(c => success(linkData(c)))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def removeAssets (datasetId: Long, ids: Seq[String]) = Action.async { req =>
    datasetRepo
      .removeAssets(datasetId, ids)
      .map(c => success(linkData(c)))
      .recoverWith(apiError)
  }

  def removeAllAssets (datasetId: Long) = Action.async { req =>
    datasetRepo
      .removeAllAssets(datasetId)
      .map(c => success(linkData(c)))
      .recoverWith(apiError)
  }

  def beginEdit (datasetId: Long) = Action.async(parse.json) { req =>
    req.body
      .validate[String]
      .map { userId =>
        datasetRepo
          .beginEdit(datasetId, userId.toLong)
          .map(c => success(linkData(c)))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def saveEdit (datasetId: Long) = Action.async(parse.json) { req =>
    datasetRepo
      .saveEdit(datasetId)
      .map(c => success(linkData(c)))
      .recoverWith(apiError)
  }

  def revertEdit (datasetId: Long) = Action.async(parse.json) { req =>
    datasetRepo
      .revertEdit(datasetId)
      .map(c => success(linkData(c)))
      .recoverWith(apiError)
  }

  def addWithAsset = Action.async(parse.json) { req =>
    req.body
      .validate[DatasetCreateRequest]
      .map { cl =>
        val created = datasetRepo.create(cl)
        created.map(c => success(linkData(c, makeLink(c.dataset))))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def update = Action.async(parse.json) { req =>
    req.body
      .validate[DatasetAndTags]
      .map { cl =>
        val updated = datasetRepo.updateWithCategories(cl)
        updated.map(c => success(linkData(c)))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def findManagedAssets(clusterId: Long) = Action.async(parse.json) { request =>
    request.body
      .validate[Seq[String]]
      .map { assets =>
        datasetRepo.queryManagedAssets(clusterId, assets)
          .map(result => success(Json.toJson(result)))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }


}
