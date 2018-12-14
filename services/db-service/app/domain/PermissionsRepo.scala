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

import com.hortonworks.dataplane.commons.domain.Entities.{Permission, RolePermission, UserPermission}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

@Singleton
class PermissionsRepo @Inject()(
    protected val userRepo: UserRepo,
    protected val roleRepo: RoleRepo,
    protected val dbConfigProvider: DatabaseConfigProvider)
    extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Permissions = TableQuery[PermissionsTable]

  def all(): Future[List[Permission]] = db.run {
    Permissions.to[List].result
  }

  def insert(permission: Permission): Future[Permission] = {
    db.run {
      Permissions returning Permissions += permission
    }
  }

  def userPermissions(username: String):Future[UserPermission] = {
    val query = for {
      users <- userRepo.Users if users.username === username
      roles <- roleRepo.Roles
      userRoles <- userRepo.UserRoles  if roles.id === userRoles.roleId if users.id === userRoles.userId
      permissions <- Permissions if roles.id === permissions.roleId
    } yield (roles.roleName, permissions.permission)

    val result = db.run(query.result)
    val perms = result.map { x =>
      x.groupBy(_._1).map { case (k, v) => RolePermission(k, v.map(_._2)) }
    }
    perms.map { p => UserPermission(username,p.toSeq)}
  }

  def findById(permissionId: Long): Future[Option[Permission]] = {
    db.run(Permissions.filter(_.id === permissionId).result.headOption)
  }

  final class PermissionsTable(tag: Tag)
      extends Table[Permission](tag, Some("dataplane"), "permissions") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def permission = column[String]("permission")
    def roleId = column[Option[Long]]("role_id")
    def created = column[Option[LocalDateTime]]("created")
    def updated = column[Option[LocalDateTime]]("updated")
    def role = foreignKey("user", roleId, roleRepo.Roles)(_.id)
    def * =
      (id, permission, roleId, created, updated) <> ((Permission.apply _).tupled, Permission.unapply)
  }

}
