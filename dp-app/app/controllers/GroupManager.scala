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

package controllers

import com.google.inject.Inject
import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors, GroupInfo}
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.GroupService
import com.typesafe.scalalogging.Logger
import models.{GroupsAndRolesListInput, GroupsListInput}
import play.api.libs.json.Json
import play.api.mvc.Controller
import services.LdapService

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Left

class GroupManager @Inject()(val ldapService: LdapService,
                             @Named("groupService") val groupService: GroupService)
  extends Controller {
  val logger = Logger(classOf[GroupManager])

  private def handleErrors(errors: Errors) = {
    if (errors.errors.exists(_.status == "400"))
      BadRequest(Json.toJson(errors))
    else
      InternalServerError(Json.toJson(errors))
  }

  def getGroups() = AuthenticatedAction.async { request =>
    groupService.getGroups(request.getQueryString("offset"), request.getQueryString("pageSize"), request.getQueryString("searchTerm")).map {
      case Left(errors) => handleErrors(errors)
      case Right(groups) => Ok(Json.toJson(groups))
    }
  }

  def addAdminGroups() = AuthenticatedAction.async(parse.json) { request =>
    request.body
      .validate[GroupsListInput]
      .map { groupsList =>
        addGroupInternal(groupsList.groups, Seq("SUPERADMIN"))
      }.getOrElse(Future.successful(BadRequest))
  }

  def getGroupsByName(name: String) = AuthenticatedAction.async { request =>
    groupService.getGroupByName(name).map {
      case Left(errors) => handleErrors(errors)
      case Right(group) => Ok(Json.toJson(group))
    }
  }

  def addGroupWithRoles() = AuthenticatedAction.async(parse.json) { request =>
    request.body
      .validate[GroupInfo]
      .map { groupInfo =>
        ldapService
          .search(groupInfo.groupName,Option("group"), false)
          .flatMap {
            case Left(errors) => Future.successful(InternalServerError(Json.toJson(errors)))
            case Right(ldapSearchResult) =>
              if(ldapSearchResult.nonEmpty){
                groupService.addGroupWithRoles(groupInfo).map {
                  case Left(errors) => handleErrors(errors)
                  case Right(updated) => Ok(Json.toJson(updated))
                }
              }else {
                Future.successful(BadRequest)
              }
          }
      }.getOrElse(Future.successful(BadRequest))
  }

  def addGroupsWithRoles = AuthenticatedAction.async(parse.json) { req =>
    req.body.validate[GroupsAndRolesListInput].map { groupsAndRolesInput =>
      val roleTypes = groupsAndRolesInput.roles
      addGroupInternal(groupsAndRolesInput.groups, roleTypes)
    }.getOrElse {
      Future.successful(BadRequest)
    }
  }

  private def addGroupInternal(groupList: Seq[String], roles: Seq[String]) = {

    val futures = groupList.map { groupName =>
      val groupInfo: GroupInfo = GroupInfo(groupName = groupName,
        displayName = groupName,
        active = Some(true),
        roles = roles)
      ldapService.search(groupInfo.groupName, Option("group"), false)
        .flatMap {
          case Left(errors)=> Future.successful(Left(errors))
          case Right(ldapSearchResult) =>
            if(ldapSearchResult.nonEmpty){
              groupService.addGroupWithRoles(groupInfo)
            }else {
              Future.successful(Left(Errors(Seq(Error(500,"Invalid Group")))))
            }
        }
    }
    val successFullyAdded = mutable.ArrayBuffer.empty[GroupInfo]
    val errorsReceived = mutable.ArrayBuffer.empty[Error]
    Future.sequence(futures).map { respList =>
      respList.foreach {
        case Left(error) => errorsReceived ++= error.errors
        case Right(groupInfo) => successFullyAdded += groupInfo
      }
      Ok(Json.toJson(
        Json.obj(
          "successfullyAdded" -> successFullyAdded,
          "errors" -> errorsReceived)))
    }
  }

  def updateGroupInfo() = AuthenticatedAction.async(parse.json) { request =>
    request.body
      .validate[GroupInfo]
      .map { groupInfo =>
        groupService.updateGroupInfo(groupInfo).map {
          case Left(errors) => handleErrors(errors)
          case Right(updated) => Ok(Json.toJson(updated))
        }
      }.getOrElse(Future.successful(BadRequest))
  }
}

