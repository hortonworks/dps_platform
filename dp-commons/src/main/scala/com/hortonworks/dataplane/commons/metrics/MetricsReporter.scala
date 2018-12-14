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

package com.hortonworks.dataplane.commons.metrics

import java.io.StringWriter

import com.codahale.metrics._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import play.api.libs.json.{JsValue, Json}

import scala.collection.JavaConverters._

object MetricsReporter {

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  val prometheusNameParam = "name[]"
  val prometheus = "prometheus"
  val exportParam = "exporter"

  def asJson(implicit mr: MetricsRegistry): JsValue = {
    val counters = mr.registry.getCounters.asScala
    val meters = mr.registry.getMeters.asScala
    val gauges = mr.registry.getGauges.asScala
    val timers = mr.registry.getTimers.asScala

    val map = scala.collection.mutable.LinkedHashMap[String, Any]()

    counters.foreach {
      case (k: String, v: Counter) =>
        map.put(k, v.getCount())
    }

    timers.foreach {
      case (k: String, v: Timer) =>
        map.put(k,
          Map("count" -> v.getCount,
            "meanRate" -> v.getMeanRate,
            "max" -> v.getSnapshot.getMax,
            "min" -> v.getSnapshot.getMin,
            "mean" -> v.getSnapshot.getMean))
    }

    meters.foreach {
      case (k: String, v: Meter) =>
        map.put(k, Map("count" -> v.getCount, "meanRate" -> v.getMeanRate))
    }

    gauges.foreach {
      case (k: String, v: Gauge[_]) =>
        map.put(k, v.getValue)
    }

    Json.parse(mapper.writeValueAsBytes(map.toMap))

  }


  def asPrometheusTextExport(params:java.util.Set[String])(implicit mr: MetricsRegistry):String = {
    val writer = new StringWriter()
    TextFormat.write004(writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(params))
    writer.toString
  }


}
