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

import scala.collection.mutable
import scala.util.Try

case class CredentialReloadEvent()

class CredentialManager(private val storePath: String, private val storePassword: String) extends mutable.Publisher[CredentialReloadEvent] with mutable.Subscriber[KeystoreReloadEvent, mutable.Publisher[KeystoreReloadEvent]] {

  private val keyStoreManager = new KeyStoreManager(storePath, storePassword)

  keyStoreManager.subscribe(this)

  def readUserCredential(alias: String): Try[(String, String)] = {
    keyStoreManager.read(alias, Set("username", "password")).map {
      x => {
        x.values.toList.map(x => new String(x, "UTF-8")) match {
          case List(username, password) => (username, password)
        }
      }
    }
  }

  def writeUserCredential(key: String, username: String, password: String): Try[Unit] = {
    keyStoreManager.write(key, Map("username" -> username.getBytes("UTF-8"), "password" -> password.getBytes("UTF-8")))
  }

  override def notify(publisher: mutable.Publisher[KeystoreReloadEvent], event: KeystoreReloadEvent): Unit = {
    publish(CredentialReloadEvent())
  }
}

object CredentialManager {
  def apply(storePath: String, storePassword: String): CredentialManager = new CredentialManager(storePath, storePassword)
}
