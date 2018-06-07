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
import javax.inject.{Inject, Singleton}

import com.hortonworks.dataplane.commons.domain.Entities.{AssetWorkspace, AssetWorkspaceRequest, DataAsset}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.Future

@Singleton
class AssetWorkspaceRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider,
                                   protected val assetRepo: DataAssetRepo)
  extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._
  import scala.concurrent.ExecutionContext.Implicits.global

  val AssetWorkspaces = TableQuery[AssetWorkspacesTable]

  def all(): Future[List[AssetWorkspace]] = db.run {
    AssetWorkspaces.to[List].result
  }

  def insert(assetWorkspace: AssetWorkspace): Future[AssetWorkspace] = {
    db.run {
      AssetWorkspaces returning AssetWorkspaces += assetWorkspace
    }
  }

  def getAssets(workspaceId: Long): Future[Seq[DataAsset]] = {
    val query = AssetWorkspaces.filter(_.workspaceId === workspaceId)
      .join(assetRepo.DatasetAssets).on(_.assetId === _.id)
      .map(_._2)

    db.run(query.to[List].result)
  }

  def insert(assetWorkspaceRequest: AssetWorkspaceRequest): Future[Seq[DataAsset]] = {
    val assets = assetWorkspaceRequest.dataAssets
    val assetGuids = assets.map(_.guid)

    val query = for {
      existingAssets <- assetRepo.DatasetAssets.filter(_.guid.inSet(assetGuids)).to[List].result
      _ <- {
        val guids = existingAssets.map(_.guid)
        assetRepo.DatasetAssets ++= assets.filter(a => !guids.contains(a.guid))
      }
      assets <- assetRepo.DatasetAssets.filter(_.guid.inSet(assetGuids)).to[List].result
      _ <- AssetWorkspaces.filter(_.workspaceId === assetWorkspaceRequest.workspaceId).delete
      _ <- AssetWorkspaces ++= assets.map(a => AssetWorkspace(a.assetType, a.id.get, assetWorkspaceRequest.workspaceId))
    } yield assets

    db.run(query.transactionally)
  }

  def deleteById(assetWorkspaceId: Long): Future[Int] = {
    val AssetWorkspace = db.run(AssetWorkspaces.filter(_.workspaceId === assetWorkspaceId).delete)
    AssetWorkspace
  }


  final class AssetWorkspacesTable(tag: Tag) extends Table[AssetWorkspace](tag, Some("dataplane"), "data_asset_workspace") {

    def assetType = column[String]("asset_type")

    def assetId = column[Long]("asset_id")

    def workspaceId = column[Long]("workspace_id")

    def * = (assetType, assetId, workspaceId) <> ((AssetWorkspace.apply _).tupled, AssetWorkspace.unapply)
  }

}
