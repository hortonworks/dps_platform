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

import com.hortonworks.dataplane.commons.domain.Entities.{ClusterSku, Status}
import domain.API.{EntityNotFound, UpdateError}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.{ExecutionContext, Future}

class ClusterSkuRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit exec: ExecutionContext) extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val ClusterSkus: TableQuery[ClusterSkusTable] = TableQuery[ClusterSkusTable]

  def allBySkuId(skuId: Long): Future[List[ClusterSku]] = db.run {
    ClusterSkus.filter(_.skuId === skuId).to[List].result
  }

  def create(clusterSku: ClusterSku): Future[ClusterSku] = {
    val clusterSkuCopy = ClusterSku(dpClusterId = clusterSku.dpClusterId, skuId = clusterSku.skuId)
    db.run {
      ClusterSkus returning ClusterSkus += clusterSkuCopy
    }
  }

  def update(clusterSku: ClusterSku): Future[ClusterSku] = {
    db.run {
        ClusterSkus
          .filter(_.id === clusterSku.id)
          .update(clusterSku)
      }
      .map {
        case 0 => throw UpdateError()
        case _ => clusterSku
      }
  }

  def getBySkuIdAndDpClusterId(skuId: Long, dpClusterId: Long): Future[ClusterSku] = {
    db.run {
      ClusterSkus.filter(_.skuId === skuId).filter(_.dpClusterId === dpClusterId).result
    }.map{ result =>
      if(result.isEmpty){
        throw new EntityNotFound
      }else {
        result.head
      }
    }
  }

  def removeClusterSkuAssociation(skuId: Long, dpClusterId: Long): Future[Int] = db.run {
    ClusterSkus.filter(_.skuId === skuId).filter(_.dpClusterId === dpClusterId).delete
  }

  final class ClusterSkusTable(tag: Tag) extends Table[ClusterSku](tag, Some("dataplane"), "dp_cluster_sku") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def dpClusterId = column[Long]("dp_cluster_id")
    def skuId = column[Long]("sku_id")

    def created = column[Option[LocalDateTime]]("created")
    def updated = column[Option[LocalDateTime]]("updated")

    def * = (id, dpClusterId, skuId, created, updated) <> ((ClusterSku.apply _).tupled, ClusterSku.unapply)
  }
}
