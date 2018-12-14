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

  def getServiceHealth(serviceName: String): Future[ServiceHealth] = {
    // 1. get all nodes for the service which are in passing state
    ws.url(s"http://$consulHost:$consulPort/v1/health/service/$serviceName?passing=true")
      .get()
      .flatMap { res =>
        res.status match {
          case 200 => Future.successful(res.json.as[Seq[JsObject]].map(instance => (instance \ "Node" \ "Node").as[String]))
          case _ => Future.failed(new Exception("Failed to get service health from consul"))
        }
      }
      .flatMap { nodes =>
        // 2. get all service checks for the service
        ws.url(s"http://$consulHost:$consulPort/v1/health/checks/$serviceName").get()
          .flatMap { res =>
            res.status match {
              // filter out checks on nodes which are unavilable by checking if check's node exists in passing nodes
              case 200 => Future.successful(res.json.as[Seq[JsObject]].filter(cService => nodes.contains((cService \ "Node").as[String])))
              case _ => Future.failed(new Exception("Failed to get service health from consul"))
            }
          }
      }
      .map { services =>

        val installedServices = services
        val healthyServices = services.filter { cServiceStatus =>
          (cServiceStatus \ "Status")
            .asOpt[String]
            .map(_.toLowerCase == "passing")
            .getOrElse(false)
        }

        ServiceHealth(installed = installedServices.nonEmpty, healthy = healthyServices.nonEmpty)
      }
  }
}

