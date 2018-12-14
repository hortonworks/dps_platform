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

import com.google.inject.{Inject, Singleton}
import com.hortonworks.dataplane.commons.domain.Entities.LdapConfiguration
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class LdapConfigRepo @Inject()(
                                protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val LdapConfigs = TableQuery[LdapConfigTable]

  def all(): Future[List[LdapConfiguration]] = db.run {
    LdapConfigs.to[List].result
  }

  def insert(ldapConfig: LdapConfiguration)(implicit ec: ExecutionContext): Future[LdapConfiguration] = db.run {
    (LdapConfigs returning LdapConfigs) += ldapConfig
  }

  def update(ldapConfig: LdapConfiguration)(implicit ec: ExecutionContext): Future[Boolean]={
    db.run(LdapConfigs.filter(_.id === ldapConfig.id).result).flatMap{curentConfig=>
      val updatedConfig=curentConfig.head.copy(ldapUrl = ldapConfig.ldapUrl)
           .copy(bindDn = ldapConfig.bindDn)
           .copy(userSearchBase = ldapConfig.userSearchBase)
           .copy(userSearchAttributeName = ldapConfig.userSearchAttributeName)
           .copy(userObjectClass = ldapConfig.userObjectClass)
           .copy(groupSearchBase = ldapConfig.groupSearchBase)
           .copy(groupSearchAttributeName = ldapConfig.groupSearchAttributeName)
           .copy(groupObjectClass = ldapConfig.groupObjectClass)
           .copy(groupMemberAttributeName = ldapConfig.groupMemberAttributeName)
           .copy(useAnonymous = ldapConfig.useAnonymous)
           .copy(referral = ldapConfig.referral)
      db.run(LdapConfigs.update(updatedConfig)).map{resp=>
        resp>0
      }
    }
  }

  final class LdapConfigTable(tag: Tag) extends Table[LdapConfiguration](tag, Some("dataplane"), "ldap_configs") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def ldapUrl = column[Option[String]]("url")

    def bindDn = column[Option[String]]("bind_dn")

    def userSearchBase = column[Option[String]]("user_searchbase")

    def userSearchAttributeName = column[Option[String]]("usersearch_attributename")

    def userObjectClass = column[Option[String]]("user_object_class")

    def groupSearchBase = column[Option[String]]("group_searchbase")

    def groupSearchAttributeName = column[Option[String]]("groupsearch_attributename")

    def groupObjectClass = column[Option[String]]("group_objectclass")

    def groupMemberAttributeName = column[Option[String]]("groupmember_attributename")

    def useAnonymous = column[Option[Boolean]]("use_anonymous")

    def referral = column[Option[String]]("referral")

    def * = (id, ldapUrl, bindDn, userSearchBase, userSearchAttributeName, userObjectClass, groupSearchBase, groupSearchAttributeName, groupObjectClass, groupMemberAttributeName, useAnonymous, referral) <> ((LdapConfiguration.apply _).tupled, LdapConfiguration.unapply)

    /* def * = (id,url, config) <> ((LdapConfiguration.apply _).tupled, LdapConfiguration.unapply)*/

  }

}
