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

import java.time.LocalDateTime
import javax.inject._

import com.hortonworks.dataplane.commons.domain.Entities.{Dataset, DatasetDetails}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue

import scala.concurrent.Future

@Singleton
class DatasetDetailsRepo @Inject()(
    protected val dbConfigProvider: DatabaseConfigProvider)
    extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val DatasetDetailsTable = TableQuery[DatasetDetailsTable]

  def allWithDatasetId(datasetId:Long): Future[List[DatasetDetails]] = db.run {
    DatasetDetailsTable.filter(_.datasetId === datasetId).to[List].result
  }

  def insert(datasetDetails: DatasetDetails): Future[DatasetDetails] = {
    db.run {
      DatasetDetailsTable returning DatasetDetailsTable += datasetDetails
    }
  }

  def findById(id: Long): Future[Option[DatasetDetails]] = {
    db.run(DatasetDetailsTable.filter(_.id === id).result.headOption)
  }

  def deleteById(id: Long): Future[Int] = {
    db.run(DatasetDetailsTable.filter(_.id === id).delete)
  }

  final class DatasetDetailsTable(tag: Tag)
      extends Table[DatasetDetails](tag, Some("dataplane"), "dataset_details") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def details = column[Option[JsValue]]("details")

    def datasetId = column[Long]("dataset_id")

    def * =
      (id,
       details,
       datasetId
      ) <> ((DatasetDetails.apply _).tupled, DatasetDetails.unapply)

  }

}
