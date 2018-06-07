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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.hortonworks.dataplane.commons.metrics.MetricsRegistry
import com.hortonworks.dataplane.cs.services.{AtlasService, RangerService}
import com.hortonworks.dataplane.cs.sync.DpClusterSync
import com.hortonworks.dataplane.cs.tls.SslContextManager
import com.hortonworks.dataplane.db.Webservice.{CertificateService, ClusterComponentService, ClusterHostsService, ClusterService, ConfigService, DpClusterService}
import com.hortonworks.dataplane.db.{CertificateServiceImpl, _}
import com.hortonworks.dataplane.http.routes.{DpProfilerRoute, _}
import com.hortonworks.dataplane.http.{ProxyServer, Webserver}
import com.typesafe.config.{Config, ConfigFactory}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient

import scala.util.Try

object AppModule extends AbstractModule {

  override def configure() = {
    bind(classOf[Config]).toInstance(ConfigFactory.load())
    bind(classOf[ActorSystem]).toInstance(ActorSystem("cluster-service"))
    bind(classOf[MetricsRegistry])
      .toInstance(MetricsRegistry("cluster-service"))
  }

  @Provides
  @Singleton
  def provideMaterializer(
      implicit actorSystem: ActorSystem): ActorMaterializer = {
    ActorMaterializer()
  }

  @Provides
  @Singleton
  def provideWsClient(implicit actorSystem: ActorSystem,
                      materializer: ActorMaterializer,
                      configuration: Config): WSClient = {

    val config = new DefaultAsyncHttpClientConfig.Builder()
      .setAcceptAnyCertificate(true)
      .setRequestTimeout(Try(configuration.getInt(
        "dp.services.ws.client.requestTimeout.mins") * 60 * 1000)
        .getOrElse(4 * 60 * 1000))
      .build
    AhcWSClient(config)
  }

  @Provides
  @Singleton
  def provideDpClusterService(implicit ws: WSClient,
                              configuration: Config): DpClusterService = {

    new DpClusterServiceImpl(configuration)
  }

  @Provides
  @Singleton
  def provideClusterDataService(
      implicit ws: WSClient,
      configuration: Config): ClusterComponentService = {
    new ClusterComponentServiceImpl(configuration)
  }

  @Provides
  @Singleton
  def provideClusterService(implicit ws: WSClient,
                            configuration: Config): ClusterService = {
    new ClusterServiceImpl(configuration)
  }

  @Provides
  @Singleton
  def provideConfigService(implicit ws: WSClient,
                           configuration: Config): ConfigService = {
    new ConfigServiceImpl(configuration)
  }

  @Provides
  @Singleton
  def provideClusterHostsService(implicit ws: WSClient,
                                 configuration: Config): ClusterHostsService = {
    new ClusterHostsServiceImpl(configuration)
  }

  @Provides
  @Singleton
  def provideAtlasApiData(actorSystem: ActorSystem,
                          materializer: ActorMaterializer,
                          credentialInterface: CredentialInterface,
                          clusterComponentService: ClusterComponentService,
                          clusterHostsService: ClusterHostsService,
                          dpClusterService: DpClusterService,
                          clusterService: ClusterService,
                          sslContextManager: SslContextManager,
                          config: Config): ClusterDataApi = {
    new ClusterDataApi(actorSystem,
                       materializer,
                       credentialInterface,
                       clusterComponentService,
                       clusterHostsService,
                       dpClusterService,
                       clusterService,
                       sslContextManager,
                       config)
  }

  @Provides
  @Singleton
  def provideAtlasService(ws: WSClient, config: Config): AtlasService = {

    implicit val knoxProxyWsClient: KnoxProxyWsClient = KnoxProxyWsClient(ws, config)
    new AtlasService(config)
  }

  @Provides
  @Singleton
  def provideAtlasRoute(atlasService: AtlasService, clusterDataApi: ClusterDataApi, credentialInterface: CredentialInterface): AtlasRoute = {
    new AtlasRoute(atlasService, clusterDataApi, credentialInterface)
  }

  @Provides
  @Singleton
  def provideClusterRegistrationRoute(storageInterface: StorageInterface,
                                      credentialInterface: CredentialInterface,
                                      config: Config,
                                      clusterSync: ClusterSync,
                                      dpClusterSync: DpClusterSync,
                                      metricsRegistry: MetricsRegistry,
                                      sslContextManager: SslContextManager): ClusterRegistrationRoute = {
    new ClusterRegistrationRoute(storageInterface,
                    credentialInterface,
                    config,
                    clusterSync,
                    dpClusterSync,
                    metricsRegistry,
                    sslContextManager)
  }

  @Provides
  @Singleton
  def provideAmbariRoute(storageInterface: StorageInterface,
                         credentialInterface: CredentialInterface,
                         config: Config,
                         clusterService: ClusterService,
                         dpClusterService: DpClusterService,
                         sslContextManager: SslContextManager): AmbariRoute = {
    new AmbariRoute(storageInterface,
                    clusterService,
                    credentialInterface,
                    dpClusterService,
                    config,
                    sslContextManager)
  }

  @Provides
  @Singleton
  def provideHdpProxyRoute(actorSystem: ActorSystem,
                            actorMaterializer: ActorMaterializer,
                            clusterData: ClusterDataApi,
                            sslContextManager: SslContextManager,
                            config: Config): HdpRoute = {
    new HdpRoute(actorSystem,
      actorMaterializer,
      clusterData,
      sslContextManager: SslContextManager,
      config)
  }

  @Provides
  @Singleton
  def provideConfigurationRoute(config: Config, sslContextManager: SslContextManager): ConfigurationRoute = {
    new ConfigurationRoute(
      config,
      sslContextManager
    )
  }

  @Provides
  @Singleton
  def provideSslContextManager(config: Config, dpClusterService: DpClusterService, certificateService: CertificateService, materializer: ActorMaterializer, actorSystem: ActorSystem): SslContextManager = {
    new SslContextManager(
      config,
      dpClusterService,
      certificateService,
      materializer,
      actorSystem
    )
  }

  @Provides
  @Singleton
  def provideCertificateService(implicit ws: WSClient, config: Config): CertificateService = {
    new CertificateServiceImpl(config)
  }

  @Provides
  @Singleton
  def provideHdpProxyServer(actorSystem: ActorSystem,
                            materializer: ActorMaterializer,
                            config: Config,
                            hdpRoute: HdpRoute): ProxyServer = {
    new ProxyServer(actorSystem, materializer, config, hdpRoute.proxy)
  }

  @Provides
  @Singleton
  def provideDpProfileRoute(storageInterface: StorageInterface,
                            credentialInterface: CredentialInterface,
                            clusterComponentService: ClusterComponentService,
                            clusterHostsService: ClusterHostsService,
                            clusterDataApi: ClusterDataApi,
                            config: Config,
                            ws: WSClient): DpProfilerRoute = {

    implicit val knoxProxyWsClient: KnoxProxyWsClient = KnoxProxyWsClient(ws, config)
    new DpProfilerRoute(clusterComponentService,
                        clusterHostsService,
                        storageInterface,
                        clusterDataApi,
                        config)
  }

  @Provides
  @Singleton
  def provideRangerRoute(rangerService: RangerService, clusterDataApi: ClusterDataApi, credentialInterface: CredentialInterface): RangerRoute = {
    new RangerRoute(rangerService, clusterDataApi, credentialInterface)
  }

  @Provides
  @Singleton
  def provideRangerService(config: Config, ws: WSClient): RangerService = {

    implicit val knoxProxyWsClient: KnoxProxyWsClient = KnoxProxyWsClient(ws, config)
    new RangerService(config)
  }

  @Provides
  @Singleton
  def provideWebservice(actorSystem: ActorSystem,
                        materializer: ActorMaterializer,
                        configuration: Config,
                        atlasRoute: AtlasRoute,
                        rangerRoute: RangerRoute,
                        dpProfilerRoute: DpProfilerRoute,
                        crr: ClusterRegistrationRoute,
                        ambariRoute: AmbariRoute,
                        configurationRoute: ConfigurationRoute): Webserver = {
    import akka.http.scaladsl.server.Directives._
    new Webserver(
      actorSystem,
      materializer,
      configuration,
      rangerRoute.rangerAudit ~
        rangerRoute.rangerPolicy ~
        dpProfilerRoute.startJob ~
        dpProfilerRoute.jobStatus ~
        dpProfilerRoute.jobDelete ~
        dpProfilerRoute.startAndScheduleJob ~
        dpProfilerRoute.datasetAssetMapping ~
        dpProfilerRoute.datasetProfiledAssetCount ~
        dpProfilerRoute.profilersLastRunInfoOnAsset ~
        dpProfilerRoute.getExistingProfiledAssetCount ~
        dpProfilerRoute.scheduleInfo ~
        dpProfilerRoute.auditResults ~
        dpProfilerRoute.auditActions ~
        dpProfilerRoute.profilerMetrics ~
        dpProfilerRoute.getProfilersStatusWithJobSummary ~
        dpProfilerRoute.getProfilersStatusWithAssetsCount ~
        dpProfilerRoute.getProfilersJobsStatus ~
        dpProfilerRoute.putProfilerState ~
        dpProfilerRoute.getProfilersHistories ~
        dpProfilerRoute.postAssetColumnClassifications ~
        dpProfilerRoute.getProfilerInstanceByName ~
        dpProfilerRoute.updateProfilerInstance ~
        atlasRoute.hiveAttributes ~
        atlasRoute.hiveTables ~
        atlasRoute.atlasEntities ~
        atlasRoute.atlasEntity ~
        atlasRoute.atlasLineage ~
        atlasRoute.atlasTypeDefs ~
        atlasRoute.postEntityClassifications ~
        crr.route ~
        crr.sync ~
        crr.health ~
        crr.metrics ~
        ambariRoute.route ~
        ambariRoute.configRoute ~
        ambariRoute.serviceStateRoute ~
        ambariRoute.ambariClusterProxy ~
        ambariRoute.ambariGenericProxy ~
        configurationRoute.reloadCertificates
    )
  }

  @Provides
  @Singleton
  def provideStorageInterface(
      dpClusterService: DpClusterService,
      clusterService: ClusterService,
      clusterComponentService: ClusterComponentService,
      clusterHostsServiceImpl: ClusterHostsService,
      configService: ConfigService): StorageInterface = {
    new StorageInterfaceImpl(clusterService,
                             dpClusterService,
                             clusterComponentService,
                             clusterHostsServiceImpl,
                             configService)
  }

  @Provides
  @Singleton
  def provideCredentialInterface(config: Config): CredentialInterface = {
    new CredentialInterfaceImpl(config)
  }

  @Provides
  @Singleton
  def provideClusterSync(actorSystem: ActorSystem,
                         config: Config,
                         clusterInterface: StorageInterface,
                         sslContextManager: SslContextManager): ClusterSync = {
    new ClusterSync(actorSystem, config, clusterInterface, sslContextManager)
  }

  @Provides
  @Singleton
  def provideDpClusterSync(actorSystem: ActorSystem,
                           config: Config,
                           clusterInterface: StorageInterface,
                           credentialInterface: CredentialInterface,
                           dpClusterService: DpClusterService,
                           sslContextManager: SslContextManager): DpClusterSync = {
    new DpClusterSync(actorSystem,
                      config,
                      clusterInterface,
                      credentialInterface,
                      dpClusterService,
                      sslContextManager)
  }

}
