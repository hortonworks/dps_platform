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

package com.hortonworks.dataplane.commons.domain

import play.api.libs.json.{JsValue, Json}

/**
  * Ambari Specific Data models
  * The names of properties are mapped to those returned from
  * Ambari API's
  */
object Ambari {

  case class AmbariCluster(security: String = "NONE",
                           clusterName: String,
                           clusterType:String,
                           services: Seq[String],
                           knoxUrl:Option[String])

  case class ServiceInfo(serviceName: String,
                         state: String,
                         serviceVersion: String)

  case class AmbariCheckResponse(ambariApiCheck: Boolean,
                                 knoxDetected: Boolean,
                                 ambariApiStatus: Int = -1,
                                 knoxUrl: Option[String],
                                 ambariIpAddress: String,
                                 ambariApiResponseBody: JsValue)

  case class AmbariDetailRequest(url: String,
                                 knoxDetected: Boolean,
                                 knoxUrl: Option[String],
                                 ambariUser: Option[String],
                                 ambariPass: Option[String],
                                 knoxTopology: Option[String],
                                 allowUntrusted: Boolean) {

    def hasCredentials =  ambariUser.isDefined && ambariPass.isDefined && knoxTopology.isEmpty
    def hasTopology = knoxTopology.isDefined && ambariUser.isEmpty && ambariPass.isEmpty

  }

  case class AmbariForbiddenResponse(status: Int,
                                     message: String,
                                     jwtProviderUrl: Option[String])

  case class DiskInfo(
      available: Option[String],
      device: Option[String],
      used: Option[String],
      percent: Option[String],
      size: Option[String],
      `type`: Option[String],
      mountpoint: Option[String]
  )

  case class ClusterHost(
      cluster_name: String,
      cpu_count: Option[Double],
      disk_info: Option[List[DiskInfo]],
      host_health_report: Option[String],
      host_name: Option[String],
      host_state: Option[String],
      host_status: Option[String],
      ip: Option[String],
      last_heartbeat_time: Option[Double],
      last_registration_time: Option[Double],
      maintenance_state: Option[String],
      os_arch: Option[String],
      os_family: Option[String],
      os_type: Option[String],
      ph_cpu_count: Option[Double],
      public_host_name: Option[String],
      rack_info: Option[String],
      recovery_summary: Option[String],
      total_mem: Option[Double]
  )

  case class NameNodeInfo(
      CapacityRemaining: Option[Double],
      CapacityTotal: Option[Double],
      CapacityUsed: Option[Double],
      DeadNodes: Option[String],
      DecomNodes: Option[String],
      HeapMemoryMax: Option[Double],
      HeapMemoryUsed: Option[Double],
      LiveNodes: Option[String],
      NonDfsUsedSpace: Option[Double],
      NonHeapMemoryMax: Option[Double],
      NonHeapMemoryUsed: Option[Double],
      PercentRemaining: Option[Double],
      PercentUsed: Option[Double],
      Safemode: Option[String],
      StartTime: Option[Double],
      TotalFiles: Option[Double],
      display_name: Option[String],
      service_name: Option[String],
      state: Option[String]
  )

  case class ClusterServiceWithConfigs(
      serviceid: Option[Long],
      servicename: String,
      clusterid: Option[Long] = None,
      servicehost: String,
      configProperties: Option[ConfigurationInfo] = None
  )

  case class ConfigurationInfo(
      stats: JsValue,
      properties: Seq[ConfigType]
  )

  case class ConfigType(
      tag: String,
      `type`: String,
      Config: JsValue,
      version: Long,
      properties: Map[String, String],
      properties_attributes: JsValue
  )

  case class ClusterProperties(
      version: String,
      cluster_id: Long,
      total_hosts: Long,
      cluster_name: String,
      health_report: JsValue,
      security_type: String,
      desired_configs: JsValue,
      provisioning_state: String,
      credential_store_properties: JsValue,
      desired_service_config_versions: JsValue
  )

  case class AmbariResponseWithDpClusterId(
      id: Long,
      data: JsValue
  )

  implicit val diskInfoReads = Json.reads[DiskInfo]
  implicit val diskInfoWrites = Json.writes[DiskInfo]
  implicit val clusterHealthWrites = Json.writes[ClusterHost]
  implicit val clusterHealthReads = Json.reads[ClusterHost]
  implicit val nameNodeWrites = Json.writes[NameNodeInfo]
  implicit val nameNodeReads = Json.reads[NameNodeInfo]
  implicit val seviceInfoReads = Json.reads[ServiceInfo]
  implicit val serviceInfoWrites = Json.writes[ServiceInfo]
  implicit val configTypeReads = Json.reads[ConfigType]
  implicit val configTypeWrites = Json.writes[ConfigType]
  implicit val configurationInfoReads = Json.reads[ConfigurationInfo]
  implicit val configurationInfoWrites = Json.writes[ConfigurationInfo]
  implicit val serviceWithEndpointWrites =
    Json.writes[ClusterServiceWithConfigs]
  implicit val serviceWithEndpointReads = Json.reads[ClusterServiceWithConfigs]
  implicit val ambariClusterReads = Json.reads[AmbariCluster]
  implicit val ambariClusterWrites = Json.writes[AmbariCluster]
  implicit val ambariCheckResponseWrites = Json.writes[AmbariCheckResponse]
  implicit val ambariCheckResponseReads = Json.reads[AmbariCheckResponse]
  implicit val ambariDetailRequestWrites = Json.writes[AmbariDetailRequest]
  implicit val ambariDetailRequestReads = Json.reads[AmbariDetailRequest]

  implicit val ambariForbiddenWrites = Json.writes[AmbariForbiddenResponse]
  implicit val ambariForbiddenWritesReads = Json.reads[AmbariForbiddenResponse]

  implicit val clusterPropertiesReads = Json.reads[ClusterProperties]
  implicit val clusterPropertiesWrites = Json.writes[ClusterProperties]

  implicit val ambariResponseWithDpClusterIdReads =
    Json.reads[AmbariResponseWithDpClusterId]
  implicit val ambariResponseWithDpClusterIdWrites =
    Json.writes[AmbariResponseWithDpClusterId]

}
