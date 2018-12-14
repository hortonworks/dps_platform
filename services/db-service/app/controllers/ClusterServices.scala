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

package controllers

import javax.inject._
import domain.API.clusters
import domain.{ClusterServiceHostsRepo, ClusterServiceRepo}
import com.hortonworks.dataplane.commons.domain.Entities.{ClusterService, ClusterServiceHost, Error, WrappedErrorException}
import com.hortonworks.dataplane.commons.domain.Ambari.{ClusterServiceWithConfigs, ConfigurationInfo}
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClusterServices @Inject()(
    csr: ClusterServiceRepo,
    cse: ClusterServiceHostsRepo)(implicit exec: ExecutionContext)
    extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def allWithCluster(clusterId: Long, serviceName: Option[String]) = Action.async {
    (serviceName match {
      case Some(serviceName) => csr.findByNameAndCluster(serviceName, clusterId).map(_.map(List(_)).getOrElse(Nil))
      case None => csr.allWithCluster(clusterId)
    })
      .map(cs => success(cs.map(c => linkData(c, makeClusterLink(c)))))
      .recoverWith(apiError)
  }

  def allWithDpCluster(dpClusterId: Long) = Action.async {
    csr
      .allWithDpCluster(dpClusterId)
      .map(cs => success(cs.map(c => linkData(c, makeClusterLink(c)))))
      .recoverWith(apiError)
  }


  private def makeClusterLink(c: ClusterService) = {
    Map("cluster" -> s"$clusters/${c.clusterId.get}")
  }

  private def makServiceLink(e: ClusterServiceHost, clusterId: Long) = {
    Map("cluster" -> s"$clusters/$clusterId")
  }

  def load(serviceId: Long) = Action.async {
    csr
      .findById(serviceId)
      .map { co =>
        co.map { c =>
            success(linkData(c, makeClusterLink(c)))
          }
          .getOrElse(NotFound)
      }
      .recoverWith(apiError)
  }

  def delete(serviceId: Long) = Action.async { req =>
    val future = csr.deleteById(serviceId)
    future.map(i => success(i)).recoverWith(apiError)
  }

  def loadWithClusterAndName(clusterId: Long, serviceName: String) =
    Action.async {
      csr
        .findByNameAndCluster(serviceName, clusterId)
        .map(_.map(c => success(linkData(c, makeClusterLink(c)))).getOrElse(notFound))
        .recoverWith(apiError)
    }


  def loadWithCluster(serviceId: Long, clusterId: Long) =
    Action.async(parse.json) { req =>
      csr
        .findByIdAndCluster(serviceId, clusterId)
        .map(cs => success(cs.map(c => linkData(c, makeClusterLink(c)))))
        .recoverWith(apiError)
    }


  def addWithCluster = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterService]
      .map { cl =>
        //        check if cluster is not null and dp-cluster is null
        if (cl.clusterId.isEmpty)
          Future.successful(UnprocessableEntity)
        else {
          val created = csr.insert(cl)
          created
            .map(c => success(linkData(c, makeClusterLink(c))))
            .recoverWith(apiError)
        }
      }
      .getOrElse(Future.successful(BadRequest))

  }

  def updateWithCluster = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterService]
      .map { cl =>
        //        check if cluster is not null and dp-cluster is null
        if (cl.clusterId.isEmpty)
          Future.successful(UnprocessableEntity)
        else {
          val created = csr.updateByName(cl)
          created
            .map(c => success(Map("updated" -> c)))
            .recoverWith(apiError)
        }
      }
      .getOrElse(Future.successful(BadRequest))

  }

  def addWithDpCluster = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterService]
      .map { cl =>
        //        check if cluster is not null and dp-cluster is null
        if (cl.clusterId.isDefined)
          Future.successful(UnprocessableEntity)
        else {
          val created = csr.insert(cl)
          created
            .map(c => success(linkData(c, makeClusterLink(c))))
            .recoverWith(apiError)
        }
      }
      .getOrElse(Future.successful(BadRequest))

  }

  def getEndpoints(clusterId: Long, serviceId: Long) = Action.async {
    cse
      .allByClusterAndService(clusterId, serviceId)
      .map(cs =>
        success(cs.map(c => linkData(c, makServiceLink(c, clusterId)))))
      .recoverWith(apiError)
  }

  def   getServiceEndpoints(serviceId: Long) = Action.async {
    cse
      .allByService(serviceId)
      .map(cs => cs match {
          case Some(c) => {
            val properties : Option[ConfigurationInfo] =  c._1.properties match {
              case Some(value) => Some(value.validate[ConfigurationInfo].get)
              case None => None
            }
            success(linkData(ClusterServiceWithConfigs(c._1.id, c._1.serviceName, c._1.clusterId, c._2.host, properties), Map()))
          }
          case None => NotFound
        })
      .recoverWith(apiError)
  }


  def getAllServiceEndpoints(serviceName: String) = Action.async {
    cse
      .allByServiceName(serviceName)
      .map(cs => success(cs.map(c => {
          val properties : Option[ConfigurationInfo] =  c._1.properties match {
            case Some(value) => Some(value.validate[ConfigurationInfo].get)
            case None => None
          }
          linkData(ClusterServiceWithConfigs(c._1.id, c._1.serviceName, c._1.clusterId, c._2.host, properties), Map())
      }))).recoverWith(apiError)
  }

  def addServiceEndpoint = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterServiceHost]
      .map { ce =>
        // check if cluster is not null and dp-cluster is null
        if (ce.serviceid.isEmpty) {
          Future.successful(UnprocessableEntity)
        } else {
          val created = cse.insert(ce)
          created
            .map(c => success(linkData(c)))
            .recoverWith(apiError)
        }
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def updateServiceEndpoint = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterServiceHost]
      .map { ce =>
        // check if cluster is not null and dp-cluster is null
        if (ce.serviceid.isEmpty) {
          Future.successful(UnprocessableEntity)
        } else {
          val created = cse.updateOrInsert(ce)
          created
            .map(c => success(linkData(c)))
            .recoverWith(apiError)
        }
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def upsertWithClusterIdAndServiceName(clusterId: String, serviceName: String) = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterService]
      .map { cl =>
        csr.upsertByName(clusterId.toLong, serviceName, cl)
          .map(cs => success(cs))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest(Json.toJson(Error(400, "Malformed body.", "db.generic")))))
  }

}
