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

package com.hortonworks.dataplane.knox

import java.net.ConnectException
import javax.net.ssl.SSLException

import com.hortonworks.dataplane.commons.domain.Entities.{Error, WrappedErrorException}
import com.hortonworks.dataplane.knox.Knox.{KnoxConfig, TokenResponse}
import org.apache.commons.lang3.exception.ExceptionUtils
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BasicKnoxApiExecutor(c: KnoxConfig, w: WSClient) extends KnoxApiExecutor{

  override val wSClient: WSClient = w
  override val config: KnoxConfig = c

  protected val tokenUrl = config.tokenUrl

  def getKnoxApiToken(token: String):Future[TokenResponse] = {
    wSClient
      .url(tokenUrl)
      .withHeaders("Cookie" -> token,"Content-Type" -> "application/json","Accept" -> "application/json")
      .get()
      .map { res =>
        res.status match {
          case 200 => res.json.validate[TokenResponse].get
          case 302 => throw WrappedErrorException(Error(500, "Knox token or the certificate on cluster might be corrupted.", "cluster.ambari.status.knox.public-key-corrupted-or-bad-auth"))
          case 403 => throw WrappedErrorException(Error(403, "User does not have required rights. Please disable or configure Ranger to add roles or log-in as another user.", "cluster.ambari.status.knox.ranger-rights-unavailable"))
          case 404 => throw WrappedErrorException(Error(500, "Knox token topology is not validated and deployment descriptor is not created.", "cluster.ambari.status.knox.configuration-error"))
          case 500 => throw WrappedErrorException(Error(500, "Knox certificate on cluster might be corrupted.", "cluster.ambari.status.knox.public-key-corrupted"))
          case _ => throw WrappedErrorException(Error(500, s"Unknown error. Server returned ${res.status}", "cluster.ambari.status.knox.genric"))
        }
      }
      .recoverWith {
        case ex: SSLException => throw WrappedErrorException(Error(500, "TLS error. Please ensure your Knox server has been configured with a correct keystore. If you are using self-signed certificates, you would need to get it added to Dataplane truststore.", "cluster.ambari.status.knox.tls-error"))
        case ex: ConnectException if ExceptionUtils.indexOfThrowable(ex, classOf[SSLException]) >= 0 => throw WrappedErrorException(Error(500, "TLS error. Please ensure your Knox server has been configured with a correct keystore. If you are using self-signed certificates, you would need to get it added to Dataplane truststore.", "cluster.ambari.status.knox.tls-error"))
        case ex: ConnectException => throw WrappedErrorException(Error(500, "Connection to remote address was refused.", "cluster.ambari.status.knox.connection-refused"))
      }
  }

}

