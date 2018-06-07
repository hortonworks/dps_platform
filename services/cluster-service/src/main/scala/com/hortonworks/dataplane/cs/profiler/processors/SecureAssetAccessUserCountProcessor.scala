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
import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics.{MetricType, ProfilerMetric, SecureAssetAccessUserCountMetric}
import com.hortonworks.dataplane.commons.domain.profiler.models.Results._
import com.hortonworks.dataplane.cs.KnoxProxyWsClient
import com.hortonworks.dataplane.cs.profiler._
import com.hortonworks.dataplane.cs.profiler.processors.helpers.DateValidator
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.WSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SecureAssetAccessUserCountProcessor extends MultiMetricProcessor {


  private case class LivyResponse(date: String, count: Long)


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
              val sensitivityQueryDistribution =   (response.json \ "data").as[List[LivyResponse]]
              Future.successful(List(MetricResult(true, MetricType.SecureAssetAccessUserCount,
                SecureAssetAccessUserCountResult(
                  sensitivityQueryDistribution.map(p => SecureAssetAccessUserCountResultForADay(p.date, p.count))))))
            case _ =>
              Future.successful(List(MetricResult(false, MetricType.SecureAssetAccessUserCount,
                MetricErrorDefinition(s"failed to retrieve  SecureAssetAccessUserCount from profiler agent." +
                  s" status: ${response.status}   , response :${response.json.toString()} "))))
          })
        }
      )
    )
  }

  def getQuery(context: ProfilerMetricContext, metricRequest: SecureAssetAccessUserCountMetric): Future[AssetMetricRequest] = {
    context.definition match {
      case ClusterContext =>
        Future.successful {
          Json.obj(
            "metrics" -> List(Map("metric" -> "hivesensitivity",
              "aggType" -> "Snapshot"),
              Map("metric" -> "hiveagg",
                "aggType" -> "Daily")),
            "sql" ->
              s"""SELECT t1.date AS date,
                 |       sum(t1.count) AS count
                 | FROM
                 |  (SELECT CONCAT(DATABASE,'.',TABLE) AS TABLE,
                 |          COUNT,date
                 |   FROM hiveagg_Daily
                 |   WHERE  date <= '${metricRequest.endDate}'
                 |     AND date >= '${metricRequest.startDate}') t1
                 | JOIN
                 |  (SELECT distinct(CONCAT(DATABASE,'.',TABLE)) AS TABLE
                 |   FROM hivesensitivity_Snapshot where status != 'rejected') t2 ON t1.table=t2.table
                 | GROUP BY t1.date
                 | ORDER BY t1.date""".stripMargin.replace("\n", "")
          )
        }
      case CollectionContext(collectionId) =>
        Future.successful {
          Json.obj(
            "metrics" -> List(Map("metric" -> "hivesensitivity",
              "aggType" -> "Snapshot"),
              Map("metric" -> "hiveagg",
                "aggType" -> "Daily"),
              Map("metric" -> "dataset",
                "aggType" -> "Snapshot")),
            "sql" ->
              s"""SELECT t1.date       AS date,
                 |       Sum(t1.count) AS count
                 |  FROM   (SELECT a1.*
                 |        FROM   (SELECT Concat(database, '.', table) AS table,
                 |                       count,
                 |                       date
                 |                FROM   hiveagg_daily
                 |                WHERE  date <= '${metricRequest.endDate}'
                 |                       AND date >= '${metricRequest.startDate}') a1
                 |               JOIN (SELECT assetid AS table
                 |                     FROM   dataset_snapshot
                 |                     WHERE  dataset = '$collectionId') a2
                 |                 ON a1.table = a2.table) t1
                 |       JOIN (SELECT DISTINCT( a1.table ) AS table
                 |             FROM   (SELECT DISTINCT( Concat(database, '.', table) ) AS TABLE
                 |                     FROM   hivesensitivity_snapshot where status != 'rejected') a1
                 |                    JOIN (SELECT assetid AS table
                 |                          FROM   dataset_snapshot
                 |                          WHERE  dataset = '$collectionId') a2
                 |                      ON a1.table = a2.table) t2
                 |         ON t1.table = t2.table
                 |  GROUP  BY t1.date
                 |  ORDER  BY t1.date""".stripMargin.replace("\n", "")
          )
        }
      case x =>
        Future.failed(new Exception(s"SecureAssetAccessUserCountMetric is not available for context $x"))
    }
  }

  private def validateMetrics(metrics: List[ProfilerMetric]): Future[SecureAssetAccessUserCountMetric] = {
    metrics.size match {
      case 1 =>
        metrics.head.definition match {
          case metric: SecureAssetAccessUserCountMetric =>
            DateValidator.validateDate(metric.startDate).flatMap(
              _ => DateValidator.validateDate(metric.endDate)
            ).map(_ => metric)
          case _ => Future.failed(new Exception(s"Invalid metric type ${metrics.head.metricType}"))
        }
      case _ => Future.failed(new Exception("Invalid number of metrics for SecureAssetAccessUserCountMetricProcessor"))
    }
  }
}
