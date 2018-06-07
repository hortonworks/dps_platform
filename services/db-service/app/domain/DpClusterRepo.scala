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

import com.hortonworks.dataplane.commons.domain.Entities.{
  DataplaneCluster,
  Location
}
import domain.API.UpdateError
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import slick.jdbc.GetResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DpClusterRepo @Inject()(
    protected val userRepo: UserRepo,
    protected val dbConfigProvider: DatabaseConfigProvider) {

  val dbConfig = dbConfigProvider.get[DpPgProfile]

  val db = dbConfig.db

  import dbConfig.profile.api._

  val Locations = TableQuery[LocationsTable]
  val DataplaneClusters = TableQuery[DpClustersTable]

  def update(dl: DataplaneCluster): Future[(DataplaneCluster, Boolean)] = {
    if (dl.id.isEmpty)
      insert(dl).map((_, true))
    else {
      findById(dl.id.get).flatMap { dpc =>
        if (dpc.isDefined) {
          // Found an entity, only update applicable fields and return
          db.run(
            DataplaneClusters.filter(_.id === dl.id)
              .map(r => (r.dcName, r.description, r.ambariUrl, r.locationId, r.name,r.properties,r.userId,r.updated, r.isDataLake))
              .update(dl.dcName, dl.description, dl.ambariUrl, dl.location, dl.name,dl.properties,dl.createdBy,Some(LocalDateTime.now()),dl.isDatalake)

          ).flatMap { v =>
            if (v > 0) findById(dl.id.get).map(r => (r.get,false))
            else Future.failed(UpdateError())
          }
        } else {
          insert(dl).map((_, true))
        }
      }
    }
  }

  def getLocation(id: Long): Future[Option[Location]] = {
    db.run(Locations.filter(_.id === id).result.headOption)
  }

  def deleteLocation(id: Long): Future[Int] = {
    val location = db.run(Locations.filter(_.id === id).delete)
    location
  }
  def all(): Future[List[DataplaneCluster]] = db.run {
    DataplaneClusters.to[List].result
  }

  def findById(dpClusterId: Long): Future[Option[DataplaneCluster]] = {
    db.run(DataplaneClusters.filter(_.id === dpClusterId).result.headOption)
  }

  def findByAmbariUrl(ambariUrl: String): Future[Option[DataplaneCluster]] = {
//  TODO: ambari ip address actually is the complete url, just with domain replaced with ip; replace it later to point to ambariUrl
    db.run(
      DataplaneClusters
        .filter(_.ambariIpAddress === ambariUrl)
        .result
        .headOption)
  }

  def insert(dpCluster: DataplaneCluster): Future[DataplaneCluster] = {
    def trimTrailingSlash = {
      if (dpCluster.ambariUrl.endsWith("/"))
        dpCluster.ambariUrl.stripSuffix("/")
      else dpCluster.ambariUrl
    }

    val dataplaneCluster = dpCluster.copy(
      isDatalake = dpCluster.isDatalake.map(Some(_)).getOrElse(Some(false)),
      ambariUrl = trimTrailingSlash,
      created = Some(LocalDateTime.now()),
      updated = Some(LocalDateTime.now())
    )
    db.run {
      DataplaneClusters returning DataplaneClusters += dataplaneCluster
    }
  }

  def addLocation(location: Location): Future[Location] = db.run {
    Locations returning Locations += location
  }

  def updateStatus(dpCluster: DataplaneCluster): Future[Int] = {
    db.run(
        DataplaneClusters
          .filter(_.id === dpCluster.id)
          .map(r => (r.state, r.updated))
          .update(dpCluster.state, Some(LocalDateTime.now())))
      .map(r => r)
  }

  def deleteCluster(clusterId: Long) =
    db.run {
      sql"""SELECT * FROM dataplane.dp_cluster_delete($clusterId)""".as[Int]
    }


  private def getLocationsByQuery(query: String): Future[List[Location]] = {
    implicit val getLocationResult = GetResult(
      r =>
        Location(r.nextLongOption,
                 r.nextString,
                 r.nextString,
                 r.nextString,
                 r.nextFloat,
                 r.nextFloat))
    db.run(
        sql"""select  l.id, l.city, l.province, l.country, l.latitude, l.longitude
            from dataplane.locations as l
            where
              lower(l.city) || ', ' || lower(l.country) like ${query.toLowerCase} || '%'
              or
              lower(l.city) || ', ' || lower(l.province) || ', ' || lower(l.country) like ${query.toLowerCase} || '%'
              or
              lower(l.province) || ', ' || lower(l.country) like ${query.toLowerCase} || '%'
              or
              lower(l.country) like ${query.toLowerCase} || '%'
            limit 20""".as[Location]
      )
      .map(v => v.toList)
  }

  private def getAllLocations(): Future[List[Location]] = db.run {
    Locations.to[List].result
  }

  def getLocations(query: Option[String]): Future[List[Location]] =
    query match {
      case Some(query) => getLocationsByQuery(query)
      case None        => getAllLocations()
    }

  final class LocationsTable(tag: Tag)
      extends Table[Location](tag, Some("dataplane"), "locations") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def city = column[String]("city")

    def province = column[String]("province")

    def country = column[String]("country")

    def latitude = column[Float]("latitude")

    def longitude = column[Float]("longitude")

    def * =
      (id, city, province, country, latitude, longitude) <> ((Location.apply _).tupled, Location.unapply)
  }

  final class DpClustersTable(tag: Tag)
      extends Table[DataplaneCluster](tag, Some("dataplane"), "dp_clusters") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def dcName = column[String]("dc_name")

    def description = column[String]("description")

    def ambariUrl = column[String]("ambari_url")

    def ambariIpAddress = column[String]("ip_address")

    def locationId = column[Option[Long]]("location_id")

    def userId = column[Option[Long]]("created_by")

    def created = column[Option[LocalDateTime]]("created")

    def updated = column[Option[LocalDateTime]]("updated")

    def properties = column[Option[JsValue]]("properties")

    def state = column[Option[String]]("state")

    def isDataLake = column[Option[Boolean]]("is_datalake")

    def knoxEnabled = column[Option[Boolean]]("knox_enabled")

    def allowUntrusted = column[Boolean]("allow_untrusted")

    def behindGateway = column[Boolean]("behind_gateway")

    def knoxUrl = column[Option[String]]("knox_url")

    def location = foreignKey("location", locationId, Locations)(_.id)

    def createdBy = foreignKey("user", userId, userRepo.Users)(_.id)

    def * =
      (id,
       name,
       dcName,
       description,
       ambariUrl,
       ambariIpAddress,
       locationId,
       userId,
       properties,
       state,
       isDataLake,
       knoxEnabled,
       allowUntrusted,
       behindGateway,
       knoxUrl,
       created,
       updated) <> ((DataplaneCluster.apply _).tupled, DataplaneCluster.unapply)
  }

}
