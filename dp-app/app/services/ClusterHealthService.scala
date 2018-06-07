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

import javax.inject.{Inject, Singleton}

import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.domain.Ambari.{ClusterHost, NameNodeInfo}
import com.hortonworks.dataplane.commons.domain.Constants._
import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors}
import com.hortonworks.dataplane.db.Webservice.{ClusterComponentService, ClusterHostsService, DpClusterService}
import models.ClusterHealthData

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ClusterHealthService @Inject()(
    @Named("clusterHostsService") val clusterHostsService: ClusterHostsService,
    @Named("dpClusterService") val dpClusterService: DpClusterService,
    @Named("clusterComponentsService") val clusterComponentService: ClusterComponentService) {

  def getClusterHealthData(
                            clusterId: Long,dpClusterId: String): Future[Either[Errors,ClusterHealthData]]= {
    // get linked clusters
    val chd = for {
      namenode <- clusterComponentService.getServiceByName(clusterId, NAMENODE)
      hosts <- clusterHostsService.getHostsByCluster(clusterId)
      dataplaneCluster <- dpClusterService.retrieve(dpClusterId)
    } yield {
      val nn = namenode match {
        case Left(errors) => None
        case Right(namenode) => (namenode.properties.get \ "stats").validate[NameNodeInfo].asOpt
      }
      val hostsList =
        hosts.right.get.map(h => h.properties.get.validate[ClusterHost].get)
      val syncState = dataplaneCluster match {
        case Left(errors) => None
        case Right(dpCluster) => dpCluster.state
      }
      ClusterHealthData(nn, hostsList, syncState)
    }

    chd.map(x => Right(x)).recoverWith {
      case e: Exception =>
        Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
    }

  }

}
