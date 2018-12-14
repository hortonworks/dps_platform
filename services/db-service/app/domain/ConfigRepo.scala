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

import javax.inject.{Inject, Singleton}

import com.hortonworks.dataplane.commons.domain.Entities.{DpConfig}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfigRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Configs = TableQuery[Configtable]

  def insert(dpConfig: DpConfig): Future[DpConfig] = {
    db.run {
      Configs returning Configs += dpConfig
    }
  }

  def findByKey(key: String): Future[Option[DpConfig]] = {
    db.run(Configs.filter(_.configKey === key).result.headOption)
  }
  def update(dpConfig: DpConfig):Future[Int]={
    db.run (
      Configs.filter(_.configKey===dpConfig.configKey)
        .map(r=>(r.configValue,r.active,r.export))
        .update(dpConfig.configValue,dpConfig.active,dpConfig.export)
    )
  }


  final class Configtable(tag: Tag) extends Table[DpConfig](tag, Some("dataplane"), "configs") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def configKey = column[String]("config_key")

    def configValue = column[String]("config_value")

    def active = column[Option[Boolean]]("active")

    def export = column[Option[Boolean]]("export")

    def * = (id, configKey, configValue, active, export) <> ((DpConfig.apply _).tupled, DpConfig.unapply)
  }

}
