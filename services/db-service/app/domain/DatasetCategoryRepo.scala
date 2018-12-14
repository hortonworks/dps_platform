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

package domain

import javax.inject._

import com.hortonworks.dataplane.commons.domain.Entities.{CategoryCount, DatasetCategory}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.Future

@Singleton
class DatasetCategoryRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider,
                                    protected val categoryRepo: CategoryRepo
                                   ) extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._
  import scala.concurrent.ExecutionContext.Implicits.global

  val DatasetCategories = TableQuery[DatasetCategoriesTable]

  def insert(datasetCategory: DatasetCategory): Future[DatasetCategory] = {
    db.run {
      DatasetCategories returning DatasetCategories += datasetCategory
    }
  }

  def getCategoryCount(categoryName: String): Future[CategoryCount] = {
    val query = (categoryRepo.Categories.filter(_.name === categoryName)
      .join(DatasetCategories).on(_.id === _.categoryId)).length

    db.run(query.result).map {
      count =>
        CategoryCount(categoryName, count)
    }
  }

  def allWithCategoryId(categoryId: Long): Future[List[DatasetCategory]] = {
    db.run(DatasetCategories.filter(_.categoryId === categoryId).to[List].result)
  }

  def allWithDatasetId(datasetId: Long): Future[List[DatasetCategory]] = {
    db.run(DatasetCategories.filter(_.datasetId === datasetId).to[List].result)
  }

  def deleteById(categoryId: Long, datasetId: Long): Future[Int] = {
    db.run(DatasetCategories.filter(d => d.datasetId === datasetId && d.categoryId === categoryId).delete)
  }

  final class DatasetCategoriesTable(tag: Tag) extends Table[DatasetCategory](tag, Some("dataplane"), "dataset_categories") {

    def categoryId = column[Long]("category_id")

    def datasetId = column[Long]("dataset_id")

    def * = (categoryId, datasetId) <> ((DatasetCategory.apply _).tupled, DatasetCategory.unapply)
  }

}
