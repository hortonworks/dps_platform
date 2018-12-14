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
import javax.inject.Inject

import com.hortonworks.dataplane.commons.domain.Entities.{Role, UserRole, UserRoles}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.Future


class RoleRepo @Inject()( protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Roles = TableQuery[RolesTable]

  def all(): Future[List[Role]] = db.run {
    Roles.to[List].result
  }

  def insert(roleName: String): Future[Role] = {
    val role = Role(roleName = roleName)
    db.run {
      Roles returning Roles += role
    }
  }

  def findById(roleId: Long): Future[Option[Role]] = {
    db.run(Roles.filter(_.id === roleId).result.headOption)
  }

  final class RolesTable(tag: Tag) extends Table[Role](tag, Some("dataplane"), "roles") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def roleName = column[String]("name")

    def created = column[Option[LocalDateTime]]("created")

    def updated = column[Option[LocalDateTime]]("updated")

    def * = (id, roleName, created, updated) <> ((Role.apply _).tupled, Role.unapply)
  }



}
