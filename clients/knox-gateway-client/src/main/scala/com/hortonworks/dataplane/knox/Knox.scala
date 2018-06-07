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

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.libs.ws.{WSRequest, WSResponse}

import scala.concurrent.Future

object Knox {

  type ApiCall = (WSRequest) => Future[WSResponse]

  /**
    * @param request - The WS request to wrap the knox token with
    * @param executor - A function which applies this request - usually a GET,POST,PUT etc call
    * @param token - The jwt token from knox
    */
  case class KnoxApiRequest(request:WSRequest,executor: ApiCall, token: Option[String])

  /**
    *
    * @param tokenTopologyName - The name of the topology
    * @param knoxUrl - knox URL
    */
  case class KnoxConfig(tokenTopologyName: String,
                        knoxUrl: Option[String],
                        knoxCookieTokenName: String = "hadoop-jwt") {

    def tokenUrl =
      s"${knoxUrl.get}/$tokenTopologyName/knoxtoken/api/v1/token"
  }

  case class TokenResponse(accessToken: String,
                           targetUrl: Option[String],
                           tokenType: Option[String],
                           expires: Long)

  object TokenResponse {

    implicit val tokenResponseReads: Reads[TokenResponse] = (
      (JsPath \ "access_token").read[String] and
        (JsPath \ "target_url").readNullable[String] and
        (JsPath \ "token_type").readNullable[String] and
        (JsPath \ "expires_in").read[Long]
    )(TokenResponse.apply _)

  }

}
