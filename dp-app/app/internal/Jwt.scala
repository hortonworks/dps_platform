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

package internal

import java.util.Date

import io.jsonwebtoken.impl.crypto.MacProvider
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.util.Try
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import org.apache.commons.codec.binary.Base64

object Jwt {

  val algorithm = SignatureAlgorithm.HS256
  val signingKey = "aPdSgVkYp3s6v9y$B&E)H@McQeThWmZq4t7w!z%C*F-JaNdRgUjXn2r5u8x/A?D("
  val issuer: String = "data_plane"
  val HOUR = 3600 * 1000


  def makeJWT(user: User): String = {
    val nowMillis = System.currentTimeMillis()
    val now = new Date(nowMillis)
    val claims = new java.util.HashMap[String, Object]()
    claims.put("user", Json.toJson(user).toString())

    val builder = Jwts.builder()
      .setIssuedAt(now)
      .setIssuer(issuer)
      .setClaims(claims)
      .setExpiration(new Date(now.getTime + 2 * HOUR))
      .signWith(algorithm, Base64.encodeBase64String(signingKey.getBytes()))
    builder.compact()

  }


  def parseJWT(jwt: String): Option[User] = {
    Logger.info("Parsing user authorization token")

    val claims = Try(Some(Jwts.parser()
      .setSigningKey(Base64.encodeBase64String(signingKey.getBytes()))
      .parseClaimsJws(jwt).getBody())) getOrElse None

    Logger.info(s"Checking if token claims are defined -  ${claims.isDefined}")

    claims.map { c =>
      val expiration: Date = c.getExpiration()
      if (expiration.before(new Date()))
        None
      val userJsonString = c.get("user").toString
      Json.parse(userJsonString).validate[User] match {
        case JsSuccess(user, _) => Some(user)
        case JsError(error) => None
      }
    } getOrElse None

  }


}
