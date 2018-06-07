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

package com.hortonworks.dataplane.cs.profiler

import com.hortonworks.dataplane.commons.domain.profiler.models.MetricContext.ProfilerMetricContext
import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics.MetricType
import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics.MetricType.MetricType
import com.hortonworks.dataplane.cs.KnoxProxyWsClient
import com.hortonworks.dataplane.cs.profiler.MultiMetricProcessor.MetricProcessorType.MetricProcessorType
import com.hortonworks.dataplane.cs.profiler.processors._

import scala.concurrent.Future

trait MultiMetricProcessor {

  def retrieveMetrics(ws: KnoxProxyWsClient, token: Option[String], profilerConfigs: GlobalProfilerConfigs, userName: String, clusterId: Long, context: ProfilerMetricContext, metricRequests: MetricRequestGroup): Future[MetricResultGroup]
}


object MultiMetricProcessor {

  object MetricProcessorType extends Enumeration {
    type MetricProcessorType = Value
    val TopKUsersPerAssetProcessor, AssetDistributionBySensitivityTagProcessor,
    QueriesAndSensitivityDistributionProcessor,
    SecureAssetAccessUserCountProcessor, SensitivityDistributionProcessor,
    AssetCountsProcessor, TopKAssetsProcessor,
    TopKCollectionsProcessor = Value
  }

  private val metricToProcessorRelationship: Map[MetricType, MetricProcessorType] = Map(
    MetricType.TopKUsersPerAsset -> MetricProcessorType.TopKUsersPerAssetProcessor,
    MetricType.AssetDistributionBySensitivityTag -> MetricProcessorType.AssetDistributionBySensitivityTagProcessor,
    MetricType.QueriesAndSensitivityDistribution -> MetricProcessorType.QueriesAndSensitivityDistributionProcessor,
    MetricType.SecureAssetAccessUserCount -> MetricProcessorType.SecureAssetAccessUserCountProcessor,
    MetricType.SensitivityDistribution -> MetricProcessorType.SensitivityDistributionProcessor,
    MetricType.AssetCounts -> MetricProcessorType.AssetCountsProcessor,
    MetricType.TopKAssets -> MetricProcessorType.TopKAssetsProcessor,
    MetricType.TopKCollections -> MetricProcessorType.TopKCollectionsProcessor
  )

  def processorOf(metricType: MetricType): MetricProcessorType = metricToProcessorRelationship(metricType)

  def getProcessor(processorType: MetricProcessorType): Option[MultiMetricProcessor] = {
    processorType match {
      case MetricProcessorType.TopKUsersPerAssetProcessor => Some(TopKUsersPerAssetProcessor)
      case MetricProcessorType.AssetDistributionBySensitivityTagProcessor => Some(AssetDistributionBySensitivityTagProcessor)
      case MetricProcessorType.QueriesAndSensitivityDistributionProcessor => Some(QueriesAndSensitivityDistributionProcessor)
      case MetricProcessorType.SecureAssetAccessUserCountProcessor => Some(SecureAssetAccessUserCountProcessor)
      case MetricProcessorType.SensitivityDistributionProcessor => Some(SensitivityDistributionProcessor)
      case MetricProcessorType.AssetCountsProcessor => Some(AssetCountProcessor)
      case MetricProcessorType.TopKAssetsProcessor => Some(TopKAssetsProcessor)
      case MetricProcessorType.TopKCollectionsProcessor => Some(TopKCollectionsProcessor)
      case _ => None
    }
  }

}
