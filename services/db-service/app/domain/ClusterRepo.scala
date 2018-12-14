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

import com.hortonworks.dataplane.commons.domain.Entities.Cluster
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue

import scala.concurrent.Future

@Singleton
class ClusterRepo @Inject()(
    protected val dbConfigProvider: DatabaseConfigProvider)
    extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Clusters = TableQuery[ClustersTable]

  def all(dpClusterId: Option[Long]): Future[List[Cluster]] = db.run {
    dpClusterId match {
      case Some(dpClusterId) =>
        Clusters.filter(_.dpClusterid === dpClusterId).to[List].result
      case None => Clusters.to[List].result
    }
  }

  def insert(cluster: Cluster): Future[Cluster] = {
    val security = if (cluster.secured.isEmpty) {
      Some(false)
    } else cluster.secured
    cluster.copy(secured = security)
    db.run {
      Clusters returning Clusters += cluster
    }
  }

  def findById(clusterId: Long): Future[Option[Cluster]] = {
    db.run(Clusters.filter(_.id === clusterId).result.headOption)
  }

  def findByDpClusterId(dpClusterId: Long): Future[List[Cluster]] = {

    db.run(Clusters.filter(_.dpClusterid === dpClusterId).to[List].result)
  }

  def deleteById(clusterId: Long): Future[Int] = {
    db.run(Clusters.filter(_.id === clusterId).delete)
  }

  final class ClustersTable(tag: Tag)
      extends Table[Cluster](tag, Some("dataplane"), "discovered_clusters") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def ambariurl = column[Option[String]]("cluster_url")

    def secured = column[Option[Boolean]]("secured")

    def kerberosuser = column[Option[String]]("kerberos_user")

    def kerberosticketLocation =
      column[Option[String]]("kerberos_ticket_location")

    def dpClusterid = column[Option[Long]]("dp_clusterid")

    def userid = column[Option[Long]]("user_id")

    def properties = column[Option[JsValue]]("properties")

    def version = column[Option[String]]("version") // stack display name from ambari

    def * =
      (id,
       name,
       ambariurl,
       secured,
       kerberosuser,
       kerberosticketLocation,
       dpClusterid,
       userid,
       properties,
       version) <> ((Cluster.apply _).tupled, Cluster.unapply)

  }

}
