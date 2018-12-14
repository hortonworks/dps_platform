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

import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.hortonworks.dataplane.knox.Knox.{KnoxApiRequest, KnoxConfig, TokenResponse}
import com.typesafe.scalalogging.Logger
import org.joda.time.DateTime
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TokenCachingKnoxApiExecutor(c: KnoxConfig, w: WSClient) extends KnoxApiExecutor{

  val logger = Logger(classOf[TokenCachingKnoxApiExecutor])

  override val wSClient: WSClient = w
  override val config: KnoxConfig = c
  val tokenUrl = config.tokenUrl
  private val expiry = 600

  private lazy val evictOnFailure: String => Unit = { key: String =>
    logger.error(s"evicting K -> $key,V -> ${tokenCache.get(key)}")
    tokenCache.invalidate(key)
  }
  val tokenCache = CacheBuilder.newBuilder().expireAfterWrite(expiry,TimeUnit.SECONDS).build(new TokenCacheLoader(evictOnFailure))


  def getKnoxApiToken(token: String):Future[TokenResponse] = {
    logger.info("Loading token from cache")
    tokenCache.get(token).flatMap{ tr =>
      logger.info(s"Access token loaded and expires at - ${new DateTime(tr.expires)}")
      // check if expired, clear map and retry
      if(tr.expires <= new DateTime().toInstant.getMillis){
        logger.info(s"Access token expired and will be reloaded - ${new DateTime(tr.expires)}")
        tokenCache.invalidate(token)
        tokenCache.get(token)
      } else Future.successful(tr)
    }
  }


   override def execute(knoxApiRequest: KnoxApiRequest): Future[WSResponse] = {
     logger.info("Attempting to call the delegate through Knox")
    val response =  callThroughKnox(knoxApiRequest)
    // The token May expire in the time between a cache load and the actual request is made
    // In this case verify a 403 response and retry
     response.flatMap { res =>
        if(res.status == 403){
          logger.info("Service retured a 403, token may have expired; Retrying")
          // try again
          callThroughKnox(knoxApiRequest)
        } else Future.successful(res)

     }
  }


  private def callThroughKnox(knoxApiRequest: KnoxApiRequest) = {
    for {
    // First get the token
      tokenResponse <- getKnoxApiToken(
        wrapTokenIfUnwrapped(knoxApiRequest.token.get))
      // Use token to issue the complete request
      response <- makeApiCall(tokenResponse, knoxApiRequest)
    } yield response
  }

  class TokenCacheLoader(evict:String => Unit) extends CacheLoader[String,Future[TokenResponse]]{
    override def load(token: String): Future[TokenResponse] = {
      logger.info("Cache will load token from Knox")
      val response = wSClient
        .url(tokenUrl)
        .withHeaders("Cookie" -> token,"Content-Type" -> "application/json","Accept" -> "application/json")
        .get()
        .map { res =>
          res.json.validate[TokenResponse].get
        }

       // make sure the result is available
       val result = for {
         res <- response
         tokenResult <- Future.successful(res)
       } yield tokenResult

      result.onFailure {
        case e:Throwable =>
          logger.error("Cannot load knox URL", e)
          evict(token)
      }

      result
    }
  }


}

