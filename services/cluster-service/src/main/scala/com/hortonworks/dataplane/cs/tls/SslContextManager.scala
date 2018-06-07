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

package com.hortonworks.dataplane.cs.tls

import javax.inject.Inject
import javax.net.ssl.{KeyManager, SSLContext, X509TrustManager}
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.after
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
import com.hortonworks.dataplane.commons.domain.Entities.{DataplaneCluster, WrappedErrorException}
import com.hortonworks.dataplane.db.Webservice.{CertificateService, DpClusterService}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.{ConfigSSLContextBuilder, DefaultKeyManagerFactoryWrapper, DefaultTrustManagerFactoryWrapper, SSLConfigSettings, SSLLooseConfig, TrustManagerConfig, TrustStoreConfig}
import com.typesafe.sslconfig.util.NoopLogger
import io.netty.handler.ssl.{ClientAuth, JdkSslContext}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.WSClient

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try


class SslContextManager @Inject()(val config: Config, val dpClusterService: DpClusterService, certificateService: CertificateService, val materializer: Materializer, val actorSystem: ActorSystem) {

  private lazy val log = Logger(classOf[SslContextManager])

  implicit val actorSystemImplicit = actorSystem

  val timeout = Duration(Try(config.getString("dp.certificate.query.timeout")).getOrElse("4 minutes"))

  import scala.collection.JavaConverters._
  val keyBlacklist = Try(config.getStringList("dp.certificate.algorithm.blacklist.key").asScala).getOrElse(Nil)

  val home = System.getProperty("java.home")
  val system = TrustStoreConfig(data=None, filePath = Some(s"$home/lib/security/cacerts"))

  private lazy val loose = buildLoose()
  private lazy val strict = new AtomicReference[SSLConfigSettings]()

  private lazy val looseHttpsContext = buildLooseHttpsContext()
  private lazy val strictHttpsContext = new AtomicReference[HttpsConnectionContext]()

  private lazy val looseWsClient = buildWSClient(allowUntrusted = true)
  private lazy val strictWsClient = new AtomicReference[WSClient]()


  def init() = {

    log.info("loading certs")

    strict.set(Await.result(buildStrict(), timeout))

    strictHttpsContext.set(Http().createClientHttpsContext(AkkaSSLConfig().withSettings(strict.get())))

    strictWsClient.set(buildWSClient(allowUntrusted = false))
  }


  def getHttpsConnectionContext(allowUntrusted: Boolean): HttpsConnectionContext = {
    allowUntrusted match {
      case true => looseHttpsContext
      case false => strictHttpsContext.get()
    }
  }

  def getWSClient(allowUntrusted: Boolean): WSClient = {
    allowUntrusted match {
      case true => looseWsClient
      case false => strictWsClient.get()
    }
  }

  def reload(): Unit = {
    log.info("received reload request")

    init()
  }

  private def getDataplaneCluster(dpClusterId: Option[String], clusterId: Option[String] = None): Future[DataplaneCluster] = {
    dpClusterService.retrieve(dpClusterId.get)
      .map {
        case Left(errors) => throw WrappedErrorException(errors.errors.head)
        case Right(dpCluster) => dpCluster
      }
  }

  private def getContext(allowUntrusted: Boolean): SSLContext = {
    allowUntrusted match {
      case true => buildContext(loose)
      case false => buildContext(strict.get())
    }
  }

  private def buildWSClient(allowUntrusted: Boolean): WSClient = {
    implicit val materializerImplicit = materializer

    val timeout = Try(config.getInt("dp.services.ws.client.requestTimeout.mins") * 60 * 1000).getOrElse(4 * 60 * 1000)

    allowUntrusted match {
      case true =>
        val config = new DefaultAsyncHttpClientConfig.Builder()
          .setAcceptAnyCertificate(true)
          .setRequestTimeout(timeout)
          .build
        AhcWSClient(config)
      case false =>
        val context = new JdkSslContext(getContext(allowUntrusted), true, ClientAuth.NONE)
        val clientConfig = new DefaultAsyncHttpClientConfig.Builder()
          .setSslContext(context)
          .setRequestTimeout(timeout)
          .build()

        AhcWSClient(clientConfig)
    }
  }

  private def buildLoose(): SSLConfigSettings = {

    val loose =
      SSLLooseConfig()
        .withAcceptAnyCertificate(true)
        .withDisableHostnameVerification(true)
        .withDisableSNI(true)
        .withAllowWeakProtocols(true)
        .withAllowWeakCiphers(true)

    SSLConfigSettings()
      .withLoose(loose)
      .withDisabledKeyAlgorithms(keyBlacklist.toList)
  }

  private def buildStrict(): Future[SSLConfigSettings] = {

    implicit val actorSchedulerImplicit = actorSystem.scheduler

    import scala.concurrent.duration._
    retry(buildStrictFuture, 8 seconds, 30)
  }

  private def buildStrictFuture =
    certificateService.list(active = Some(true))
      .map { certificates =>

        val trusts =
          certificates
            .map(cCertificate => {
              log.info(s"loaded certificate: $cCertificate")
              TrustStoreConfig(Some(cCertificate.data), None).withStoreType(cCertificate.format)
            })

        val trustManagerConfig =
          TrustManagerConfig()
            .withTrustStoreConfigs((trusts :+ system).toList)

        SSLConfigSettings()
          .withTrustManagerConfig(trustManagerConfig)
          .withDisabledKeyAlgorithms(keyBlacklist.toList)
      }

  private def buildContext(sslConfig: SSLConfigSettings): SSLContext = {
    val keyManagerFactory = new DefaultKeyManagerFactoryWrapper(sslConfig.keyManagerConfig.algorithm)
    val trustManagerFactory = new DefaultTrustManagerFactoryWrapper(sslConfig.trustManagerConfig.algorithm)
    new ConfigSSLContextBuilder(NoopLogger.factory(), sslConfig, keyManagerFactory, trustManagerFactory).build()
  }

  private def buildLooseHttpsContext(): HttpsConnectionContext = {
    val trustfulSslContext: SSLContext = {

      object NoCheckX509TrustManager extends X509TrustManager {
        def checkClientTrusted(chain: Array[X509Certificate], authType: String) = ()

        def checkServerTrusted(chain: Array[X509Certificate], authType: String) = ()

        override def getAcceptedIssuers = Array[X509Certificate]()
      }

      val context = SSLContext.getInstance("TLS")
      context.init(Array[KeyManager](), Array(NoCheckX509TrustManager), null)
      context
    }

    val ctx = Http().createClientHttpsContext(AkkaSSLConfig().withSettings(loose))

    new HttpsConnectionContext(
      trustfulSslContext,
      ctx.sslConfig,
      ctx.enabledCipherSuites,
      ctx.enabledProtocols,
      ctx.clientAuth,
      ctx.sslParameters
    )
  }

  //  https://gist.github.com/viktorklang/9414163#gistcomment-1612263
  private def retry[T](f: => Future[T], delay: FiniteDuration, retries: Int)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    f recoverWith {
      case _ if retries > 0 => {
        log.info(s"retries left: $retries")
        after(delay, s)(retry(f, delay, retries - 1))
      }
    }
  }
}
