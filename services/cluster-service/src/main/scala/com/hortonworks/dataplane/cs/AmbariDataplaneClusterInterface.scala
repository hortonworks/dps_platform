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

import com.hortonworks.dataplane.commons.domain.Entities.{DataplaneCluster, HJwtToken}
import com.hortonworks.dataplane.cs.tls.SslContextManager
import com.hortonworks.dataplane.knox.Knox.{ApiCall, KnoxApiRequest, KnoxConfig}
import com.hortonworks.dataplane.knox.KnoxApiExecutor
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

sealed trait AmbariDataplaneClusterInterface {

  def discoverClusters()(implicit hJwtToken: Option[HJwtToken]): Future[Seq[String]]

  def getHdpVersion(implicit hJwtToken: Option[HJwtToken]): Future[Seq[String]]

  def getClusterDetails(clusterName:String)(implicit hJwtToken: Option[HJwtToken]):Future[Option[JsValue]]

  def getServiceInfo(clusterName:String, serviceName:String)(implicit hJwtToken: Option[HJwtToken]):Future[Option[JsValue]]

  def getServiceVersion(stack:String, stackVersion: String, serviceName:String)(implicit hJwtToken: Option[HJwtToken]):Future[String]

  def getServices(clusterName:String)(implicit hJwtToken: Option[HJwtToken]): Future[Seq[String]]

}

class AmbariDataplaneClusterInterfaceImpl(dataplaneCluster: DataplaneCluster,
                                          val ws: WSClient, // inject strict or loose instance instead of adding entire provider
                                          val config: Config,
                                          private val credentials: Credentials)
    extends AmbariDataplaneClusterInterface {

  val logger = Logger(classOf[AmbariDataplaneClusterInterfaceImpl])

  val tokenTopologyName = Try(config.getString("dp.services.knox.token.topology"))
    .getOrElse("token")

  val prefix = Try(config.getString("dp.service.ambari.cluster.api.prefix"))
    .getOrElse("/api/v1/clusters")

  val stackApiPrefix = Try(config.getString("dp.service.ambari.stack.api.prefix"))
    .getOrElse("/api/v1/stacks")

  /** On a registered dpCluster, discover the clusters
    * and start data fetch jobs for them
    *
    * @return List of Cluster names
    */
  override def discoverClusters()(implicit hJwtToken: Option[HJwtToken]): Future[Seq[String]] = {

    val url = s"${dataplaneCluster.ambariUrl}$prefix"
    val response = getAmbariResponse(url, dataplaneCluster.allowUntrusted)
    response.map { res =>
      val items = (res.json \ "items" \\ "Clusters").map(_.as[JsObject].validate[Map[String, String]].map(m => Some(m)).getOrElse(None))
      // each item is a Some(cluster)
      // start defining the cluster mapping
      val clusterOpts = items.map { item =>
        item.flatMap { _.get("cluster_name") }
      }
      clusterOpts.collect { case Some(c) => c }
    }
  }

  override def getHdpVersion(implicit hJwtToken: Option[HJwtToken]): Future[Seq[String]] = {
    val url = s"${dataplaneCluster.ambariUrl}$prefix"
    val response = getAmbariResponse(url, dataplaneCluster.allowUntrusted)

    response.map { res =>
      val items = (res.json \ "items" \\ "Clusters").map(_.as[JsObject].validate[Map[String, String]].map(m => Some(m)).getOrElse(None))
      val hdpVersion = items.map { item =>
        item.flatMap { map =>
          map.get("version")
        }
      }
      hdpVersion.collect { case Some(hdv) => hdv }
    }
  }

  def getAmbariResponse(requestUrl: String, allowUntrusted: Boolean)(implicit hJwtToken: Option[HJwtToken]): Future[WSResponse] = {
    val request = ws.url(requestUrl)
    val requestWithLocalAuth = request.withAuth(credentials.user.get, credentials.pass.get, WSAuthScheme.BASIC)
    val delegatedCall:ApiCall = {req => req.get()}

    if(dataplaneCluster.knoxEnabled.isDefined && dataplaneCluster.knoxEnabled.get && dataplaneCluster.knoxUrl.isDefined && hJwtToken.isDefined){
      KnoxApiExecutor(KnoxConfig(tokenTopologyName, dataplaneCluster.knoxUrl), ws).execute(
        KnoxApiRequest(request, delegatedCall, Some(hJwtToken.get.token)))
    } else requestWithLocalAuth.get()
  }

  override def getClusterDetails(clusterName:String)(implicit hJwtToken: Option[HJwtToken]):Future[Option[JsValue]] = {
    val url = s"${dataplaneCluster.ambariUrl}$prefix/$clusterName"
    val response = getAmbariResponse(url, dataplaneCluster.allowUntrusted)

    response.map { res =>
      (res.json \ "Clusters").toOption
    }.recoverWith {
      case e: Exception =>
        logger.warn(s"Cannot get security details for cluster $clusterName",e)
        Future.successful(None)
    }
  }

  override def getServiceInfo(clusterName: String, serviceName: String)(implicit hJwtToken: Option[HJwtToken]): Future[Option[JsValue]] = {
    val url = s"${dataplaneCluster.ambariUrl}$prefix/$clusterName/services/$serviceName"
    val response = getAmbariResponse(url, dataplaneCluster.allowUntrusted)

    response.map { res =>
      (res.json \ "ServiceInfo").toOption
    }.recoverWith {
      case e: Exception =>
        Future.successful(None)
    }
  }

  override def getServices(clusterName: String)(implicit hJwtToken: Option[HJwtToken]): Future[Seq[String]] = {
    val url = s"${dataplaneCluster.ambariUrl}$prefix/$clusterName/services"
    val response = getAmbariResponse(url, dataplaneCluster.allowUntrusted)

    response.map { res =>
      val items = (res.json \ "items" \\ "ServiceInfo").map(_.as[JsObject].validate[Map[String, String]].map(m => Some(m)).getOrElse(None))
      val serviceOpts = items.map { item =>
        item.flatMap { map =>
          map.get("service_name")
        }
      }
      serviceOpts.collect { case Some(s) => s }
    }
  }

  override def getServiceVersion(stack: String, stackVersion: String, serviceName: String)(implicit hJwtToken: Option[HJwtToken]):Future[String]  = {
    val url = s"${dataplaneCluster.ambariUrl}$stackApiPrefix/$stack/versions/$stackVersion/services/$serviceName"
    val response = getAmbariResponse(url, dataplaneCluster.allowUntrusted)

    response.map { res =>
      (res.json \ "StackServices" \ "service_version").validate[String].getOrElse("UNKNOWN")
    }
  }
}

object AmbariDataplaneClusterInterfaceImpl {
  def apply(dataplaneCluster: DataplaneCluster,
            ws: WSClient,
            config: Config, credentials: Credentials): AmbariDataplaneClusterInterfaceImpl =
    new AmbariDataplaneClusterInterfaceImpl(dataplaneCluster, ws, config,credentials)
}
