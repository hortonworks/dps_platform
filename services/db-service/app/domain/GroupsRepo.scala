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

import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.RoleType
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GroupsRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider,
                           protected val roleRepo: RoleRepo,
                           private val rolesUtil: RolesUtil) extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Groups = TableQuery[GroupsTable]
  val GroupsRoles = TableQuery[GroupsRolesTable]

  def all(): Future[List[Group]] = db.run {
    Groups.to[List].result
  }

  def getAllActiveGroups(): Future[List[Group]] = db.run {
    Groups.filter(_.active===true).to[List].result
  }

  def allWithRoles(offset: Long = 0, pageSize: Long = 10, searchTerm: Option[String]): Future[GroupsList] = {
    val query = searchTerm match {
      case Some(searchTerm) => Groups.filter(_.groupName like (s"%$searchTerm%")).sortBy(_.updated.desc).drop(offset).take(pageSize)
      case None => Groups.sortBy(_.updated.desc).drop(offset).take(pageSize)
    }

    val countQuery = searchTerm match {
      case Some(searchTerm) => Groups.filter(_.groupName like (s"%$searchTerm%")).length
      case None => Groups.length
    }
    for {
      groups <- db.run(query.result).flatMap { groups =>
        val userIds = groups.map(res => res.id.get).seq
        db.run(GroupsRoles.filter(_.groupId inSet userIds).result).flatMap { groupRoles =>
          val roleIdUsersMap = getRolesMap(groupRoles)
          roleRepo.all().map { allRoles =>
            groups.map { group =>
              val grpRoles = roleIdUsersMap.get(group.id.get) match {
                case Some(roles) => rolesUtil.getRolesAsRoleTypes(roles, allRoles)
                case None => Seq()
              }
              GroupInfo(id = group.id, groupName = group.groupName, displayName = group.displayName, roles = grpRoles, active = group.active)
            }
          }
        }
      }
      count <- db.run(countQuery.result).map(c => c)
    } yield {
      GroupsList(count, groups)
    }
  }

  private def getRolesMap(groupRoles: Seq[GroupRole]) = {
    val groupIdRolesMap = mutable.Map.empty[Long, ArrayBuffer[Long]]
    groupRoles.foreach { groupRole =>
      if (groupIdRolesMap.contains(groupRole.groupId.get)) {
        groupIdRolesMap.get(groupRole.groupId.get).get += groupRole.roleId.get
      } else {
        val roleIdBuff = mutable.ArrayBuffer.empty[Long]
        roleIdBuff += groupRole.roleId.get
        groupIdRolesMap.put(groupRole.groupId.get, roleIdBuff)
      }
    }
    groupIdRolesMap
  }

  def getRolesForGroup(groupName: String): Future[GroupRoles] = {
    val query = for {
      groups <- Groups if groups.groupName === groupName
      roles <- roleRepo.Roles
      groupRoles <- GroupsRoles if roles.id === groupRoles.roleId if groups.id === groupRoles.groupId
    } yield (roles.roleName)

    val result = db.run(query.result)
    result.map(r => GroupRoles(groupName, r))
  }

  def addGroupWithRoles(groupInfo: GroupInfo) = {
    val group = Group(groupName = groupInfo.groupName, displayName = groupInfo.displayName, active = groupInfo.active)
    rolesUtil.getRoleNameMap().flatMap { roleNameMap =>
      val query = for {
        group <- Groups returning Groups += group
        groupRoles <- {
          val groupRolesList = rolesUtil.getGroupRoleObjects(group.id.get, groupInfo.roles, roleNameMap)
          GroupsRoles returning GroupsRoles ++= groupRolesList
        }
      } yield {
        (group, groupRoles)
      }
      db.run(query.transactionally).map(res => groupInfo)
    }
  }

  def getGroupByName(groupName: String):Future[GroupInfo] = {
    for {
      (group, groupRoles) <- getGroupDetailInternal(groupName)
      roleIdMap <- rolesUtil.getRoleIdMap
    } yield {
      val roles = groupRoles.map { userRoleObj =>
        val roleName: String = roleIdMap(userRoleObj._2.get.roleId.get).roleName
        RoleType.withName(roleName)
      }
      GroupInfo(id = group.id, groupName = group.groupName, displayName = group.displayName, roles = roles, active = group.active)
    }
  }
  def groupExists(groupName:String): Future[Boolean] ={
    db.run(Groups.filter(_.groupName===groupName).result.headOption).map(res=>res.isDefined)
  }
  def getRolesForGroups(groupIds:Seq[Long])={
    val query=GroupsRoles.filter(_.groupId.inSet(groupIds)).to[List].result
    db.run(query).flatMap{res=>
      val roleIds:Seq[Long]=res.map(role=>role.roleId.get)
      rolesUtil.getRoleTypesForRoleIds(roleIds)
    }
  }

  def getGroupDetailInternal(groupName: String) = {
    val query = for {
      (group, groupRole) <- Groups.filter(_.groupName === groupName) joinLeft GroupsRoles on (_.id === _.groupId)
    } yield {
      (group, groupRole)
    }
    db.run(query.result).map { results =>
      val group: Group = results.head._1
      val roles = results.filter(res => res._2.isDefined)
      (group, roles)
    }
  }

  private def resolveGroupRolesEntries(roles: Seq[RoleType.Value], groupRoles: Seq[GroupRole]): Future[(Seq[Long], Seq[Long])] = {
    rolesUtil.getRoleNameMap().map { roleNameMap =>
      val requiredRoleIds: Seq[Long] = roles.map(roleType => roleNameMap(roleType.toString).id.get)
      val existingRoleIds: Seq[Long] = groupRoles.map(groupRole => groupRole.roleId.get)
      val toBeAdded = requiredRoleIds.filterNot(existingRoleIds.contains(_))
      val toBeDeleted = existingRoleIds.filterNot(requiredRoleIds.contains(_))
      val toBeDeletedRoles = groupRoles.filter { grpRoles =>
        toBeDeleted.contains(grpRoles.roleId.get)
      }
      val toBeDeletedIDs = toBeDeletedRoles.map { groupRole =>
        groupRole.id.get
      }
      (toBeAdded, toBeDeletedIDs)
    }
  }

  def updateGroupInfo(groupInfo: GroupInfo) = {
    for {
      (group, groupRoles) <- getGroupDetailInternal(groupInfo.groupName)
      groupRoles <- db.run(GroupsRoles.filter(_.groupId === group.id.get).result)
      (toBeAddedRoleIds, toBeDeletedRoleIds) <- resolveGroupRolesEntries(groupInfo.roles, groupRoles)
    } yield {
      val groupRoleObjs = rolesUtil.getGroupRolesObjectsforRoleIds(group.id.get, toBeAddedRoleIds)
      val query = for {
        updateActive <- getUpdateStatusQuery(groupInfo)
        insertQuery <- GroupsRoles returning GroupsRoles ++= groupRoleObjs
        delQuery <- GroupsRoles.filter(_.id inSet toBeDeletedRoleIds).delete
      } yield {
        (updateActive, delQuery, insertQuery)
      }
      db.run(query.transactionally)
    }
  }

  private def getUpdateStatusQuery(groupInfo: GroupInfo) = {
    Groups.filter(_.groupName === groupInfo.groupName)
      .map { r =>
        (r.active, r.updated)
      }
      .update(groupInfo.active, Some(LocalDateTime.now()))
  }

  final class GroupsTable(tag: Tag) extends Table[Group](tag, Some("dataplane"), "groups") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def groupName = column[String]("group_name")

    def displayName = column[String]("display_name")

    def active = column[Option[Boolean]]("active")

    def created = column[Option[LocalDateTime]]("created")

    def updated = column[Option[LocalDateTime]]("updated")

    def * = (id, groupName, displayName, active, created, updated) <> ((Group.apply _).tupled, Group.unapply)
  }

  final class GroupsRolesTable(tag: Tag) extends Table[(GroupRole)](tag, Some("dataplane"), "groups_roles") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def groupId = column[Option[Long]]("group_id")

    def roleId = column[Option[Long]]("role_id")

    def group = foreignKey("group_groupRole", groupId, Groups)(_.id)

    def role = foreignKey("role_groupRole", roleId, roleRepo.Roles)(_.id)

    def * = (id, groupId, roleId) <> ((GroupRole.apply _).tupled, GroupRole.unapply)

  }

}
