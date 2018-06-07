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

package com.hortonworks.dataplane.commons.service.api

import java.nio.file._

import scala.collection.JavaConverters._

// derieved from:
// https://github.com/pathikrit/better-files#file-monitoring

trait FileMonitor {
  val root: Path                                  // starting file
  def start(): Unit                               // start the monitor
  def onChange(path: Path): Unit = {}                   // callback
  def onException(e: Throwable): Unit = {}              // handle errors e.g. a read error
  def stop(): Unit                                // stop the monitor
}

class ThreadFileMonitor(val root: Path) extends Thread with FileMonitor {
  setDaemon(true) // daemonize this thread
  setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
    override def uncaughtException(thread: Thread, exception: Throwable) = onException(exception)
  })

  val service = root.getFileSystem.newWatchService()

  override def run() = Iterator.continually(service.take()).foreach(process)

  override def interrupt() = {
    println("stopping watch")
    service.close()
    super.interrupt()
  }

  override def start() = {
    watch(root.getParent)
    super.start()
  }

  protected[this] def watch(file: Path): Unit = {
    if (Files.isDirectory(file)) {
      file.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)
    }
  }

  protected[this] def process(key: WatchKey) = {
    key.pollEvents().asScala.foreach {
      case event: WatchEvent[Path @unchecked] => dispatch(event.kind(), event.context())
    }
    key.reset()
  }

  def dispatch(eventType: WatchEvent.Kind[Path], file: Path): Unit = onChange(file)
}
