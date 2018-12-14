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

import com.codahale.metrics.MetricRegistry.MetricSupplier
import com.codahale.metrics.{Gauge, JmxReporter, MetricRegistry}
import io.prometheus.client.CollectorRegistry

import scala.collection.JavaConverters._

class MetricsRegistry(private val name:String) {

  private[metrics] val registry =  new MetricRegistry()

  CollectorRegistry.defaultRegistry.register(new CustomDropWizardCollector(registry))

  private val gc = s"$name.gc"

  private val buffers = s"$name.buffers"

  private val memory = s"$name.memory"

  private val threads = s"$name.threads"

  def intJVMMetrics = {
    import java.lang.management.ManagementFactory

    import com.codahale.metrics.jvm.{BufferPoolMetricSet, GarbageCollectorMetricSet, MemoryUsageGaugeSet, ThreadStatesGaugeSet}

    registerMetricSet(gc, new GarbageCollectorMetricSet, registry)
    registerMetricSet(buffers, new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer), registry)
    registerMetricSet(memory, new MemoryUsageGaugeSet, registry)
    registerMetricSet(threads, new ThreadStatesGaugeSet, registry)

    //Set up the JMX reporter
    val reporter = JmxReporter.forRegistry(registry).build
    reporter.start()
  }

  import com.codahale.metrics.{MetricRegistry, MetricSet}

  private def registerMetricSet(prefix: String, metricSet: MetricSet, registry: MetricRegistry):Unit = {
    import scala.collection.JavaConversions._
    metricSet.getMetrics.entrySet.asScala.foreach(entry => entry.getValue match {
      case set: MetricSet => registerMetricSet(s"$prefix.${entry.getKey}", set, registry)
      case _ => registry.register(s"$prefix.${entry.getKey}", entry.getValue)
    })
  }

  intJVMMetrics


  def newMeter(meterName:String) = {
    registry.meter(s"$name.$meterName")
  }


  def newTimer(timerName:String) = {
    registry.timer(s"$name.$timerName")
  }


  def newGauge[T](gaugeName:String,f: () => T): Unit = {
    registry.gauge(s"$name.$gaugeName",new MetricSupplier[Gauge[_]](){
      override def newMetric(): Gauge[T] = new Gauge[T]{
        override def getValue: T = f()
      }
    })
  }


  def counter(counterName:String) = {
    registry.counter(s"$name.$counterName")
  }

}

object MetricsRegistry {
  def apply(name:String): MetricsRegistry = new MetricsRegistry(name)
}
