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

import com.hortonworks.dataplane.knox.Knox.TokenResponse
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.Json

class TokenValidationSpec extends FlatSpec with Matchers {

  "Token Parser" should "parse a valid token with all fields" in {

    val token =
      """{"access_token":"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbjEiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNTA3NjE4OTczfQ.CokjgkVnKR_lLCzTmB9LWvZjtRzKOXlwRySXA7zwuRtVuWXj7oMCrnpVZ0eWCzvlusZTG1KebZS06VYRC_t85PbVUTS_DKpQdvECb58nccUDiPQkrF62A-8T972FhhhV8GZDOJ5mwzzAmqmzmwpjhIKtZq2FLwBDMzyTlNqY6GM","cookie.name":"hadoop-jwt","target_url":"https://ctr-e134-1499953498516-209927-01-000005.hwx.site:8443/gateway/tokenbased","token_type":"Bearer ","expires_in":1507618973524}"""

    import Knox.TokenResponse._

    Json
      .parse(token)
      .validate[TokenResponse]
      .map { t =>
        assert(
          t.targetUrl.get == "https://ctr-e134-1499953498516-209927-01-000005.hwx.site:8443/gateway/tokenbased")
      }
      .getOrElse {
        fail()
      }
  }

  it should "parse null fields into an Option type" in {

    val token =
      """{"access_token":"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbjEiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNTA3NjE4NDEzfQ.O_8wD7f4Y08JxSv5DuWSjK8rIK3Zhep_oHTXj2huaLiCw_oohMUTbedw_hBU5qvFbbAWMVPD0AM2w3Q5PrJDESmxER8WR-FlrnwfLSH_8-CLZiTgHy8cyZ3jB7QsnJns839WhE7M5u4V7GnQys5cScgtF3GG8gE8j29rarzKVTE","cookie.name":"hadoop-jwt","token_type":"Bearer ","expires_in":1507618413641}"""

    import Knox.TokenResponse._

    Json
      .parse(token)
      .validate[TokenResponse]
      .map { t =>
        assert(t.targetUrl.isEmpty)
        assert(t.tokenType.get == "Bearer ")
        assert(t.expires == 1507618413641L)
      }
      .getOrElse {
        fail()
      }

  }

}
