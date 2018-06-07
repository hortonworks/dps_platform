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

import com.hortonworks.dataplane.commons.domain.Entities.Cluster
import com.hortonworks.dataplane.restmock.httpmock.when
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

class AmbariInterfaceSpec
    extends AsyncFlatSpec
    with Matchers
    with ServerSupport
    with BeforeAndAfterEach
    with BeforeAndAfterAll with ScalaFutures {

  logger.info("started local server at 9999")
  protected val stop = server.startOnPort(9999)

  val appConfig = ConfigFactory.parseString("""dp.services.endpoints {
                                              |
                                              |  #Very specific to namenode since there is no way of knowing the protocol with the endpoint value
                                              |  namenode = [{"name":"hdfs-site","properties":[{"protocol":"http","name":"dfs.namenode.http-address"},{"protocol":"https","name":"dfs.namenode.https-address"},{"protocol":"rpc","name":"dfs.namenode.rpc-address"}]}]
                                              |
                                              |  hdfs = [{"name":"core-site","properties":[{"protocol":"hdfs","name":"fs.defaultFS"}]}]
                                              |
                                              |  hive = [{"name":"hive-interactive-site","properties":[{"protocol":"TCP","name":"hive.server2.thrift.port"}]}]
                                              |
                                              |}""".stripMargin)

  "AmbariInterface" should "call the default cluster endpoint" in {
    when get ("/api/v1/clusters") withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") thenRespond (200, """{
                                                                                                          |  "href" : "http://172.22.72.9:8080/api/v1/clusters",
                                                                                                          |  "items" : [
                                                                                                          |    {
                                                                                                          |      "href" : "http://172.22.72.9:8080/api/v1/clusters/test",
                                                                                                          |      "Clusters" : {
                                                                                                          |        "cluster_name" : "test",
                                                                                                          |        "version" : "HDP-2.6"
                                                                                                          |      }
                                                                                                          |    }
                                                                                                          |  ]
                                                                                                          |}""")
    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "somecluster",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test")),
      Credentials(Some("admin"),Some("admin")), appConfig, ws)

    ambariInterface.ambariConnectionCheck.map { ac =>
      assert(ac.status)
      assert(ac.url.toString == "http://localhost:9999/api/v1/clusters/test")
    }

  }


  it should "discover atlas and its properties" in {

    val json  = Source.fromURL(getClass.getResource("/atlas.json")).mkString
    val atlasConfig = "/api/v1/clusters/test/configurations/service_config_versions"
    when get(atlasConfig) withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") withParams ("service_name"->"ATLAS","is_current" ->"true") thenRespond(200,json)

    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "test",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test")),
      Credentials(Some("admin"),Some("admin")), appConfig, ws)

    val atlas  = ambariInterface.getAtlas
    atlas.map { either =>
      assert(either.isRight)
      assert(Option(either.right.get.properties.toString).isDefined)
    }

  }

  it should "discover the namenode and its properties" in {

    val json  = Source.fromURL(getClass.getResource("/namenode.json")).mkString
    val hdfsJson  = Source.fromURL(getClass.getResource("/hdfs.json")).mkString
    val nnh  = Source.fromURL(getClass.getResource("/namenodehosts.json")).mkString
    val nameNodeConfig = "/api/v1/clusters/test/components/NAMENODE"
    when get nameNodeConfig withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") thenRespond(200,json)
    when get "/api/v1/clusters/test/configurations/service_config_versions" withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") withParams ("service_name"->"HDFS","is_current"->"true") thenRespond(200,hdfsJson)

    when get "/api/v1/clusters/test/host_components" withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") thenRespond(200,nnh)

    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "test",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test")),
      Credentials(Some("admin"),Some("admin")),appConfig, ws)

    val atlas  = ambariInterface.getNameNodeStats
    atlas.map { either =>
      assert(either.isRight)
      assert(either.right.get.serviceHost.size == 1)
      assert(either.right.get.props.isDefined)
      assert((either.right.get.props.get \ "stats").isDefined)
      assert((either.right.get.props.get \ "metrics").isDefined)
      assert(((either.right.get.props.get \ "metrics") \ "jvm").isDefined)
      //verify the first one
      assert(either.right.get.serviceHost(0).host == "yusaku-beacon-2.c.pramod-thangali.internal")
    }
  }


  it should "discover HDFS and its properties" in {

    val hdfsJson  = Source.fromURL(getClass.getResource("/hdfs.json")).mkString
    when get "/api/v1/clusters/test/configurations/service_config_versions" withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") withParams ("service_name"->"HDFS","is_current"->"true") thenRespond(200,hdfsJson)

    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "test",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test")),
      Credentials(Some("admin"),Some("admin")),appConfig, ws)

    val atlas  = ambariInterface.getHdfsInfo
    atlas.map { either =>
      assert(either.isRight)
      assert(either.right.get.serviceHost.isEmpty)
      assert(either.right.get.props.isDefined)
    }
  }


  it should "discover the hosts and diskinfo from the cluster" in {
    val json  = Source.fromURL(getClass.getResource("/hosts.json")).mkString
    val detailjson  = Source.fromURL(getClass.getResource("/host_name.json")).mkString
    val hostsConfig = "/api/v1/clusters/test/hosts"
    val hostConfig = "/api/v1/clusters/test/hosts/ashwin-dp-knox-test-1.novalocal"
    when get(hostsConfig) withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") thenRespond(200,json)
    when get(hostConfig) withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") thenRespond(200,detailjson)

    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "test",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test"))
      ,Credentials(Some("admin"),Some("admin")),appConfig, ws)

    val atlas  = ambariInterface.getGetHostInfo
    atlas.map { either =>
      assert(either.isRight)
      assert(either.right.get.size == 1)
      assert(either.right.get.head.properties.isDefined)
    }

  }

   it should "discover knox properties from the cluster" in {
    val json  = Source.fromURL(getClass.getResource("/knox.json")).mkString
    val url = "/api/v1/clusters/test/configurations/service_config_versions"
    when get url withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") withParams ("service_name"->"KNOX","is_current" ->"true") thenRespond(200,json)

    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "test",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test")),
      Credentials(Some("admin"),Some("admin")),appConfig, ws)

    val atlas  = ambariInterface.getKnoxInfo
    atlas.map { either =>
      assert(either.isRight)
    }

  }

  it should "discover beacon endpoints from the cluster" in {
    val beaconprops  = Source.fromURL(getClass.getResource("/beaconconfig.json")).mkString
    val beaconHosts  = Source.fromURL(getClass.getResource("/beaconhost.json")).mkString
    val url = "/api/v1/clusters/test/configurations/service_config_versions"
    when get url withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") withParams ("service_name"->"BEACON","is_current" ->"true") thenRespond(200,beaconprops)
    when get "/api/v1/clusters/test/host_components" withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") thenRespond(200,beaconHosts)

    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "test",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test")),
      Credentials(Some("admin"),Some("admin")),appConfig, ws)

    val beacon  = ambariInterface.getBeacon
    beacon.map { either =>
      assert(either.isRight)
    }
  }


  it should "discover hiveserver thrift endpoints from the cluster" in {
    val hiveconfig  = Source.fromURL(getClass.getResource("/hiveconfig.json")).mkString
    val hivehost  = Source.fromURL(getClass.getResource("/hivehosts.json")).mkString
    val url = "/api/v1/clusters/test/configurations/service_config_versions"
    when get url withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") withParams ("service_name"->"HIVE","is_current" ->"true") thenRespond(200,hiveconfig)
    when get "/api/v1/clusters/test/host_components" withHeaders ("Authorization" -> "Basic YWRtaW46YWRtaW4=") thenRespond(200,hivehost)

    val ws = AhcWSClient()
    val ambariInterface = new AmbariClusterInterface(
      Cluster(name = "test",
        clusterUrl = Some("http://localhost:9999/api/v1/clusters/test")),
      Credentials(Some("admin"),Some("admin")),appConfig, ws)

    val beacon  = ambariInterface.getHs2Info
    beacon.map { either =>
      assert(either.isRight)
      assert(either.right.get.props.isDefined)
      assert(either.right.get.serviceHost.size == 1)
    }
  }



  override def afterEach = {
    server.reset
  }

  override def afterAll = {
    stop()
  }

}
