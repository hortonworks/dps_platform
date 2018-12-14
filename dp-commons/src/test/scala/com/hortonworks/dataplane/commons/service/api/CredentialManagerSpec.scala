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

import java.io.File
import java.nio.file.Paths

import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.scalatest.TryValues._

import sys.process._

class CredentialManagerSpec extends AsyncFlatSpec with AsyncMockFactory with Matchers with BeforeAndAfterAll {
  private val randomGenerator = new scala.util.Random()
  private val keyStoreFilePath = s"${Paths.get("").toAbsolutePath.toString}/test-${randomGenerator.nextInt()}.jceks"
  private val keyStorePassword = "changeit"
  private val keyStoreFile = s"keytool -genseckey -keystore $keyStoreFilePath -storetype jceks -storepass $keyStorePassword -alias jceksaes -keypass mykeypass" !!
  private val credentialManager = new CredentialManager(keyStoreFilePath, keyStorePassword)


  override def afterAll(): Unit ={
    val file:File = new File(keyStoreFilePath)
    file.delete()
  }

  "CredentialManager" should "write credentials to the keystore" in {
    val result = credentialManager.writeUserCredential("DPSPlatform.test.credential","test","test@123")
    assert(result.isSuccess)
  }

  "CredentialManager" should "read credentials from the keystore" in {
    val result = credentialManager.readUserCredential("DPSPlatform.test.credential")
    assert(result.isSuccess)
    assert(result.success.value._1 === "test")
    assert(result.success.value._2 === "test@123")
  }

  "CredentialManager" should "throw exception if a key read is not present in the keystore" in {
    val result = credentialManager.readUserCredential("xyz.credential")
    assert(result.isFailure)
    result.failure.exception shouldBe a [CredentialNotFoundInKeystoreException]
  }

}
