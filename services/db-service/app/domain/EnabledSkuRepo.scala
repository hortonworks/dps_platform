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

import com.hortonworks.dataplane.commons.domain.Entities.EnabledSku
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnabledSkuRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider,skuRepo: SkuRepo)
  extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val EnabledSkus = TableQuery[EnabledSkusTable]

  def all(): Future[List[EnabledSku]] = db.run {
    EnabledSkus.to[List].result
  }
  def getEnabledSkus():Future[Seq[String]]={//TODO caching for a perios of time
    val query=for{
      skus<-skuRepo.Skus if skus.status===1.toShort
      enabledSkus<-EnabledSkus if enabledSkus.skuId===skus.id
    }yield {
      (skus,enabledSkus)
    }
    db.run(query.result).map{res=>
      res.map{r=>r._1.name}
    }
  }
  def insert(skuId: Long, enabledBy: Long, smartSenseId: String, subscriptionId: String): Future[EnabledSku] = {
    val enabledSku = EnabledSku(skuId = skuId, enabledBy = enabledBy,
      smartSenseId = smartSenseId, subscriptionId = subscriptionId)
    db.run {
      EnabledSkus returning EnabledSkus += enabledSku
    }
  }

  def findById(skuId: Long):Future[Option[EnabledSku]] = {
    db.run(EnabledSkus.filter(_.skuId === skuId).result.headOption)
  }

  def deleteById(skuId: Long): Future[Int] = {
    db.run(EnabledSkus.filter(_.skuId === skuId).delete)
  }

  final class EnabledSkusTable(tag: Tag) extends Table[(EnabledSku)](tag, Some("dataplane"), "enabled_skus") {
    def skuId = column[Long]("sku_id")

    def enabledBy = column[Long]("enabled_by")
    def enabledOn = column[Option[LocalDateTime]]("enabled_on")

    def smartSenseId = column[String]("smartsense_id")
    def subscriptionId = column[String]("subscription_id")

    def created = column[Option[LocalDateTime]]("created")
    def updated = column[Option[LocalDateTime]]("updated")

    def * = (skuId, enabledBy, enabledOn, smartSenseId, subscriptionId, created, updated) <>
      ((EnabledSku.apply _).tupled, EnabledSku.unapply)
  }


}