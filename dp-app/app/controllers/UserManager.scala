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

import java.util.concurrent.atomic.AtomicInteger

import com.google.inject.Inject
import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.commons.domain.Ldap.LdapSearchResult.ldapConfigInfoFormat
import com.hortonworks.dataplane.commons.domain.Entities
import com.hortonworks.dataplane.db.Webservice.{GroupService, UserService}
import models.{JsonResponses, UserListInput, UsersAndRolesListInput}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import services.{LdapService, PluginManifestService}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Left

class UserManager @Inject()(val ldapService: LdapService,
                            @Named("userService") val userService: UserService,
                            @Named("groupService")val groupService:GroupService,
                            @Named("pluginManifestService") val pluginService: PluginManifestService)
    extends Controller {

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

  def ldapSearch = AuthenticatedAction.async { request =>
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

  def addUsersWithRoles=AuthenticatedAction.async(parse.json) { req =>
    req.body.validate[UsersAndRolesListInput].map{usersAndRolesInput=>
      val roleTypes=usersAndRolesInput.roles
      addUserInternal(usersAndRolesInput.users,roleTypes)
    }.getOrElse{
      Future.successful(BadRequest)
    }
  }

  def addSuperAdminUsers = AuthenticatedAction.async(parse.json) { req =>
    req.body
      .validate[UserListInput]
      .map { userList =>
        addUserInternal(userList.users,Seq("SUPERADMIN"))
      }
      .getOrElse(Future.successful(BadRequest))
  }
  private  def addUserInternal(userList:Seq[String],roles:Seq[String])={
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
  def listUsers = AuthenticatedAction.async { req =>
    userService.getUsers().map {
      case Left(errors) => handleErrors(errors)
      case Right(users) => Ok(Json.toJson(users))
    }
  }

  def listUsersWithRoles(offset: Option[String], pageSize: Option[String], searchTerm: Option[String]) = AuthenticatedAction.async { request =>
    userService.getUsersWithRoles(offset, pageSize, searchTerm)
      .flatMap {
        case Left(errors) => Future.failed(WrappedErrorException(errors.errors.head))
        case Right(users) => Future.successful(users)
      }
      .map { response => Ok(Json.toJson(response)) }
      .recoverWith {
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
  }

  def getUserDetail = AuthenticatedAction.async { req =>
    val userNameOpt: Option[String] = req.getQueryString("userName")
    userNameOpt
      .map { userName =>
        userService.getUserDetail(userName).map{
          case Left(errors) => handleErrors(errors)
          case Right(userInfo) => Ok(Json.toJson(userInfo))
        }
      }.getOrElse(Future.successful(BadRequest))

  }
  def adminUpdateUserRolesAndStatus = AuthenticatedAction.async(parse.json) { req =>
    req.body
      .validate[UserInfo]
      .map { userInfo =>
        userService.updateActiveAndRoles(userInfo).map {
          case Left(errors) => handleErrors(errors)
          case Right(_) if(userInfo.active.getOrElse(true)) => Ok(Json.toJson("success"))
          case Right(_) => Ok(Json.toJson("success")).withHeaders(("X-Mark-User-For-Token-Refresh", userInfo.userName))
        }
      }
      .getOrElse(Future.successful(BadRequest))
  }
  def getAllRoles = AuthenticatedAction.async {

    val created: AtomicInteger = new AtomicInteger(0)

    Future.sequence(
      pluginService.list()
        .flatMap(_.require_platform_roles)
        .distinct
        .map { cRole =>
          userService.addRole(Role(roleName = cRole))
            .map(_ => created.incrementAndGet())
            .recover { case _ => created.get() }
        }
    )
      .flatMap { _ =>
        Logger.info(s"Created ${created.get} new roles.")

        userService.getRoles()
      }
      .flatMap {
        case Left(errors) => Future.failed(WrappedErrorException(errors.errors.head))
        case Right(users) => Future.successful(users)
      }
      .map { response => Ok(Json.toJson(response)) }
      .recoverWith {
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
  }

  def getUserGroupsFromLdap()=AuthenticatedAction.async { req =>
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
  def createUserFromLdapGroupsConfiguration()= Action.async { req =>
    val userNameOpt: Option[String] = req.getQueryString("userName")
    if (userNameOpt.isEmpty) {
      Logger.error("userName is not specified")
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
      Logger.error("userName is not specified")
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
