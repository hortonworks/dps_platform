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

package com.hortonworks.dataplane.db

import javax.inject.Singleton

import com.hortonworks.dataplane.commons.domain.Entities.{UserInfo, _}
import com.hortonworks.dataplane.db.Webservice.UserService
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UserServiceImpl(config: Config)(implicit ws: WSClient)
    extends UserService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  override def loadUser(username: String): Future[Either[Errors, User]] = {
    ws.url(s"$url/users?username=$username")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        mapToUser(res)
      }
  }

  override def loadUserById(id: String): Future[Either[Errors, User]] = {
    ws.url(s"$url/users/$id")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        mapToOneUser(res)
      }
  }
  private def mapToUserInfo(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[UserInfo].get)
      case 404 => createEmptyErrorResponse
      case _ => mapErrors(res)
    }
  }
  private def mapToSuccess(res: WSResponse) = {
    res.status match {
      case 200 =>{ Right((res.json \ "results"));Right(true
      )}
      case _ => mapErrors(res)
    }
  }

  private def mapToOneUser(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[User].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToUser(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").head.validate[User].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToUsers(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[Seq[User]].get)
      case _ => mapErrors(res)
    }
  }
  private def mapToUsersWithRoles(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[UsersList].get)
      case _ => mapErrors(res)
    }
  }

  def mapToRole(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[Role].get)
      case _ => mapErrors(res)
    }
  }
  def mapToRoles(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[Seq[Role]].get)
      case _ => mapErrors(res)
    }
  }
  private def mapToUserRoles(res: WSResponse) = {
    res.status match {
      case 200 =>Right((res.json \ "results").validate[UserRoles].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToUserRole(res: WSResponse) = {
    res.status match {
      case 200 =>Right((res.json \ "results").validate[UserRole].get)
      case _ => mapErrors(res)
    }
  }
  private  def mapToUserGroupInfo(res:WSResponse)={
    res.status match {
      case 200 => Right((res.json \ "results").validate[UserGroupInfo].get)
      case _ => mapErrors(res)
    }
  }
  private def mapToUserContext(res: WSResponse) = {
    res.status match {
      case 200 =>Right((res.json \ "results").validate[UserContext].get)
      case _ => mapErrors(res)
    }
  }
  private def mapAddUserWithRolesResponse(res:WSResponse)={
    res.status match {
      case 200 =>Right((res.json \ "results").validate[UserInfo].get)
      case _ => mapErrors(res)
    }
  }

  override def getUserRoles(userName: String): Future[Either[Errors, UserRoles]] = {
    ws.url(s"$url/users/role/$userName")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        mapToUserRoles(res)
      }
  }



  override def addUser(user: User): Future[Either[Errors, User]] = {
    ws.url(s"$url/users")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(user))
      .map(mapToUser)
  }

  override def updateUser(user: User): Future[Either[Errors, User]] = {
    ws.url(s"$url/users/${user.id}")
      .withHeaders("Accept" -> "application/json")
      .put(Json.toJson(user))
      .map(mapToOneUser)
  }

  override def addUserWithRoles(userInfo: UserInfo): Future[Either[Errors, UserInfo]] = {
    ws.url(s"$url/users/withroles")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(userInfo))
        .map(mapAddUserWithRolesResponse)

  }

  override  def getUserDetail(userName:String): Future[Either[Errors,UserInfo]]={
    ws.url(s"$url/users/detail/$userName")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToUserInfo)
  }

  override def addRole(role: Role): Future[Either[Errors, Role]] = {
    ws.url(s"$url/roles")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(role))
      .map(mapToRole)
  }

  override def addUserRole(
      userRole: UserRole): Future[Either[Errors, UserRole]] = {
    ws.url(s"$url/users/role")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(userRole))
      .map(mapToUserRole(_))
  }
  override  def getUsers(): Future[Either[Errors,Seq[User]]]={
    ws.url(s"$url/users")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map{res=>
        mapToUsers(res)
      }
  }

  override  def getUsersWithRoles(offset: Option[String], pageSize: Option[String], searchTerm: Option[String]): Future[Either[Errors,UsersList]]={
    ws.url(s"$url/users/all")
      .withQueryString("offset" -> offset.getOrElse("0"), "pageSize" -> pageSize.getOrElse("10"), "searchTerm" -> searchTerm.getOrElse("") )
      .withHeaders("Accept" -> "application/json")
      .get()
      .map{res=>
        mapToUsersWithRoles(res)
      }
  }


  override  def getRoles():  Future[Either[Errors,Seq[Role]]]={
    ws.url(s"$url/roles")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { mapToRoles(_)
      }
  }

  override def updateActiveAndRoles(userInfo: UserInfo): Future[Either[Errors,Boolean]]= {
    ws.url(s"$url/users/updateActiveAndRoles")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(userInfo))
      .map { res =>
        res.status match {
          case 200 => Right(true)
          case _ =>mapErrors(res)
        }
      }
  }

  override def addUserWithGroups(userGroupInfo: UserGroupInfo): Future[Either[Errors,UserGroupInfo]]={
    ws.url(s"$url/users/withgroups")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(userGroupInfo))
      .map(mapToUserGroupInfo)
  }


  override def updateUserWithGroups(userLdapGroups: UserLdapGroups): Future[Either[Errors,UserContext]]= {
    ws.url(s"$url/users/withgroups")
      .withHeaders("Accept" -> "application/json")
      .put(Json.toJson(userLdapGroups))
      .map(mapToUserContext)
  }
  override def getUserContext(userName:String): Future[Either[Errors,UserContext]] ={
    ws.url(s"$url/users/$userName/usercontext")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToUserContext)
  }
}
