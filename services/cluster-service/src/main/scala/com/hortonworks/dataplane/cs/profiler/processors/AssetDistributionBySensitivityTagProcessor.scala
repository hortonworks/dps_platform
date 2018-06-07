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

package com.hortonworks.dataplane.cs.profiler.processors

import com.hortonworks.dataplane.commons.domain.Constants.PROFILER
import com.hortonworks.dataplane.commons.domain.profiler.models.MetricContext.{ClusterContext, CollectionContext, ProfilerMetricContext}
import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics.{AssetDistributionBySensitivityTagMetric, MetricType, ProfilerMetric}
import com.hortonworks.dataplane.commons.domain.profiler.models.Results.{AssetDistributionBySensitivityTagResult, MetricErrorDefinition, MetricResult}
import com.hortonworks.dataplane.cs.KnoxProxyWsClient
import com.hortonworks.dataplane.cs.profiler._
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.WSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssetDistributionBySensitivityTagProcessor extends MultiMetricProcessor {

  private case class LivyResponse(tag: String, count: Long)


  private implicit val livyResponseFormatter: Format[LivyResponse] = Json.format[LivyResponse]


  def retrieveMetrics(ws: KnoxProxyWsClient, token: Option[String], profilerConfigs: GlobalProfilerConfigs, userName: String, clusterId: Long, context: ProfilerMetricContext, metricRequests: MetricRequestGroup): Future[MetricResultGroup] = {

    validateMetrics(metricRequests).flatMap(metric =>
      getQuery(context, metric).flatMap(
        postData => {
          val future: Future[WSResponse] = ws.url(profilerConfigs.assetMetricsUrl, clusterId, PROFILER)
            .withToken(token)
            .withHeaders("Accept" -> "application/json")
            .post(postData)
          future.flatMap(response => response.status match {
            case 202 =>
              val tagsAndCounts =  (response.json \ "data").as[List[LivyResponse]]
              Future.successful(List(MetricResult(true, MetricType.AssetDistributionBySensitivityTag, AssetDistributionBySensitivityTagResult(tagsAndCounts.map(p => p.tag -> p.count).toMap))))
            case _ =>
              Future.successful(List(MetricResult(false, MetricType.AssetDistributionBySensitivityTag, MetricErrorDefinition(s"failed to retrieve  AssetDistributionBySensitivityTag from profiler agent. " +
                s"status: ${response.status}  , response :${response.json.toString()} "))))
          })
        }
      )
    )
  }

  def getQuery(context: ProfilerMetricContext, metricRequest: AssetDistributionBySensitivityTagMetric): Future[AssetMetricRequest] = {
    context.definition match {
      case ClusterContext =>
        Future.successful {
          Json.obj(
            "metrics" -> List(Map("metric" -> "hivesensitivity",
              "aggType" -> "Snapshot")),
            "sql" ->
              s"""select label as tag,count(distinct(CONCAT(database,'.',table))) as count
                 |  from hivesensitivity_Snapshot  where status != 'rejected'
                 |  group by tag order by count DESC limit ${metricRequest.k}""".stripMargin.replace("\n", "")
          )
        }
      case CollectionContext(collectionId) =>
        Future.successful {
          Json.obj(
            "metrics" -> List(Map("metric" -> "hivesensitivity",
              "aggType" -> "Snapshot"),
              Map("metric" -> "dataset",
                "aggType" -> "Snapshot")),
            "sql" ->
              s""" select t1.tag as tag,count(distinct(t1.table)) as count
                 |  FROM
                 |  (select label as tag,CONCAT(database,'.',table) as table
                 |  from hivesensitivity_Snapshot  where status != 'rejected') t1
                 |  JOIN
                 |  (select assetid from dataset_Snapshot where dataset='$collectionId') t2
                 |  ON t1.table=t2.assetid
                 |  group by tag order by count DESC limit ${metricRequest.k}""".stripMargin.replaceAll("\n", " ")
          )
        }

      case x =>
        Future.failed(new Exception(s"AssetDistributionBySensitivityTagMetric is not defined for context $x"))
    }
  }

  private def validateMetrics(metrics: List[ProfilerMetric]): Future[AssetDistributionBySensitivityTagMetric] = {
    metrics.size match {
      case 1 =>
        metrics.head.definition match {
          case metric: AssetDistributionBySensitivityTagMetric =>
            Future.successful(metric)
          case _ => Future.failed(new Exception(s"Invalid metric type ${metrics.head.metricType}"))
        }
      case _ => Future.failed(new Exception("Invalid number of metrics for AssetDistributionBySensitivityTagProcessor"))
    }
  }
}
