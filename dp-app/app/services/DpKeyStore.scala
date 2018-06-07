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

package services

import com.hortonworks.dataplane.commons.service.api.CredentialManager
import javax.inject.Singleton

import play.api.Configuration
import com.google.inject.Inject
import com.typesafe.scalalogging.Logger
import models.CredentialEntry

import scala.util.Try

@Singleton
class DpKeyStore @Inject()(configuration: Configuration) {
  private val logger = Logger(classOf[DpKeyStore])

  private val storePath = configuration.getString("dp.keystore.path").get
  private val storePassword = configuration.getString("dp.keystore.password").get

  val credentialManager = new CredentialManager(storePath, storePassword)

  def getCredentialEntry(alias: String): Try[CredentialEntry] = {
    credentialManager.readUserCredential(alias)
      .map { credential => CredentialEntry(credential._1, credential._2)}
  }

  def createCredentialEntry(alias: String, username: String, password: String): Try[Unit] = credentialManager.writeUserCredential(alias, username , password)
}
