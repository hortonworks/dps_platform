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

import java.io._
import java.nio.file.{Path, Paths}
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec

import scala.collection.mutable
import scala.util.Try

case class KeystoreReloadEvent()

class KeyStoreManager(private val storePath: String, private val storePassword: String) extends mutable.Publisher[KeystoreReloadEvent] {

  //  initialize
  private var keystore = load(storePath, storePassword)
  private val watcher = new ThreadFileMonitor(Paths.get(storePath)) {
    override def onChange(path: Path): Unit = {
      keystore = load(storePath, storePassword)
      
      // publishing event
      publish(KeystoreReloadEvent())
    }
  }
  watcher.start()

  def read(alias: String, keys: Set[String]): Try[Map[String, Array[Byte]]] = {
    keystore.map { keystore =>
      (for {
        key <- keys
        value = if (!keystore.containsAlias(s"$alias.$key")) {
            throw CredentialNotFoundInKeystoreException(s"Credential not found for key $key of $alias")
          } else {
            keystore.getKey(s"$alias.$key", storePassword.toCharArray).getEncoded
          }

      } yield {
        key -> value
      }).toMap
    }
  }

  def write(alias: String, keyValueMap: Map[String, Array[Byte]]): Try[Unit] = {
    keystore.map { keystore =>
      keyValueMap foreach {
        case (key, value) =>
          if (keystore.containsAlias(s"$alias.$key")) {
            keystore.deleteEntry(s"$alias.$key")
          }
          keystore.setKeyEntry(s"$alias.$key", new SecretKeySpec(value, "AES"), storePassword.toCharArray, null)
      }
      flush(storePath, storePassword, keystore)
    }
  }

  private def load(storePath: String, storePassword: String): Try[KeyStore] = Try({
    var is: InputStream = null
    var keystore: KeyStore = null
    try {
      is = new FileInputStream(storePath)
      keystore = KeyStore.getInstance("JCEKS")
      keystore.load(is, storePassword.toCharArray)
    } finally {
      if (is != null) {
        is.close()
      }
    }
    keystore
  })

  private def flush(storePath: String, storePassword: String, keystore: KeyStore): Try[Unit] = Try({
    var os: OutputStream = null
    try {
      os = new FileOutputStream(storePath)
      keystore.store(os, storePassword.toCharArray)
    } finally {
      if(os != null) {
        os.close()
      }
    }
  })
}

object KeyStoreManager {
  def apply(storePath: String, storePassword: String): KeyStoreManager = new KeyStoreManager(storePath, storePassword)
}
