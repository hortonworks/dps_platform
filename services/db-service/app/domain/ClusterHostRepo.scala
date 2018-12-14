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

import com.hortonworks.dataplane.commons.domain.Entities.ClusterHost
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ClusterHostRepo @Inject()(
    protected val dbConfigProvider: DatabaseConfigProvider)
    extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val ClusterHosts = TableQuery[ClusterHostTable]

  def allWithCluster(clusterId: Long): Future[List[ClusterHost]] = db.run {
    ClusterHosts.filter(_.clusterId === clusterId).to[List].result
  }

  def insert(clusterHost: ClusterHost): Future[ClusterHost] = {
    db.run {
      ClusterHosts returning ClusterHosts += clusterHost
    }
  }

  def upsert(clusterHost: ClusterHost): Future[Int] = {

    db.run(
        ClusterHosts
          .filter(_.clusterId === clusterHost.clusterId)
          .filter(_.host === clusterHost.host)
          .map(r => (r.status, r.properties))
          .update(clusterHost.status, clusterHost.properties))
      .map {
        case 0 =>
          db.run(ClusterHosts += clusterHost)
          1
        case 1 => 1
        case _ => throw new Exception("Too many rows updated")
      }
      .recoverWith {
        case e: Exception =>
          Logger.error("Could not insert host info")
          Future.successful(0)
      }
  }

  def findByHostAndCluster(clusterId:Long,hostName:String) = {
    db.run(
      ClusterHosts
        .filter(c => c.clusterId === clusterId && c.host === hostName)
        .result
        .headOption)
  }

  def findByClusterAndHostId(clusterId: Long,
                             hostId: Long): Future[Option[ClusterHost]] = {
    db.run(
      ClusterHosts
        .filter(c => c.clusterId === clusterId && c.id === hostId)
        .result
        .headOption)
  }

  def deleteById(clusterId: Long, id: Long): Future[Int] = {
    db.run(
      ClusterHosts
        .filter(c => c.clusterId === clusterId && c.id === id)
        .delete)
  }

  final class ClusterHostTable(tag: Tag)
      extends Table[ClusterHost](tag, Some("dataplane"), "cluster_hosts") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def host = column[String]("host")

    def ipaddr = column[String]("ipaddr")

    def status = column[String]("status")

    def properties = column[Option[JsValue]]("properties")

    def clusterId = column[Long]("cluster_id")

    def * =
      (id, host,ipaddr,status, properties, clusterId) <> ((ClusterHost.apply _).tupled, ClusterHost.unapply)
  }

}
