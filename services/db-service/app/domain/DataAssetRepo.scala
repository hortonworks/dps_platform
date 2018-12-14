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

import com.hortonworks.dataplane.commons.domain.Entities.{AssetsAndCounts, DataAsset}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsValue, Json, Reads}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global



@Singleton
class DataAssetRepo @Inject()(
                               protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val AllDatasetAssets = TableQuery[DatasetAssetTable]
  def DatasetAssets = AllDatasetAssets.filter(_.state === "Active")
  def DatasetEditAssets = AllDatasetAssets.filter(_.editFlag === "Mark_Add")


  def allWithDatasetId(datasetId: Long, queryName: String, offset: Long, limit: Long, state: Option[String]): Future[AssetsAndCounts] = {
    val baseTableQuery = if (state.getOrElse("") == "Edit") DatasetEditAssets else DatasetAssets
    db.run(for {
      count <- baseTableQuery.filter(record => record.datasetId === datasetId && record.assetName.like(s"%$queryName%")).length.result
      assets <- baseTableQuery.filter(record => record.datasetId === datasetId && record.assetName.like(s"%$queryName%")).drop(offset).take(limit).to[List].result
    } yield (assets, count)).map {
      case (assets, count) => AssetsAndCounts(assets, count)
    }
  }


  def insert(dataAsset: DataAsset): Future[DataAsset] = {
    db.run {
      AllDatasetAssets returning AllDatasetAssets += dataAsset
    }
  }

  def findById(id: Long): Future[Option[DataAsset]] = {
    db.run(DatasetAssets.filter(_.id === id).result.headOption)
  }

  def findByGuid(guid: String): Future[Option[DataAsset]] = {
    db.run(DatasetAssets.filter(_.guid === guid).result.headOption)
  }

  def deleteById(id: Long): Future[Int] = {
    db.run(DatasetAssets.filter(_.id === id).delete)
  }

  final class DatasetAssetTable(tag: Tag)
    extends Table[DataAsset](tag, Some("dataplane"), "data_asset") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def assetType = column[String]("asset_type")

    def assetName = column[String]("asset_name")

    def guid = column[String]("guid")

    def assetProperties = column[JsValue]("asset_properties")

    def clusterId = column[Long]("cluster_id")

    def datasetId = column[Option[Long]]("dataset_id")

    def state = column[Option[String]]("state")

    def editFlag = column[Option[String]]("edit_flag")

    def * =
      (id,
        assetType,
        assetName,
        guid,
        assetProperties,
        clusterId,
        datasetId,
        state,
        editFlag) <> ((DataAsset.apply _).tupled, DataAsset.unapply)

  }

}


