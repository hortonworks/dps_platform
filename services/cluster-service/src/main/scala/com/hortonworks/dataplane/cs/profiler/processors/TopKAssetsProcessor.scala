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
import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics.{MetricType, ProfilerMetric, TopKAssetsMetric}
import com.hortonworks.dataplane.commons.domain.profiler.models.Results.{MetricErrorDefinition, MetricResult, TopKAssetsResult}
import com.hortonworks.dataplane.cs.KnoxProxyWsClient
import com.hortonworks.dataplane.cs.profiler._
import com.hortonworks.dataplane.cs.profiler.processors.helpers.DateValidator
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.WSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TopKAssetsProcessor extends MultiMetricProcessor {

  private case class LivyResponse(asset: String, count: Long)


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
              val tagsAndCounts = (response.json \ "data").as[List[LivyResponse]]
              Future.successful(List(MetricResult(true, MetricType.TopKAssets, TopKAssetsResult(tagsAndCounts.map(p => p.asset -> p.count).toMap))))
            case _ =>
              Future.successful(List(MetricResult(false, MetricType.TopKAssets, MetricErrorDefinition(s"failed to retrieve  TopKAssets from profiler agent" +
                s". status: ${response.status}   , response :${response.json.toString()} "))))
          })
        }
      )
    )
  }


  def getQuery(context: ProfilerMetricContext, metricRequest: TopKAssetsMetric): Future[AssetMetricRequest] = {

    context.definition match {
      case ClusterContext =>
        Future.successful {
          Json.obj(
            "metrics" -> List(
              Map("metric" -> "hiveagg",
                "aggType" -> "Daily")),
            "sql" ->
              s"""SELECT Concat(DATABASE, '.', table) AS asset,sum(count) as count
                 |        FROM   hiveagg_daily
                 |        WHERE  date >= '${metricRequest.startDate}'  and date <= '${metricRequest.endDate}'
                 |  GROUP  BY Concat(DATABASE, '.', table)
                 |  ORDER  BY count DESC
                 |  LIMIT  ${metricRequest.k}""".stripMargin.replace("\n", "")
          )
        }
      case CollectionContext(collectionId) =>
        Future.successful {
          Json.obj(
            "metrics" -> List(
              Map("metric" -> "hiveagg",
                "aggType" -> "Daily"),
              Map("metric" -> "dataset",
                "aggType" -> "Snapshot")),
            "sql" ->
              s"""
                 |SELECT a.table as asset,sum(count) as count FROM
                 |  (SELECT Concat(DATABASE, '.', table) AS table,count
                 |        FROM   hiveagg_daily
                 |        WHERE  date >= '${metricRequest.startDate}'  and date <= '${metricRequest.endDate}'
                 |        ) a JOIN
                 |        (SELECT assetid as table
                 |    FROM   dataset_snapshot
                 |    WHERE  dataset = '$collectionId') b
                 |    ON a.table=b.table
                 |    GROUP BY a.table
                 |    ORDER BY count DESC
                 |    LIMIT ${metricRequest.k}""".stripMargin.replace("\n", "")
          )
        }
      case x =>
        Future.failed(new Exception(s"TopKCollectionsMetric is not available for context $x"))
    }
  }

  private def validateMetrics(metrics: List[ProfilerMetric]): Future[TopKAssetsMetric] = {
    metrics.size match {
      case 1 =>
        metrics.head.definition match {
          case metric: TopKAssetsMetric =>
            DateValidator.validateDate(metric.startDate).flatMap(
              _ => DateValidator.validateDate(metric.endDate)
            ).map(_ => metric)
          case _ => Future.failed(new Exception(s"Invalid metric type ${metrics.head.metricType}"))
        }
      case _ => Future.failed(new Exception("Invalid number of metrics for TopKAssetsProcessor"))
    }
  }
}
