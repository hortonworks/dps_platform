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

import com.hortonworks.dataplane.commons.domain.Entities.{Dataset, UnclassifiedDataset}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue

import scala.concurrent.Future

@Singleton
class UnclassifiedDatasetRepo @Inject()(
    protected val dbConfigProvider: DatabaseConfigProvider)
    extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val UnclassifiedDatasets = TableQuery[UnclassifiedDatasetsTable]

  def all(): Future[List[UnclassifiedDataset]] = db.run {
    UnclassifiedDatasets.to[List].result
  }

  def insert(dataset: UnclassifiedDataset): Future[UnclassifiedDataset] = {
    db.run {
      UnclassifiedDatasets returning UnclassifiedDatasets += dataset
    }
  }

  def findById(datasetId: Long): Future[Option[UnclassifiedDataset]] = {
    db.run(UnclassifiedDatasets.filter(_.id === datasetId).result.headOption)
  }

  def deleteById(datasetId: Long): Future[Int] = {
    db.run(UnclassifiedDatasets.filter(_.id === datasetId).delete)
  }

  final class UnclassifiedDatasetsTable(tag: Tag)
      extends Table[UnclassifiedDataset](tag, Some("dataplane"), "unclassified_datasets") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def description = column[Option[String]]("description")

    def dpClusterId = column[Long]("dp_clusterid")

    def createdBy = column[Long]("createdby")

    def createdOn = column[Option[LocalDateTime]]("createdon")

    def lastmodified = column[Option[LocalDateTime]]("lastmodified")

    def customprops = column[Option[JsValue]]("custom_props")

    def * =
      (id,
       name,
       description,
       dpClusterId,
       createdBy,
       createdOn,
       lastmodified,
       customprops
      ) <> ((UnclassifiedDataset.apply _).tupled, UnclassifiedDataset.unapply)

  }

}
