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
import com.hortonworks.dataplane.commons.domain.RoleType
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.db.Webservice.GroupService
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class GroupServiceImpl(config: Config)(implicit ws: WSClient) extends GroupService {
  import com.hortonworks.dataplane.commons.domain.JsonFormatters._
  def getGroups(offset: Option[String], pageSize: Option[String], searchTerm: Option[String]): Future[Either[Errors, GroupsList]] = {
    ws.url(s"$url/groups")
      .withQueryString("offset" -> offset.getOrElse("0"), "pageSize" -> pageSize.getOrElse("10"), "searchTerm" -> searchTerm.getOrElse(""))
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        mapToGroupInfos(res)
      }
  }
  def getAllActiveGroups() : Future[Either[Errors,Seq[Group]]]={
    ws.url(s"$url/groups/active")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(res=>
        mapToGroups(res))

 }
  private def mapToGroupInfos(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[GroupsList].get)
      case _ => mapErrors(res)
    }
  }
  private def mapToGroups(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[Seq[Group]].get)
      case _ => mapErrors(res)
    }
  }

  def addGroupWithRoles(groupInfo: GroupInfo): Future[Either[Errors, GroupInfo]] = {
    ws.url(s"$url/groups/withroles")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(groupInfo))
      .map { res =>
        mapToGroupInfo(res)
      }
  }

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  private def mapToGroupInfo(res: WSResponse) = {
    res.status match {
      case 200 => Right((res.json \ "results").validate[GroupInfo].get)
      case _ => mapErrors(res)
    }
  }

  def updateGroupInfo(groupInfo: GroupInfo): Future[Either[Errors, Boolean]] = {
    ws.url(s"$url/groups/update")
      .withHeaders("Accept" -> "application/json")
      .post(Json.toJson(groupInfo))
      .map { res =>
        res.status match {
          case 200 => Right(true)
          case _ => mapErrors(res)
        }
      }
  }

  def getGroupByName(groupName: String): Future[Either[Errors,GroupInfo]] = {
    ws.url(s"$url/groups/${groupName}")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        res.status match {
          case 200 => Right((res.json \ "results").validate[GroupInfo].get)
          case _ => mapErrors(res)
        }
      }
  }
  override def getRolesForGroups(groupIds:Seq[Long]): Future[Either[Errors,Seq[String]]]={
    val groupIdsStr=groupIds.mkString(",")
    ws.url(s"$url/groups/roles?groupIds=$groupIdsStr")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        res.status match {
          case 200 => Right((res.json \ "results").validate[Seq[String]].get)
          case _ => mapErrors(res)
        }
      }
  }
}
