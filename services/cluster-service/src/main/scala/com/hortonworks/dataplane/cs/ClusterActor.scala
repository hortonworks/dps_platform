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
import akka.actor.{Actor, ActorLogging, ActorRef}
import com.hortonworks.dataplane.commons.domain.Entities.{Cluster, DataplaneCluster, ClusterService => ClusterData}
import com.hortonworks.dataplane.commons.service.api.Poll
import com.typesafe.config.Config
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global

private[cs] sealed case class SaveAtlas(atlas: Either[Throwable, Atlas])
private[cs] sealed case class SaveNameNode(
    nameNode: Either[Throwable, NameNode])
private[cs] sealed case class SaveBeacon(beacon: Either[Throwable, BeaconInfo])
private[cs] sealed case class SaveHdfs(hdfs: Either[Throwable, Hdfs])
private[cs] sealed case class SaveHive(hive: Either[Throwable, HiveServer])
private[cs] sealed case class SaveKnox(knox: Either[Throwable, KnoxInfo])
private[cs] sealed case class SaveHostInfo(
    atlas: Either[Throwable, Seq[HostInformation]])
private[cs] sealed case class HandleError(exception: Exception)
private[cs] sealed case class ServiceSaved(clusterData: ClusterData,
                                           cluster: Cluster)
private[cs] sealed case class HostInfoSaved(cluster: Cluster)

class ClusterActor(cluster: Cluster,
                   dpCluster: DataplaneCluster,
                   wsClient: WSClient,
                   storageInterface: StorageInterface,
                   credentials: Credentials,
                   val dbActor: ActorRef,
                   config: Config)
    extends Actor
    with ActorLogging {

  val ambariInterface =
    new AmbariClusterInterface(cluster, credentials, config, wsClient)
  val clusterSaveState =
    collection.mutable.Map("NAMENODE" -> false, "HOST_INFO" -> false)

  import akka.pattern.pipe

  override def preStart = {
    log.info(s"Starting cluster actor for ${self.path}")
  }

  override def receive = {
    case Poll() =>
      // update data lake status
      log.info(s"Received a poll for cluster actor ${self.path}")
      log.info(s"Updating status for datalake ${dpCluster.id.get}")

      // reset save state
      resetSaveState
      storageInterface
        .updateDpClusterStatus(dpCluster.copy(state = Some("SYNC_IN_PROGRESS")))
        .map { res =>
          log.info(
            s"updated datalake status to SYNC_IN_PROGRESS for datalake ${dpCluster.id.get} - $res")
        }

      // Make sure we can connect to Ambari
      ambariInterface.ambariConnectionCheck.pipeTo(self)

    case AmbariConnection(status, url, kerberos, connectionError) =>
      log.info(s"Ambari connection to $url check was $status")
      if (!status && connectionError.isDefined)
        log.error(s"Ambari connection failed, reason ${connectionError.get}")
      if (status) {
        log.info("Getting ambari host information")
        ambariInterface.getGetHostInfo.map(SaveHostInfo).pipeTo(self)
      }
    case SaveAtlas(atlas) =>
      log.info("Saving ambari atlas information")
      dbActor ! PersistAtlas(cluster, atlas)
      ambariInterface.getKnoxInfo.map(SaveKnox).pipeTo(self)

    case SaveKnox(knox) =>
      log.info("Saving ambari knox information")
      dbActor ! PersistKnox(cluster, knox)
      ambariInterface.getNameNodeStats.map(SaveNameNode).pipeTo(self)

    case SaveHostInfo(hostInfo) =>
      log.info("Saving ambari host information")
      dbActor ! PersistHostInfo(cluster, hostInfo)
      ambariInterface.getAtlas.map(SaveAtlas).pipeTo(self)

    case SaveNameNode(nameNode) =>
      log.info("Saving ambari name node information")
      dbActor ! PersistNameNode(cluster, nameNode)
      ambariInterface.getBeacon.map(SaveBeacon).pipeTo(self)

    case SaveBeacon(beacon) =>
      log.info("Saving Beacon information")
      dbActor ! PersistBeacon(cluster, beacon)
      ambariInterface.getHdfsInfo.map(SaveHdfs).pipeTo(self)

    case SaveHdfs(hdfs) =>
      log.info("Saving Hdfs information")
      dbActor ! PersistHdfs(cluster, hdfs)
      ambariInterface.getHs2Info.map(SaveHive).pipeTo(self)

    case SaveHive(hive) =>
      log.info("Saving hive information")
      dbActor ! PersistHive(cluster, hive)


    case ServiceSaved(service,cluster) =>
      log.info(s"Cluster state saved - $service")
      if(service.servicename == "NAMENODE")
        clusterSaveState(service.servicename) = true
      if (allServicesSaved) updateDpClusterStatus

    case HostInfoSaved(cluster)=>
      log.info(s"Host state saved for cluster ${cluster.name}")
      clusterSaveState("HOST_INFO") = true
      if (allServicesSaved) updateDpClusterStatus


    case Failure(f) =>
      log.error(s"One of the operations resulted in a failure - ${f}")
      storageInterface
        .updateDpClusterStatus(dpCluster.copy(state = Some("SYNC_ERROR")))
        .map { res =>
          log.info(
            s"updated datalake status to error for datalake ${dpCluster.id.get} - ${res}")
        }

  }

  private def updateDpClusterStatus = {
    log.info("All info saved for cluster")
    storageInterface
      .updateDpClusterStatus(dpCluster.copy(state = Some("SYNCED")))
      .map { res =>
        log.info(
          s"updated datalake status to synced for datalake ${dpCluster.id.get} - $res")
        log.info("Resetting save state")
        resetSaveState
      }
  }

  private def resetSaveState = {
    clusterSaveState.foreach {
      case (k: String, v: Boolean) => clusterSaveState(k) = false
    }
  }

  private def allServicesSaved = {
    clusterSaveState.foldRight(true) {
      case ((k: String, v: Boolean), r: Boolean) => v && r
    }
  }
}
