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

import com.hortonworks.dataplane.commons.domain.Entities.{Category, CategoryCount, Errors}
import com.hortonworks.dataplane.db.Webservice.CategoryService
import com.typesafe.config.Config
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CategoryServiceImpl(config: Config)(implicit ws: WSClient)
  extends CategoryService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  override def list(): Future[Either[Errors, Seq[Category]]] = {
    ws.url(s"$url/categories")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCategories)
  }

  def search(searchText: String, size: Option[Long]): Future[Either[Errors, Seq[Category]]] = {
    ws.url(s"$url/categories/search/$searchText?size=${size.getOrElse(Long.MaxValue)}")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCategories)
  }

  def listWithCount(queryString: String, userId: Long): Future[Either[Errors, Seq[CategoryCount]]] = {
    ws.url(s"$url/categoriescount?userId=$userId&$queryString")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCategoriesCount)
  }

  def listWithCount(categoryName: String): Future[Either[Errors, CategoryCount]] = {
    ws.url(s"$url/categoriescount/$categoryName")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCategoryCount)
  }

  override def create(category: Category): Future[Either[Errors, Category]] = {
    ws.url(s"$url/categories")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .post(Json.toJson(category))
      .map(mapToCategory)

  }

  override def retrieve(categoryId: String): Future[Either[Errors, Category]] = {
    ws.url(s"$url/categories/$categoryId")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCategory)
  }

  override def delete(categoryId: String): Future[Either[Errors, Category]] = {
    ws.url(s"$url/categories/$categoryId")
      .withHeaders("Accept" -> "application/json")
      .delete()
      .map(mapToCategory) // TODO fix the return type for delete
  }

  private def mapToCategories(res: WSResponse) = {
    res.status match {
      case 200 =>
        Right((res.json \ "results").as[Seq[JsValue]].map { d =>
          d.validate[Category].get
        })
      case _ => mapErrors(res)
    }
  }

  private def mapToCategory(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[Category](
          res,
          r => (r.json \\ "results").head.validate[Category].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToCategoriesCount(res: WSResponse) = {
    res.status match {
      case 200 =>
        Right(((res.json \ "results").as[Seq[JsValue]].map { d =>
          d.validate[CategoryCount].get
        }))
      case _ => mapErrors(res)
    }
  }

  private def mapToCategoryCount(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[CategoryCount](
          res,
          r => (r.json \\ "results").head.validate[CategoryCount].get)
      case _ => mapErrors(res)
    }
  }
}
