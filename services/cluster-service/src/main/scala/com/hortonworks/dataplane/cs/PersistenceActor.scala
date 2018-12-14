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

package com.hortonworks.dataplane.cs

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.hortonworks.dataplane.commons.domain.Entities.{
  Cluster,
  ClusterHost,
  ClusterServiceHost,
  Errors,
  ClusterService => ClusterData
}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

private[dataplane] case class PersistAtlas(cluster: Cluster,
                                           atlas: Either[Throwable, Atlas])
private[dataplane] case class PersistKnox(cluster: Cluster,
                                          knox: Either[Throwable, KnoxInfo])
private[dataplane] case class PersistBeacon(
    cluster: Cluster,
    knox: Either[Throwable, BeaconInfo])

private[dataplane] case class PersistHdfs(cluster: Cluster,
                                          knox: Either[Throwable, Hdfs])
private[dataplane] case class PersistHive(cluster: Cluster,
                                          knox: Either[Throwable, HiveServer])

private[dataplane] case class PersistNameNode(
    cluster: Cluster,
    knox: Either[Throwable, NameNode])

private[dataplane] case class PersistHostInfo(
    cluster: Cluster,
    hostInfo: Either[Throwable, Seq[HostInformation]])

private sealed case class ServiceExists(cluster: Cluster,
                                        clusterData: ClusterData,
                                        endpoints: Seq[ClusterServiceHost],
                                        boolean: Boolean)

private sealed case class PersistenceResult(option: Option[ClusterData],
                                            cluster: Cluster)
private sealed case class UpdateResult(boolean: Boolean,
                                       clusterData: ClusterData,
                                       cluster: Cluster)
private sealed case class HostsUpdated(errors: Errors, cluster: Cluster)

class PersistenceActor(clusterInterface: StorageInterface)
    extends Actor
    with ActorLogging {

  override def receive = {
    case PersistAtlas(cluster, atlas) =>
      if (atlas.isRight) {
        val at = atlas.right.get
        val props = Try(Some(Json.parse(at.properties))).getOrElse(None)
        val toPersist = ClusterData(
          serviceName = "ATLAS",
          properties = props,
          clusterId = Some(cluster.id.get)
        )

        clusterInterface
          .serviceRegistered(cluster, toPersist.serviceName)
          .map(ServiceExists(cluster, toPersist, Seq(), _))
          .pipeTo(self)

      } else
        log.error(s"Error saving atlas info, Atlas data was not returned",
                  atlas.left.get)

    case PersistBeacon(cluster, beacon) =>
      if (beacon.isRight) {
        val beaconInfo = beacon.right.get
        val props = Try(beaconInfo.properties).getOrElse(None)
        val toPersist = ClusterData(
          serviceName = "BEACON",
          properties = props,
          clusterId = Some(cluster.id.get)
        )

        clusterInterface
          .serviceRegistered(cluster, toPersist.serviceName)
          .map(
            ServiceExists(cluster,
                          toPersist,
                          beaconInfo.endpoints.map(e =>
                            ClusterServiceHost(host = e.host)),
                          _))
          .pipeTo(self)

      } else
        log.error(s"Error saving Beacon info, Beacon data was not returned",
                  beacon.left.get)

    case PersistHdfs(cluster, hdfs) =>
      if (hdfs.isRight) {
        val hdfsInfo = hdfs.right.get
        val props = Try(hdfsInfo.props).getOrElse(None)
        val toPersist = ClusterData(
          serviceName = "HDFS",
          properties = props,
          clusterId = Some(cluster.id.get)
        )

        clusterInterface
          .serviceRegistered(cluster, toPersist.serviceName)
          .map(ServiceExists(cluster, toPersist, Seq(), _))
          .pipeTo(self)

      } else
        log.error(s"Error saving HDFS info, HDFS data was not returned",
                  hdfs.left.get)

    case PersistHive(cluster, hive) =>
      if (hive.isRight) {
        val hiveInfo = hive.right.get
        val props = Try(hiveInfo.props).getOrElse(None)
        val toPersist = ClusterData(
          serviceName = "HIVE",
          properties = props,
          clusterId = Some(cluster.id.get)
        )

        clusterInterface
          .serviceRegistered(cluster, toPersist.serviceName)
          .map(
            ServiceExists(cluster,
                          toPersist,
                          hiveInfo.serviceHost.map(e =>
                            ClusterServiceHost(host = e.host)),
                          _))
          .pipeTo(self)

      } else
        log.error(s"Error saving HIVE info, HIVE data was not returned",
                  hive.left.get)

    case PersistKnox(cluster, knox) =>
      if (knox.isRight) {
        val at = knox.right.get
        val props = at.properties
        val toPersist = ClusterData(serviceName = "KNOX",
                                    properties = props,
                                    clusterId = Some(cluster.id.get))
        clusterInterface
          .serviceRegistered(cluster, toPersist.serviceName)
          .map(ServiceExists(cluster, toPersist, Seq(), _))
          .pipeTo(self)
      } else
        log.error(s"Error saving KNOX info, KNOX data was not returned",
                  knox.left.get)

    case PersistNameNode(cluster, namenode) =>
      if (namenode.isRight) {
        val nn = namenode.right.get
        val toPersist = ClusterData(serviceName = "NAMENODE",
                                    properties = nn.props,
                                    clusterId = Some(cluster.id.get))

        clusterInterface
          .serviceRegistered(cluster, toPersist.serviceName)
          .map(
            ServiceExists(cluster,
                          toPersist,
                          nn.serviceHost.map(e =>
                            ClusterServiceHost(host = e.host)),
                          _))
          .pipeTo(self)

      } else
        log.error(
          s"Error saving NAMENODE info, NAMENODE data was not returned",
          namenode.left.get)

    case PersistHostInfo(cluster, hostInfo) =>
      if (hostInfo.isRight) {
        val hostInfos = hostInfo.right.get.map(
          hi =>
            ClusterHost(
              host = hi.name,
              ipaddr = hi.ip,
              status = hi.hostStatus,
              properties = hi.properties,
              clusterId = cluster.id.get
          ))

        clusterInterface
          .addOrUpdateHostInformation(hostInfos)
          .map(HostsUpdated(_, cluster))
          .pipeTo(self)

      } else
        log.error(s"Error saving Host info, Host data was not returned, error",
                  hostInfo.left.get)

    case ServiceExists(cluster, toPersist, endpoints, exists) =>
      if (exists) {
        log.info("Service exists, updating info")
        clusterInterface
          .updateServiceByName(toPersist, endpoints)
          .map(UpdateResult(_, toPersist, cluster))
          .pipeTo(self)
      } else {
        log.info("Inserting service information")
        clusterInterface
          .addService(toPersist, endpoints)
          .map(PersistenceResult(_, cluster))
          .pipeTo(self)
      }
    case PersistenceResult(data, cluster) =>
      if (data.isDefined) {
        context.parent ! ServiceSaved(data.get, cluster)
      }
      log.info(s"Added cluster service information - $data")

    case UpdateResult(data, service, cluster) =>
      if (data) {
        log.info(s"Updated cluster service info -  ${data}")
        context.parent ! ServiceSaved(service, cluster)
      }

    case Failure(e) =>
      e.printStackTrace()
      log.error(s"Persistence Error $e", e)

    case HostsUpdated(errors, cluster) =>
      if (errors.errors.isEmpty) {
        context.parent ! HostInfoSaved(cluster)
      } else
        log.error(s"Error updating cluster info $errors")

  }

}
