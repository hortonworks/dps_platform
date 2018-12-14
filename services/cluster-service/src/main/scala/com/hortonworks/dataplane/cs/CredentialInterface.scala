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

package com.hortonworks.dataplane.cs

import java.util.concurrent.ConcurrentHashMap
import javax.inject.{Inject, Singleton}

import com.hortonworks.dataplane.commons.service.api.{CredentialManager, CredentialNotFoundInKeystoreException, CredentialReloadEvent}
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.collection.{concurrent, mutable}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global


trait CredentialInterface {
  def getCredential(key: String): Future[Credentials]

  def onReload(key: String, callback: Unit => Unit): Unit
}

@Singleton
class CredentialInterfaceImpl @Inject()(val config: Config) extends CredentialInterface with mutable.Subscriber[CredentialReloadEvent, mutable.Publisher[CredentialReloadEvent]] {

  private val storePath = config.getString("dp.keystore.path")
  private val storePassword = config.getString("dp.keystore.password")

  private val credentialManager = new CredentialManager(storePath, storePassword)

  private val callbacks: concurrent.Map[String, Unit => Unit] = new ConcurrentHashMap[String, Unit => Unit]().asScala

  // subscribe for events
  credentialManager.subscribe(this)

  override def getCredential(key: String): Future[Credentials] =
    Future
      .fromTry(
        credentialManager.readUserCredential(key)
        .map (credential => Credentials(Some(credential._1), Some(credential._2)))
        .recover {
          case ex: CredentialNotFoundInKeystoreException => Credentials(Some(config.getString(s"$key.username")), Some(config.getString(s"$key.password")))
        })

  override def onReload(key: String, callback: Unit => Unit): Unit = {
    callbacks.put(key, callback)
  }

  override def notify(publisher: mutable.Publisher[CredentialReloadEvent], event: CredentialReloadEvent): Unit = {
    callbacks.values.foreach(cCallback => cCallback())
  }
}
