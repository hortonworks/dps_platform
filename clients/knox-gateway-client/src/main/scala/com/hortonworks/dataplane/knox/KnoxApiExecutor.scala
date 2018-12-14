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

import Knox.{
  KnoxApiRequest,
  KnoxConfig,
  TokenResponse
}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait KnoxApiExecutor {
  val config: KnoxConfig
  val wSClient: WSClient

  def getKnoxApiToken(token: String):Future[TokenResponse]

  protected def wrapTokenIfUnwrapped(token: String): String =
    if (token.startsWith("hadoop-jwt")) token
    else s"${config.knoxCookieTokenName}=$token"

   protected final def makeApiCall(tokenResponse: TokenResponse,
                  knoxApiRequest: KnoxApiRequest) = {
    val wSRequest = knoxApiRequest.request.withHeaders(
      ("Cookie", wrapTokenIfUnwrapped(tokenResponse.accessToken)))
    // issue the call
    knoxApiRequest.executor.apply(wSRequest)
  }

  def execute(knoxApiRequest: KnoxApiRequest): Future[WSResponse] = {

    for {
      // First get the token
      tokenResponse <- getKnoxApiToken(wrapTokenIfUnwrapped(knoxApiRequest.token.get))
      // Use token to issue the complete request
      response <- makeApiCall(tokenResponse, knoxApiRequest)
    } yield response

  }


}

object KnoxApiExecutor {
  def apply(c: KnoxConfig, w: WSClient) = new DefaultKnoxApiExecutor(c,w)
  def withExceptionHandling(c: KnoxConfig, w: WSClient) = new BasicKnoxApiExecutor(c, w)
  def withTokenCaching(c: KnoxConfig, w: WSClient) = new TokenCachingKnoxApiExecutor(c,w)
  def withTokenDisabled(c: KnoxConfig, w: WSClient) = new TokenDisabledKnoxApiExecutor(c,w)
}
