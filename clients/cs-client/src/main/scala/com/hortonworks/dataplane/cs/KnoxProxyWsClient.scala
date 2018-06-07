
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

package com.hortonworks.dataplane.cs


import java.net.URL

import com.hortonworks.dataplane.commons.domain.Constants
import com.hortonworks.dataplane.commons.domain.Entities.HJwtToken
import com.typesafe.config.Config
import play.api.libs.ws._


case class KnoxProxyWsRequest(private val request: WSRequest, private val fallback: String) {

  def withHeaders(token: Option[HJwtToken]): WSRequest = {
    val wsRequest = token match {
      case Some(jwtToken) =>
        request.withHeaders(Constants.DPTOKEN -> jwtToken.token)
      case None =>  request
    }
    wsRequest.withHeaders(Constants.SERVICE_ENDPOINT ->  fallback)
  }

  def withToken(token: Option[String]): WSRequest = {
    val wsRequest = token match {
      case Some(token) => request.withHeaders(Constants.DPTOKEN -> token)
      case None =>  request
    }
    wsRequest.withHeaders(Constants.SERVICE_ENDPOINT ->  fallback)
  }

}

case class KnoxProxyWsClient(wrappedClient: WSClient, config: Config) {
  private def proxyUrl =
    Option(System.getProperty("dp.services.hdp_proxy.service.uri"))
      .getOrElse(config.getString("dp.services.hdp_proxy.service.uri"))

  def url(urlString: String, clusterId: Long, serviceName: String): KnoxProxyWsRequest = {
    val url = new URL(urlString)
    val fallbackEndpoint = s"${url.getProtocol}://${url.getAuthority}"
    val fallbackPath = url.getFile
    val req = wrappedClient.url(s"$proxyUrl/cluster/$clusterId/service/${serviceName.toLowerCase}$fallbackPath")

    KnoxProxyWsRequest(req, fallbackEndpoint)
  }
}





