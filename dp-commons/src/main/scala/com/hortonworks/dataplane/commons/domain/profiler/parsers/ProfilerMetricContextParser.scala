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

import com.hortonworks.dataplane.commons.domain.profiler.models.Assets.Asset
import com.hortonworks.dataplane.commons.domain.profiler.models.MetricContext._
import com.hortonworks.dataplane.commons.domain.profiler.models.MetricContext.MetricContextType._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import AssetParser._

object ProfilerMetricContextParser {


  private val metricContextTypeIdentifier = "contextType"

  private val metricContextDefinitionIdentifier = "definition"

  private implicit val contextTypeFormat: Format[MetricContextType] = new Format[MetricContextType] {
    def reads(json: JsValue) = JsSuccess(MetricContextType.withName(json.as[String]))

    def writes(myEnum: MetricContextType) = JsString(myEnum.toString)
  }
  private implicit val collectionContextFormat: Format[CollectionContext] = Json.format[CollectionContext]
  private implicit val profileMetricContextRead: Reads[Either[ProfilerMetricContext, ErrorMessage]] = ((JsPath \ metricContextTypeIdentifier).read[MetricContextType]
    and (JsPath \ metricContextDefinitionIdentifier).read[JsValue]) (
    (contextType, definition) => {
      contextType match {
        case MetricContextType.CLUSTER => Left(ProfilerMetricContext(MetricContextType.CLUSTER, ClusterContext))
        case MetricContextType.COLLECTION => Left(ProfilerMetricContext(MetricContextType.COLLECTION, definition.as[CollectionContext]))
        case MetricContextType.ASSET => Left(ProfilerMetricContext(MetricContextType.ASSET, definition.as[Asset]))
        case _ => Right(s"unsupported context type ${contextType.toString}")
      }
    }
  )

  implicit val profileMetricContextFormat: Format[ProfilerMetricContext] = new Format[ProfilerMetricContext] {

    def reads(json: JsValue) = {
      json.as[Either[ProfilerMetricContext, ErrorMessage]] match {
        case Left(context) => JsSuccess(context)
        case Right(error) => JsError(error)
      }
    }

    def writes(context: ProfilerMetricContext) = context.definition match {
      case ClusterContext => Json.toJson(Map(metricContextTypeIdentifier -> JsString(context.contextType.toString), metricContextDefinitionIdentifier -> emptyJson))
      case definition: CollectionContext => Json.toJson(Map(metricContextTypeIdentifier -> JsString(context.contextType.toString), metricContextDefinitionIdentifier -> Json.toJson(definition)))
      case definition: Asset => Json.toJson(Map(metricContextTypeIdentifier -> JsString(context.contextType.toString), metricContextDefinitionIdentifier -> Json.toJson(definition)))
      case _ => JsNull
    }
  }

}
