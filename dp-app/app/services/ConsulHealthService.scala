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

import com.hortonworks.dataplane.commons.domain.Entities.ServiceHealth
import com.typesafe.config.Config
import play.api.libs.json.{JsObject, JsResult}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait HealthService {
  def getServiceHealth(serviceId: String) : Future[ServiceHealth]
}

class ConsulHealthService (config: Config)(implicit ws: WSClient) extends HealthService {

  private def consulHost = Option(config.getString("consul.host")).getOrElse("localhost")

  private def consulPort = Option(config.getString("consul.port")).getOrElse("8500")

  private val healthUrl = s"http://$consulHost:$consulPort/v1/health/checks"

  def getServiceHealth(serviceId: String): Future[ServiceHealth] = {
    ws.url(s"$healthUrl/$serviceId").get()
      .flatMap(res => {
        res.status match {
          case 200 => {
           val opt = res.json.as[List[JsObject]].headOption
            opt match {
              case None => Future.successful(ServiceHealth(Some(false), Some(false)))
              case Some(response) => {
                val status: JsResult[String] = (response \ "Status").validate[String]
                if (status.isSuccess && status.get.toLowerCase == "passing") {
                  Future.successful(ServiceHealth(Some(true), Some(true)))
                } else {
                  Future.successful(ServiceHealth(Some(true), Some(false)))
                }
              }
            }
          }
          case _ => Future.failed(new Exception("Failed to get service health from consul"))
        }
      })
  }
}

