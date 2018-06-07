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

import javax.inject.{Inject, Singleton}

import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors, GroupInfo}
import domain.{GroupsRepo, RolesUtil}
import play.api.libs.json.Json
import play.api.mvc.Action

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Groups @Inject()(groupsRepo: GroupsRepo, rolesUtil: RolesUtil)(
  implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def getGroups() = Action.async { request =>
    val offset: Long = request.getQueryString("offset").get.toLong
    val pageSize: Long = request.getQueryString("pageSize").get.toLong
    val searchTerm: Option[String] = request.getQueryString("searchTerm")
    groupsRepo
      .allWithRoles(offset, pageSize, searchTerm)
      .map { groups =>
        success(groups)
      }
      .recoverWith(apiError)
  }
  def getAllActiveGroups()=Action.async{request =>
   groupsRepo.getAllActiveGroups().map{groups=>
     success(groups)
   }.recoverWith(apiError)
  }

  def insertWithRoles = Action.async(parse.json) { req =>
    req.body
      .validate[GroupInfo]
      .map { groupInfo =>
        groupsRepo.groupExists(groupInfo.groupName).flatMap {
          case true=>Future.successful(Conflict(Json.toJson(Errors(Seq(Error(409, s"Group Already Exists:${groupInfo.groupName}"))))))
          case _=>{
            groupsRepo
              .addGroupWithRoles(groupInfo)
              .map(groupInfo => success(groupInfo))
          }
       }.recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def updateGroupInfo = Action.async(parse.json) { req =>
    req.body
      .validate[GroupInfo]
      .map { groupInfo =>
        groupsRepo.updateGroupInfo(groupInfo)
          .map { res =>
            Ok
          }
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def groupInfoByName(groupName: String) = Action.async { request =>
    groupsRepo
      .getGroupByName(groupName)
      .map { group =>
        success(group)
      }
      .recoverWith(apiError)
  }
  /*
  groupIds: comma separated groupIds
   */
  def getRoles()=Action.async {req=>
    val groupIdsStr = req.getQueryString("groupIds")
    groupIdsStr match {
      case None=> Future.successful(BadRequest("groupIds not given"))
      case Some(groupIdStr)=>{
        val groupIds=groupIdsStr.get.split(",").map(group=>group.toLong)
        groupsRepo.getRolesForGroups(groupIds).map{roles=>
          success(roles)
        }.recoverWith(apiError)
      }
    }
  }
}
