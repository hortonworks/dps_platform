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

import com.hortonworks.dataplane.commons.domain.Entities.{ClusterService, ClusterServiceHost}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ClusterServiceHostsRepo @Inject()(
    protected val dbConfigProvider: DatabaseConfigProvider,
    private val csr: ClusterServiceRepo)
    extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Hosts = TableQuery[ClusterServiceHostsTable]

  def allByClusterAndService(
      clusterId: Long,
      serviceId: Long): Future[List[ClusterServiceHost]] = {
    val query = for {
      services <- csr.Services if services.clusterid === clusterId
      endpoints <- Hosts if endpoints.serviceid === serviceId
      if endpoints.serviceid === services.id
    } yield (endpoints)

    db.run(query.to[List].result)
  }

  def allByServiceName(serviceName: String): Future[List[(ClusterService, ClusterServiceHost)]] = {
    val query = for {
      (service, endpoint) <- csr.Services.filter(_.servicename === serviceName) join Hosts on(_.id === _.serviceid)
    } yield (service, endpoint)
    db.run(query.to[List].result)
  }

  def allByService(serviceId: Long): Future[Option[(ClusterService, ClusterServiceHost)]] = {
    val query = for {
      (service, endpoint) <- csr.Services.filter(_.id === serviceId) join Hosts on(_.id === _.serviceid)
    } yield (service, endpoint)
    db.run(query.to[List].result.headOption)
  }

  def insert(endPoint: ClusterServiceHost): Future[ClusterServiceHost] = {
    db.run {
      Hosts returning Hosts += endPoint
    }
  }

  def updateOrInsert(host: ClusterServiceHost): Future[Boolean] = {
    db.run(
        Hosts
          .filter(r => r.serviceid === host.serviceid)
          .map(o => o.host)
          .update(host.host))
      .map {
        case 0 =>
          db.run(Hosts += host)
          true
        case _ => true
      }
  }

  def findById(hostId: Long): Future[Option[ClusterServiceHost]] = {
    db.run(Hosts.filter(_.id === hostId).result.headOption)
  }

  def deleteById(hostId: Long): Future[Int] = {
    db.run(Hosts.filter(_.id === hostId).delete)
  }

  final class ClusterServiceHostsTable(tag: Tag)
      extends Table[ClusterServiceHost](tag,
                                        Some("dataplane"),
                                        "cluster_service_hosts") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def host = column[String]("host")

    def serviceid = column[Option[Long]]("service_id")

    def * =
      (id, host, serviceid) <> ((ClusterServiceHost.apply _).tupled, ClusterServiceHost.unapply)

  }

}
