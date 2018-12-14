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

import com.hortonworks.dataplane.commons.domain.Entities.HJwtToken
import play.api.libs.json.JsValue

import scala.concurrent.Future

private[dataplane] case class Kerberos(user: String, ticket: String)

private[dataplane] case class AmbariConnection(
    status: Boolean,
    url: URL,
    kerberos: Option[Kerberos] = None,
    connectionError: Option[Throwable] = None)

private[dataplane] case class ServiceHost(host: String)

private[dataplane] case class Atlas(properties: String)

private[dataplane] case class ServiceInfo(serviceHost: Seq[ServiceHost] = Seq(),
                                         props: Option[JsValue])

private[dataplane] case class Ranger(serviceHost: Seq[ServiceHost] = Seq(),
                                     props: Option[JsValue])

private[dataplane] case class DpProfiler(serviceHost: Seq[ServiceHost] = Seq(),
                                     props: Option[JsValue])

private[dataplane] case class NameNode(serviceHost: Seq[ServiceHost] = Seq(),
                                       props: Option[JsValue])

private[dataplane] case class Hdfs(serviceHost: Seq[ServiceHost] = Seq(),
                                   props: Option[JsValue])

private[dataplane] case class HiveServer(serviceHost: Seq[ServiceHost] = Seq(),
                                         props: Option[JsValue])

private[dataplane] case class HostInformation(hostState: String,
                                              hostStatus: String,
                                              name: String,
                                              ip: String,
                                              properties: Option[JsValue])

private[dataplane] case class KnoxInfo(properties: Option[JsValue])

private[dataplane] case class BeaconInfo(properties: Option[JsValue],
                                         endpoints: Seq[ServiceHost])

private[dataplane] case class Credentials(user: Option[String],
                                          pass: Option[String])


trait AmbariInterfaceV2 {

  def getNameNodeStats(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, NameNode]]

  def getHdfsInfo(
      implicit hJwtToken: Option[HJwtToken]): Future[Either[Throwable, Hdfs]]

  def getGetHostInfo(implicit hJwtToken: Option[HJwtToken])
    : Future[Either[Throwable, Seq[HostInformation]]]

  def getServiceInformation(service: String, component: String)(implicit hJwtToken: Option[HJwtToken]): Future[ServiceInfo]

}
