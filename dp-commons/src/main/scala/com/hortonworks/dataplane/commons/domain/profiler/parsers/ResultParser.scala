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

package com.hortonworks.dataplane.commons.domain.profiler.parsers

import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics.MetricType.MetricType
import com.hortonworks.dataplane.commons.domain.profiler.models.Metrics._
import com.hortonworks.dataplane.commons.domain.profiler.models.Results._
import play.api.libs.functional.syntax._
import play.api.libs.json._

object ResultParser {

  private val metricTypeIdentifier = "metricType"

  private val resultDefinitionIdentifier = "definition"

  private val resultStatusIdentifier = "status"

  private implicit val metricTypeFormat: Format[MetricType] = new Format[MetricType] {
    def reads(json: JsValue) = JsSuccess(MetricType.withName(json.as[String]))

    def writes(myEnum: MetricType) = JsString(myEnum.toString)
  }
  private implicit val topKUsersPerAssetMetricFormat: Format[TopKUsersPerAssetResult] = Json.format[TopKUsersPerAssetResult]
  private implicit val assetDistributionBySensitivityTagResultFormat: Format[AssetDistributionBySensitivityTagResult] =
    Json.format[AssetDistributionBySensitivityTagResult]
  private implicit val queriesAndSensitivityDistributionResultFormat: Format[QueriesAndSensitivityDistributionResult] =
    Json.format[QueriesAndSensitivityDistributionResult]
  private implicit val secureAssetAccessUserCountResultForADayFormat: Format[SecureAssetAccessUserCountResultForADay] =
    Json.format[SecureAssetAccessUserCountResultForADay]
  private implicit val secureAssetAccessUserCountResultFormat: Format[SecureAssetAccessUserCountResult] =
    Json.format[SecureAssetAccessUserCountResult]
  private implicit val sensitivityDistributionResultFormat: Format[SensitivityDistributionResult] =
    Json.format[SensitivityDistributionResult]
  private implicit val topKCollectionsResultFormat: Format[TopKCollectionsResult] =
    Json.format[TopKCollectionsResult]
  private implicit val topKAssetsResultFormat: Format[TopKAssetsResult] =
    Json.format[TopKAssetsResult]
  private implicit val assetCountsResultForADayFormat: Format[AssetCountsResultForADay] =
    Json.format[AssetCountsResultForADay]
  private implicit val assetCountsResultFormat: Format[AssetCountsResult] =
    Json.format[AssetCountsResult]
  private implicit val metricErrorDefinitionFormat: Format[MetricErrorDefinition] =
    Json.format[MetricErrorDefinition]

  private implicit val assetReadEither: Reads[Either[MetricResult, ErrorMessage]] = (
    (JsPath \ resultStatusIdentifier).read[Boolean] and
      (JsPath \ metricTypeIdentifier).read[MetricType]
      and (JsPath \ resultDefinitionIdentifier).read[JsValue]) (
    (status, metricType, definition) => {
      if (status) {
        metricType match {
          case MetricType.TopKUsersPerAsset => Left(MetricResult(status, MetricType.TopKUsersPerAsset, definition.as[TopKUsersPerAssetResult]))
          case MetricType.AssetDistributionBySensitivityTag => Left(MetricResult(status, MetricType.AssetDistributionBySensitivityTag
            , definition.as[AssetDistributionBySensitivityTagResult]))
          case MetricType.QueriesAndSensitivityDistribution => Left(MetricResult(status, MetricType.QueriesAndSensitivityDistribution
            , definition.as[QueriesAndSensitivityDistributionResult]))
          case MetricType.SecureAssetAccessUserCount => Left(MetricResult(status, MetricType.SecureAssetAccessUserCount
            , definition.as[SecureAssetAccessUserCountResult]))
          case MetricType.SensitivityDistribution => Left(MetricResult(status, MetricType.SensitivityDistribution
            , definition.as[SensitivityDistributionResult]))
          case MetricType.TopKCollections => Left(MetricResult(status, MetricType.TopKCollections
            , definition.as[TopKCollectionsResult]))
          case MetricType.TopKAssets => Left(MetricResult(status, MetricType.TopKAssets
            , definition.as[TopKAssetsResult]))
          case MetricType.AssetCounts => Left(MetricResult(status, MetricType.AssetCounts
            , definition.as[AssetCountsResult]))
          case _ => Right(s"unsupported result type ${metricType.toString}")
        }
      }
      else Left(MetricResult(status, metricType, definition.as[MetricErrorDefinition]))
    }
  )

  implicit val metricFormat: Format[MetricResult] = new Format[MetricResult] {

    def reads(json: JsValue) =
      json.as[Either[MetricResult, ErrorMessage]] match {
        case Left(metric) => JsSuccess(metric)
        case Right(error) => JsError(error)
      }

    def writes(metric: MetricResult) = {
      metric.definition match {
        case definition: TopKUsersPerAssetResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: AssetDistributionBySensitivityTagResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: QueriesAndSensitivityDistributionResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: SecureAssetAccessUserCountResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: SensitivityDistributionResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: TopKCollectionsResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: TopKAssetsResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: AssetCountsResult => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case definition: MetricErrorDefinition => Json.toJson(Map(resultStatusIdentifier -> JsBoolean(metric.status),
          metricTypeIdentifier -> JsString(metric.metricType.toString), resultDefinitionIdentifier -> Json.toJson(definition)))
        case _ => JsNull
      }

    }
  }
}
