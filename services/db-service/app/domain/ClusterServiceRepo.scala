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

import javax.inject._

import com.hortonworks.dataplane.commons.domain.Entities.ClusterService
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ClusterServiceRepo @Inject()(
    protected val dbConfigProvider: DatabaseConfigProvider,private val clusterRepo: ClusterRepo)
    extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Services = TableQuery[ClusterServiceTable]

  def all(): Future[List[ClusterService]] = db.run {
    Services.to[List].result
  }

  def allWithCluster(clusterId: Long) = {
    db.run(Services.filter(_.clusterid === clusterId).to[List].result)
  }

  def allWithDpCluster(dpClusterId: Long) = {

    val query = for {
      clusters <- clusterRepo.Clusters if clusters.dpClusterid === dpClusterId
      services <- Services if services.clusterid === clusters.id
    } yield services

    db.run(query.to[List].result)
  }

  def findByNameAndCluster(serviceName: String, clusterId: Long) = {
    db.run(
      Services
        .filter(_.servicename === serviceName)
        .filter(_.clusterid === clusterId)
        .result
        .headOption)
  }



  def findByIdAndCluster(serviceId: Long, clusterId: Long) = {
    db.run(
      Services
        .filter(_.id === serviceId)
        .filter(_.clusterid === clusterId)
        .result
        .headOption)
  }

  def insert(cluster: ClusterService): Future[ClusterService] = {
    db.run {
      Services returning Services += cluster
    }
  }

  def updateByName(cs: ClusterService): Future[Int] =
    db.run(
        Services
          .filter(_.servicename === cs.servicename)
          .filter(_.clusterid === cs.clusterId)
          .map(r => r.properties)
          .update(cs.properties))
      .map(r => r)

  def findById(clusterId: Long): Future[Option[ClusterService]] =
    db.run(Services.filter(_.id === clusterId).result.headOption)

  def deleteById(clusterId: Long): Future[Int] = {
    db.run(Services.filter(_.id === clusterId).delete)
  }

  final class ClusterServiceTable(tag: Tag)
      extends Table[ClusterService](tag,
                                    Some("dataplane"),
                                    "cluster_services") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def servicename = column[String]("service_name")

    def clusterid = column[Option[Long]]("cluster_id")

    def properties = column[Option[JsValue]]("properties")

    def * =
      (id, servicename, properties, clusterid) <> ((ClusterService.apply _).tupled, ClusterService.unapply)

  }

}
