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

import com.hortonworks.dataplane.commons.domain.Entities.HJwtToken
import com.hortonworks.dataplane.cs.Webservice.ClusterClient
import com.typesafe.config.Config

import scala.concurrent.Future


class ClusterClientImpl(val config: Config)(implicit ws: ClusterWsClient)
  extends ClusterClient {

  import scala.concurrent.ExecutionContext.Implicits.global


  override def getKnoxToken(clusterId: String)(implicit token: Option[HJwtToken]): Future[KnoxData] = {
    ws.url(s"$url/clusters/$clusterId/knox_token")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get().flatMap { response =>
      response.status match {
        case 200 =>
          val token = (response.json \ "token").as[String]
          val knoxUrl = (response.json \ "knox_url").as[String]
          Future.successful(KnoxData(knoxUrl, token))
        case _ =>
          Future.failed(new Exception("Failed to fetch the knox information."))
      }
    }
  }
}
