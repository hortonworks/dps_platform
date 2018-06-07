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

package com.hortonworks.dataplane.cs.sync

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.hortonworks.dataplane.commons.domain.Entities.{Cluster, ClusterServiceHost, DataplaneCluster, HJwtToken, ClusterService => ClusterServiceData}
import com.hortonworks.dataplane.cs._
import com.hortonworks.dataplane.cs.tls.SslContextManager
import com.hortonworks.dataplane.knox.Knox.KnoxConfig
import com.hortonworks.dataplane.knox.KnoxApiExecutor
import com.typesafe.config.Config
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.util.{Failure, Try}
import scala.concurrent.ExecutionContext.Implicits.global

private[sync] object TaskType extends Enumeration {
  type TaskType = Value
  val HostInfo, Atlas, Ranger, DpProfiler, Knox, NameNode, Beacon, Hdfs, Hive = Value
}

private[sync] object TaskStatus extends Enumeration {
  type TaskStatus = Value
  val Complete, Failed = Value
}

import com.hortonworks.dataplane.cs.sync.TaskType.TaskType
import com.hortonworks.dataplane.cs.sync.TaskStatus.TaskStatus

private[sync] case class ExecuteTask(hJwtToken: Option[HJwtToken])
private[sync] case class ExecutionComplete(taskStatus: TaskStatus)
private[sync] case class TaskComplete(taskType: TaskType,
                                      taskStatus: TaskStatus)
private[sync] case class ClusterData(dataplaneCluster: DataplaneCluster,
                                     cluster: Cluster)

sealed trait SyncTaskBase extends Actor with ActorLogging {
  val taskType: TaskType
  val wsClient: WSClient
  val config: Config
  val clusterData: ClusterData
  val storageInterface: StorageInterface
  val clusterSynchronizer:ActorRef
  def executeTask(implicit hJwtToken: Option[HJwtToken]): Future[TaskStatus]
  final def notifyStatus(taskStatus: TaskStatus) = {
    clusterSynchronizer ! TaskComplete(taskType, taskStatus)
  }

}

abstract class ClusterSyncTask(cl: ClusterData,
                               c: Config,
                               w: WSClient,
                               si: StorageInterface,
                               credentialInterface: CredentialInterface,
                               cs:ActorRef)
    extends SyncTaskBase {

  final override val config: Config = c
  final override val wsClient: WSClient = w
  final override val storageInterface: StorageInterface = si
  final override val clusterData: ClusterData = cl
  final override val clusterSynchronizer:ActorRef = cs
  final protected val dbActor: ActorRef =
    context.actorOf(Props(classOf[PersistenceActor], si))
  private val knoxConfig = KnoxConfig(
    Try(c.getString("dp.services.knox.token.topology")).getOrElse("token"),
    cl.dataplaneCluster.knoxUrl)
  final protected def knoxEnabled =
    cl.dataplaneCluster.knoxEnabled.isDefined && cl.dataplaneCluster.knoxEnabled.get && cl.dataplaneCluster.knoxUrl.isDefined
  final protected val executor =
    if (knoxEnabled) KnoxApiExecutor.withTokenCaching(knoxConfig, w)
    else KnoxApiExecutor.withTokenDisabled(knoxConfig, w)
  final protected val ambariInterface: AmbariInterfaceV2 =
    new AmbariClusterInterfaceV2(cl.cluster, cl.dataplaneCluster, c, credentialInterface, executor, wsClient)

  import akka.pattern.pipe
  override final def receive: Receive = {
    case ExecuteTask(hJwtToken) =>
      implicit val token = hJwtToken
      executeTask.map(ExecutionComplete).pipeTo(self)
    case ExecutionComplete(status) =>
        notifyStatus(status)
    case Failure(f) =>
        log.error(s"Task failed with $f")
        notifyStatus(TaskStatus.Failed)
  }

  override final def postStop(): Unit = {
    log.info(s"Stopping worker - $taskType for ${clusterData.dataplaneCluster.ambariUrl}")
  }


  final protected def persistService(cluster: Cluster,
                           toPersist: ClusterServiceData,
                           endpoints: Seq[ClusterServiceHost]) = {
    log.info("Inserting service information")
    storageInterface.addService(toPersist, endpoints)
  }

  final protected def updateService(cluster: Cluster,
                          toPersist: ClusterServiceData,
                          endpoints: Seq[ClusterServiceHost]) = {
    log.info("Service exists, updating info")
    storageInterface
      .updateServiceByName(toPersist, endpoints)
  }

}

class ClusterSynchronizer(private val config: Config,
                          private val clusterData: ClusterData,
                          private val wSClient: WSClient,
                          private val storageInterface: StorageInterface,
                          private val credentialInterface: CredentialInterface,
                          callback: PartialFunction[TaskStatus,Unit]) extends Actor with ActorLogging{



  val workers = {
    val map = collection.mutable.Map[TaskType,ActorRef]()
    map.put(TaskType.NameNode,context.actorOf(Props(classOf[FetchNameNodeTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.HostInfo,context.actorOf(Props(classOf[FetchHostInfoTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.Atlas,context.actorOf(Props(classOf[FetchAtlasTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.Knox,context.actorOf(Props(classOf[FetchKnoxTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.Beacon,context.actorOf(Props(classOf[FetchBeaconTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.Hdfs,context.actorOf(Props(classOf[FetchHdfsTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.Hive,context.actorOf(Props(classOf[FetchHiveTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.Ranger,context.actorOf(Props(classOf[FetchRangerTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.put(TaskType.DpProfiler,context.actorOf(Props(classOf[FetchDpProfilerTask],clusterData,config,wSClient,storageInterface, credentialInterface, self)))
    map.toMap
  }

  val completion = collection.mutable.Set[TaskType]()
  workers.keys.foreach( tt => completion.add(tt))


  override def receive: Receive = {
    case ExecuteTask(hJwtToken) =>
      workers.foreach { case (k,v) =>
        log.info(s"Starting task for $k")
        v ! ExecuteTask(hJwtToken)
      }

    case TaskComplete(taskType,taskStatus) =>
      log.info(s"Task status for $taskType - $taskStatus")
      completion.remove(taskType)
      if(completion.isEmpty){
        log.info("All tasks completed, Calling routine to destroy all workers")
        callback(TaskStatus.Complete)
      }

  }

  override def postStop(): Unit = {
    log.info(s"Stopping Synchronizer for ${clusterData.dataplaneCluster.ambariUrl}")
  }
}
