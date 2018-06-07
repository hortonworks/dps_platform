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

import com.hortonworks.dataplane.commons.domain.Entities.{Workspace, WorkspaceDataCount, WorkspaceDetails}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.Future

@Singleton
class WorkspaceRepo @Inject()(protected val assetWorkspaceRepo: AssetWorkspaceRepo,
                              protected val notebookWorkspaceRepo: NotebookWorkspaceRepo,
                              protected val userRepo: UserRepo,
                              protected val clusterRepo: ClusterRepo,
                              protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._
  import scala.concurrent.ExecutionContext.Implicits.global


  val Workspaces = TableQuery[WorkspacesTable]

  def all(): Future[List[Workspace]] = db.run {
    Workspaces.to[List].result
  }

  private def getWorkspaceWithNameQuery(inputQuery: Query[WorkspacesTable, Workspace, Seq]) = {
    for {
      ((workspace, user), cluster) <- (inputQuery
        .join(userRepo.Users).on(_.createdBy === _.id))
        .join(clusterRepo.Clusters).on(_._1.source === _.id)
    } yield (workspace, user.username, cluster.name)
  }

  private def getWorkspaceDetails(inputQuery: Query[WorkspacesTable, Workspace, Seq]): Future[List[WorkspaceDetails]] = {
    val assetCountQuery = assetWorkspaceRepo.AssetWorkspaces.groupBy(_.workspaceId).map {
      case (s, results) => (s -> results.length)
    }

    val appCountQuery = notebookWorkspaceRepo.NotebookWorkspaces.groupBy(_.workspaceId).map {
      case (s, results) => (s -> results.length)
    }

    val query = for {
      (((workspace, username, clustername), asset), notebook)
      <- (getWorkspaceWithNameQuery(inputQuery).joinLeft(assetCountQuery).on(_._1.id === _._1))
        .joinLeft(appCountQuery).on(_._1._1.id === _._1)
    } yield (workspace, username, clustername, asset.map(_._2).getOrElse(0), notebook.map(_._2).getOrElse(0))

    db.run(query.to[List].result).map {
      rows =>
        rows.map {
          case (workspace, username, clustername, assetCount, notebookCount) =>
            WorkspaceDetails(workspace, username, clustername,
              Some(WorkspaceDataCount(assetCount, notebookCount)))
        }
    }
  }

  def allWithDetails(): Future[List[WorkspaceDetails]] = {
    getWorkspaceDetails(Workspaces)
  }

  def insert(workspace: Workspace): Future[Workspace] = {
    db.run {
      Workspaces returning Workspaces += workspace
    }
  }

  def deleteById(workspaceId: Long): Future[Int] = {
    val workspace = db.run(Workspaces.filter(_.id === workspaceId).delete)
    workspace
  }

  def deleteByName(name: String): Future[Int] = {
    val workspace = db.run(Workspaces.filter(_.name === name).delete)
    workspace
  }

  def findById(workspaceId: Long): Future[Option[Workspace]] = {
    db.run(Workspaces.filter(_.id === workspaceId).result.headOption)
  }

  def findByName(name: String): Future[Option[Workspace]] = {
    db.run(Workspaces.filter(_.name === name).result.headOption)
  }

  def findByNameWithDetails(name: String): Future[Option[WorkspaceDetails]] = {
    getWorkspaceDetails(Workspaces.filter(_.name === name)).map(_.headOption)
  }

  def findByUserId(userId: Long): Future[Seq[Workspace]] = {
    db.run(Workspaces.filter(_.createdBy === Option(userId)).to[List].result)
  }

  final class WorkspacesTable(tag: Tag) extends Table[Workspace](tag, Some("dataplane"), "workspace") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def source = column[Long]("source")

    def description = column[String]("description")

    def createdBy = column[Option[Long]]("createdby")

    def created = column[Option[LocalDateTime]]("created")

    def updated = column[Option[LocalDateTime]]("updated")

    def * = (id, name, source, description, createdBy, created, updated) <> ((Workspace.apply _).tupled, Workspace.unapply)
  }

}
