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

import javax.inject.{Inject, Named}
import com.google.inject.Singleton
import com.hortonworks.dataplane.commons.domain.Entities._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.cs.Webservice.AmbariWebService
import models.WrappedErrorsException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.{Configuration, Logger}

@Singleton
class AmbariService @Inject()(
    @Named("clusterAmbariService") ambariWebService: AmbariWebService,
    private val wSClient: WSClient,
    private val configuration: Configuration) {

  import com.hortonworks.dataplane.commons.domain.Ambari._

  private def clusterService =
    Option(System.getProperty("dp.services.cluster.service.uri"))
      .getOrElse(
        configuration.underlying.getString("dp.services.cluster.service.uri"))

  def statusCheck(url: String, allowUntrusted: Boolean, behindGateway: Boolean)(implicit hJwtToken: Option[HJwtToken]): Future[Either[Errors,AmbariCheckResponse]] = {
    ambariWebService.checkAmbariStatus(url, allowUntrusted, behindGateway)
  }

  def getClusterServices(dpcwServices: DpClusterWithDpServices)(implicit token:Option[HJwtToken]):Future[Either[Errors,Seq[ServiceInfo]]] = {
    ambariWebService.getAmbariServicesInfo(dpcwServices)
  }
  def getClusterDetails(ambariDetailRequest: AmbariDetailRequest)(implicit hJwtToken: Option[HJwtToken]) = {
    ambariWebService.getAmbariDetails(ambariDetailRequest)
  }

  def syncCluster(dpCluster: DataplaneClusterIdentifier)(implicit hJwtToken: Option[HJwtToken]): Future[Boolean] = {
   ambariWebService.syncAmbari(dpCluster).map {
     case value@true =>
       Logger.info(s"Successfully synced datalake with ${dpCluster.id}")
       value
     case value@false =>
       Logger.error(s"Cannot sync datalake with ${dpCluster.id}")
       value
   }
  }

  def retrieveAmbariVersion(clusterId: Long, token: Option[HJwtToken]): Future[String] = {
    implicit val hJwtToken = token;

    ambariWebService.requestAmbariApi(clusterId, "services/AMBARI/components/AMBARI_SERVER")
      .map {
        case Left(errors) => throw WrappedErrorsException(errors)
        case Right(response) => response
      }
      .flatMap { response =>
        (response \ "RootServiceComponents" \ "component_version")
          .asOpt[String]
          .map(Future.successful(_))
          .getOrElse(Future.failed(WrappedErrorException(Error(500, "Unable to parse response from Ambari", "core.parse.ambari-response"))))
      }
  }

}
