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

import java.util.logging.{Level, Logger}
import java.util
import java.util._

import com.codahale.metrics.Timer
import com.codahale.metrics._
import io.prometheus.client.Collector
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import io.prometheus.client.dropwizard.DropwizardExports



/**
  * This class is almost same as DropwizardExports.java except that we are adding labels here in this class. Label is 'client'.
  * For more info on how this class works you can always look into DropwizardExports.java
  * Collect Dropwizard metrics from a MetricRegistry.
  */

class CustomDropWizardCollector(val registry: MetricRegistry) extends io.prometheus.client.Collector with io.prometheus.client.Collector.Describable {

  private val LOGGER = Logger.getLogger(classOf[DropwizardExports].getName)
  private val CLIENT = Option(System.getenv("DP_METRICS_CLIENT_LABEL")).getOrElse("client")
  private val CLIENT_NAME = Option(System.getenv("DP_METRICS_CLIENT_ID")).getOrElse("defaultClientId")

  private val LABEL_NAMES_LIST = Collections.unmodifiableList(Collections.singletonList(CLIENT))
  private val LABEL_VALUES_LIST = Collections.unmodifiableList(Collections.singletonList(CLIENT_NAME))


  def fromCounter(dropwizardName: String, counter: Counter) = {
    val name = sanitizeMetricName(dropwizardName)
    val clientName: String = CLIENT_NAME
    val sample = new MetricFamilySamples.Sample(name, LABEL_NAMES_LIST, util.Arrays.asList(clientName), counter.getCount.doubleValue)
    util.Arrays.asList(new Collector.MetricFamilySamples(name, Type.GAUGE, getHelpMessage(dropwizardName, counter), util.Arrays.asList(sample)))
  }

  private def getHelpMessage(metricName: String, metric: Metric) = String.format("Generated from Dropwizard metric import (metric=%s, type=%s)", metricName, metric.getClass.getName)

  def fromGauge(dropwizardName: String, gauge: Gauge[_]) = {
    val name = sanitizeMetricName(dropwizardName)
    val obj = gauge.getValue
    if (obj.isInstanceOf[Number]) getGaugeMetricSample(obj.asInstanceOf[Number].doubleValue,name,dropwizardName,gauge)
    else if (obj.isInstanceOf[Boolean]) if (obj.asInstanceOf[Boolean]) getGaugeMetricSample(1,name,dropwizardName,gauge)
    else getGaugeMetricSample(0,name,dropwizardName,gauge)
    else {
      LOGGER.log(Level.FINE, String.format("Invalid type for Gauge %s: %s", name, obj.getClass.getName))
      new util.ArrayList[Collector.MetricFamilySamples]()
    }
  }

  def getGaugeMetricSample(value: Double, name: String,dropwizardName: String, gauge:Gauge[_]) = {
    val sample = new MetricFamilySamples.Sample(name, LABEL_NAMES_LIST, LABEL_VALUES_LIST, value)
    util.Arrays.asList(new Collector.MetricFamilySamples(name, Type.GAUGE, getHelpMessage(dropwizardName, gauge), util.Arrays.asList(sample)))
  }

  def fromSnapshotAndCount(dropwizardName: String, snapshot: Snapshot, count: Long, factor: Double, helpMessage: String): util.List[Collector.MetricFamilySamples] = {
    val name = sanitizeMetricName(dropwizardName)
    val labelNames = util.Arrays.asList(CLIENT, "quantile")
    val samples = util.Arrays.asList(new MetricFamilySamples.Sample(name, labelNames, util.Arrays.asList(CLIENT, "0.5"), snapshot.getMedian * factor), new MetricFamilySamples.Sample(name, labelNames, util.Arrays.asList(CLIENT, "0.75"), snapshot.get75thPercentile * factor), new MetricFamilySamples.Sample(name, labelNames, util.Arrays.asList(CLIENT, "0.95"), snapshot.get95thPercentile * factor), new MetricFamilySamples.Sample(name, labelNames, util.Arrays.asList(CLIENT, "0.98"), snapshot.get98thPercentile * factor), new MetricFamilySamples.Sample(name, labelNames, util.Arrays.asList(CLIENT, "0.99"), snapshot.get99thPercentile * factor), new MetricFamilySamples.Sample(name, labelNames, util.Arrays.asList(CLIENT, "0.999"), snapshot.get999thPercentile * factor), new MetricFamilySamples.Sample(name + "_count", LABEL_NAMES_LIST, LABEL_VALUES_LIST, count))
    util.Arrays.asList(new Collector.MetricFamilySamples(name, Type.SUMMARY, helpMessage, samples))
  }

  def fromHistogram(dropwizardName: String, histogram: Histogram) = fromSnapshotAndCount(dropwizardName, histogram.getSnapshot, histogram.getCount, 1.0, getHelpMessage(dropwizardName, histogram))

  import java.util.concurrent.TimeUnit

  def fromTimer(dropwizardName: String, timer: Timer) = fromSnapshotAndCount(dropwizardName, timer.getSnapshot, timer.getCount, 1.0D / TimeUnit.SECONDS.toNanos(1L), getHelpMessage(dropwizardName, timer))

  def fromMeter(dropwizardName: String, meter: Meter): util.List[MetricFamilySamples] = {
    val name = sanitizeMetricName(dropwizardName)
    util.Arrays.asList(new Collector.MetricFamilySamples(name + "_total", Type.COUNTER, getHelpMessage(dropwizardName, meter), util.Arrays.asList(new MetricFamilySamples.Sample(name + "_total", LABEL_NAMES_LIST, LABEL_VALUES_LIST, meter.getCount))))
  }

  import scala.collection.JavaConversions._

  def collect = {
    val mfSamples = new util.ArrayList[Collector.MetricFamilySamples]
    for (entry <- registry.getGauges.entrySet) {
      mfSamples.addAll(fromGauge(entry.getKey, entry.getValue))
    }
    for (entry <- registry.getCounters.entrySet) {
      mfSamples.addAll(fromCounter(entry.getKey, entry.getValue))
    }
    for (entry <- registry.getHistograms.entrySet) {
      mfSamples.addAll(fromHistogram(entry.getKey, entry.getValue))
    }
    for (entry <- registry.getTimers.entrySet) {
      mfSamples.addAll(fromTimer(entry.getKey, entry.getValue))
    }
    for (entry <- registry.getMeters.entrySet) {
      mfSamples.addAll(fromMeter(entry.getKey, entry.getValue))
    }
    mfSamples
  }

  def describe = new util.ArrayList[Collector.MetricFamilySamples]

  def sanitizeMetricName(dropwizardName: String): String = {
    if (dropwizardName.contains(CLIENT_NAME)) {
      dropwizardName.replaceAll(CLIENT_NAME + "\\.", "")
    }
    dropwizardName.replaceAll("[^a-zA-Z0-9:_]", "_")
  }
}
