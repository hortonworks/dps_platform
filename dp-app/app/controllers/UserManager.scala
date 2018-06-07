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
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.commons.domain.Ldap.LdapSearchResult.ldapConfigInfoFormat
import com.hortonworks.dataplane.commons.domain.{Entities, RoleType}
import com.hortonworks.dataplane.db.Webservice.{GroupService, UserService}
import com.typesafe.scalalogging.Logger
import models.{JsonResponses, UserListInput, UsersAndRolesListInput}
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import services.LdapService

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Left

class UserManager @Inject()(val ldapService: LdapService,
                            @Named("userService") val userService: UserService,
                            @Named("groupService")val groupService:GroupService)
    extends Controller {
  val logger = Logger(classOf[UserManager])

  private def handleErrors(errors: Errors) = {
    if (errors.errors.exists(_.status == 400))
      BadRequest(Json.toJson(errors))
    else if (errors.errors.exists(_.status == 403))
      Forbidden(Json.toJson(errors))
    else if (errors.errors.exists(_.status == 404))
      NotFound(Json.toJson(errors))
    else if (errors.errors.exists(_.status == 409))
      Conflict(Json.toJson(errors))
    else
      InternalServerError(Json.toJson(errors))
  }

  def ldapSearch = Action.async { request =>
    val fuzzyMatch: Boolean = request.getQueryString("fuzzyMatch").exists {
      res =>
        res.toBoolean
    }
    val searchType=request.getQueryString("searchType")

    ldapService
      .search(request.getQueryString("name").get,searchType,fuzzyMatch)
      .map {
        case Left(errors) => handleErrors(errors)
        case Right(ldapSearchResult) =>
          Ok(Json.toJson(ldapSearchResult))
      }

  }

  def addUsersWithRoles=Action.async(parse.json) { req =>
    req.body.validate[UsersAndRolesListInput].map{usersAndRolesInput=>
      val roleTypes=usersAndRolesInput.roles.map{roleStr=>RoleType.withName(roleStr)}
      addUserInternal(usersAndRolesInput.users,roleTypes)
    }.getOrElse{
      Future.successful(BadRequest)
    }
  }

  def addSuperAdminUsers = Action.async(parse.json) { req =>
    req.body
      .validate[UserListInput]
      .map { userList =>
        addUserInternal(userList.users,Seq(RoleType.SUPERADMIN))
      }
      .getOrElse(Future.successful(BadRequest))
  }
  private  def addUserInternal(userList:Seq[String],roles:Seq[RoleType.Value])={
    val futures: Seq[Future[Either[Errors, UserInfo]]] = userList.map { userName =>
      val userInfo: UserInfo = UserInfo(userName = userName,
        displayName = userName,
        active =Some(true),
        roles = roles)

      ldapService.search(userInfo.userName, Option("user"), false)
        .flatMap {
          case Left(errors)=> Future.successful(Left(errors))
          case Right(ldapSearchResult) =>
            if(ldapSearchResult.nonEmpty){
              userService.addUserWithRoles(userInfo)
            }else {
              Future.successful(Left(Errors(Seq(Error(500,"Invalid User")))))
            }
        }
    }
    //TODO check of any alternate ways.since it is bulk the json may contain success as well as failures
    val successFullyAdded = mutable.ArrayBuffer.empty[UserInfo]
    val errorsReceived = mutable.ArrayBuffer.empty[Error]

    Future.sequence(futures).map { respList =>
      respList.foreach {
        case Left(error) => errorsReceived ++= error.errors
        case Right(userInfo) => successFullyAdded += userInfo
      }
      Ok(Json.toJson(
        Json.obj(
        "successfullyAdded" -> successFullyAdded,
        "errors" -> errorsReceived)))
    }
  }
  def listUsers = Action.async { req =>
    userService.getUsers().map {
      case Left(errors) => handleErrors(errors)
      case Right(users) => Ok(Json.toJson(users))
    }
  }

  def listUsersWithRoles = Action.async { request =>
    userService.getUsersWithRoles(request.getQueryString("offset"), request.getQueryString("pageSize"), request.getQueryString("searchTerm")).map {
      case Left(errors) => handleErrors(errors)
      case Right(users) => Ok(Json.toJson(users))
    }
  }

  def getUserDetail = Action.async { req =>
    val userNameOpt: Option[String] = req.getQueryString("userName")
    userNameOpt
      .map { userName =>
        userService.getUserDetail(userName).map{
          case Left(errors) => handleErrors(errors)
          case Right(userInfo) => Ok(Json.toJson(userInfo))
        }
      }.getOrElse(Future.successful(BadRequest))

  }
  def adminUpdateUserRolesAndStatus = Action.async(parse.json) { req =>
    req.body
      .validate[UserInfo]
      .map { userInfo =>
        userService.updateActiveAndRoles(userInfo).map {
          case Left(errors) => handleErrors(errors)
          case Right(updated) => Ok(Json.toJson("success"))
        }
      }
      .getOrElse(Future.successful(BadRequest))
  }
  def getAllRoles = Action.async { req =>
    userService.getRoles().map {
      case Left(errors) => handleErrors(errors)
      case Right(roles) => Ok(Json.toJson(roles))
    }
  }
  def getUserGroupsFromLdap()=Action.async { req =>
    val userNameOpt: Option[String] = req.getQueryString("userName")
    if (userNameOpt.isEmpty){
      Future.successful(BadRequest)
    }else{
      ldapService.getUserGroups(userNameOpt.get).map{
        case Left(errors) => handleErrors(errors)
        case Right(ldapUser) =>
          Ok(Json.toJson(ldapUser))
      }
    }
  }
  def createUserFromLdapGroupsConfiguration()=Action.async { req =>
    val userNameOpt: Option[String] = req.getQueryString("userName")
    if (userNameOpt.isEmpty) {
      logger.error("userName is not specified")
      Future.successful(BadRequest("userName not specified"))
    } else {
      createUserWithLdapGroups(userNameOpt.get).flatMap{
        case Left(errors)=>Future.successful(handleErrors(errors))
        case Right(userGroupInfo)=>{
          userService.getUserContext(userNameOpt.get).map{
            case Left(errors)=>handleErrors(errors)
            case Right(userContext)=>Ok(Json.toJson(userContext))
          }
        }
      }
    }
  }
  def resyncUserFromLdap()=Action.async { req =>
    val userNameOpt: Option[String] = req.getQueryString("userName")
    if (userNameOpt.isEmpty) {
      logger.error("userName is not specified")
      Future.successful(BadRequest("userName not specified"))
    } else {
      val userName=userNameOpt.get
      getMatchingGroupsFromLdapAndDb(userName).flatMap{
        case Left(errors)=>Future.successful(handleErrors(errors))
        case Right(userGroups)=>{
          val userLdapGroups=UserLdapGroups(userGroups.username ,ldapGroups = userGroups.groups.map(_.groupName))
          userService.updateUserWithGroups(userLdapGroups).map{
            case Left(errors)=>handleErrors(errors)
            case Right(userCtx)=>Ok(Json.toJson(userCtx))
          }
        }
      }
    }
  }

  private def createUserWithLdapGroups(userName: String):Future[Either[Errors,UserGroupInfo]] = {
    getMatchingGroupsFromLdapAndDb(userName).flatMap{
      case Left(errors)=>Future.successful(Left(errors))
      case Right(userGroups)=>{
        if (userGroups.groups.length<1){
          Future.successful(Left(Errors(Seq(Error(403,"NO_ALLOWED_GROUPS:The user doesnt have valid groups configured")))))
        }else{
          val groupIds=userGroups.groups.map(grp=>grp.id.get)
          val userGroupInfo=UserGroupInfo(id=None,userName=userGroups.username,displayName=userGroups.username,groupIds = groupIds )
          userService.addUserWithGroups(userGroupInfo).map {
            case Left(errors)=>Left(errors)
            case Right(userGroupInfo)=>Right(userGroupInfo)
          }
        }
      }
    }
  }

  private def getMatchingGroupsFromLdapAndDb(userName: String):Future[Either[Errors, Entities.UserGroups]] = {
    for {
      ldapUser <- ldapService.getUserGroups(userName)
      dbGroups <- groupService.getAllActiveGroups()
    } yield {
      ldapUser match {
        case Left(errors) => Left(errors)
        case Right(ldpUsr) => {
          val ldapGroupNames: Seq[String] = ldpUsr.groups.map(res => res.name)
          dbGroups match {
            case Left(errors) => Left(errors)
            case Right(dbGrps) => {
              val filteredGroups = dbGrps.filter(res => ldapGroupNames.contains(res.groupName))
              Right(UserGroups(ldpUsr.name, filteredGroups))
            }
          }
        }
      }
    }
  }
}
