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

import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics.ProfilerMetric
import com.hortonworks.dataplane.commons.domain.profiler.models.Requests.ProfilerMetricRequest
import com.hortonworks.dataplane.commons.domain.profiler.models.Responses.ProfilerMetricResults
import com.hortonworks.dataplane.cs.KnoxProxyWsClient
import com.hortonworks.dataplane.cs.profiler.MultiMetricProcessor.MetricProcessorType.MetricProcessorType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MetricRetriever {

  def retrieveMetrics(ws: KnoxProxyWsClient, token: Option[String], profilerConfigs: GlobalProfilerConfigs, metricRequest: ProfilerMetricRequest, userName: String): Future[ProfilerMetricResults] = {
    val processorTypeToRequestGroup: Map[MetricProcessorType, MetricRequestGroup] = segregateMetricRequestsBasedOnProcessor(metricRequest.metrics)
    val unsupportedMetricsExist = processorTypeToRequestGroup.keys.exists(MultiMetricProcessor.getProcessor(_).isEmpty)
    if (unsupportedMetricsExist)
      Future.failed(new Exception("Unsupported metric Type/s in given request"))
    else {
      val processorToGroup = processorTypeToRequestGroup.map(
        typeAndRequestGroup => MultiMetricProcessor.getProcessor(typeAndRequestGroup._1).get -> typeAndRequestGroup._2
      )
      val eventualMetricResults = processorToGroup.map(
        processorAndGroup =>
          processorAndGroup._1.retrieveMetrics(ws, token, profilerConfigs, userName, metricRequest.clusterId, metricRequest.context, processorAndGroup._2)
      )
      Future.sequence(eventualMetricResults).map(
        resultGroups => {
          val results = resultGroups.toList.flatten
          val overallStatus = results.forall(_.status)
          ProfilerMetricResults(overallStatus, results)
        }
      )
    }
  }


  private def segregateMetricRequestsBasedOnProcessor(allMetrics: List[ProfilerMetric]): Map[MetricProcessorType, MetricRequestGroup] = {
    allMetrics.foldRight(Map[MetricProcessorType, MetricRequestGroup]()) {
      (metric, segregates) => {
        val processorType = MultiMetricProcessor.processorOf(metric.metricType)
        val updatedMetrics = segregates.getOrElse(processorType, List.empty[ProfilerMetric]) :+ metric
        segregates + (processorType -> updatedMetrics)
      }
    }
  }

}

