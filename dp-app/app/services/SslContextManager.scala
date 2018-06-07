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

import java.util.concurrent.atomic.AtomicReference
import javax.inject.{Inject, Singleton}
import javax.net.ssl.SSLSocketFactory

import com.hortonworks.dataplane.db.Webservice.CertificateService
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import com.typesafe.sslconfig.ssl.{ConfigSSLContextBuilder, DefaultKeyManagerFactoryWrapper, DefaultTrustManagerFactoryWrapper, SSLConfigSettings, TrustManagerConfig, TrustStoreConfig}
import com.typesafe.sslconfig.util.NoopLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}


@Singleton
class SslContextManager @Inject()(val config: Config, certificateService: CertificateService) {

  private lazy val log = Logger(classOf[SslContextManager])

  private val sfw: AtomicReference[SSLSocketFactory] = new AtomicReference[SSLSocketFactory]()

  import scala.collection.JavaConverters._
  val keyBlacklist = Try(config.getStringList("dp.certificate.algorithm.blacklist.key").asScala).getOrElse(Nil)
  val timeout = Duration(Try(config.getString("dp.certificate.query.timeout")).getOrElse("4 minutes"))

  val home = System.getProperty("java.home")
  val system = TrustStoreConfig(data=None, filePath = Some(s"$home/lib/security/cacerts"))


  private def buildSocketFactory(): Future[SSLSocketFactory] =
    certificateService.list(active = Some(true))
      .map { certificates =>

        log.info(s"reseived ${certificates.size} certificates")

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
      .map { strictConfig =>
        val keyManagerFactory = new DefaultKeyManagerFactoryWrapper(strictConfig.keyManagerConfig.algorithm)
        val trustManagerFactory = new DefaultTrustManagerFactoryWrapper(strictConfig.trustManagerConfig.algorithm)
        val context = new ConfigSSLContextBuilder(NoopLogger.factory(), strictConfig, keyManagerFactory, trustManagerFactory).build()
        context.getSocketFactory()
      }

  def reload(): Future[SSLSocketFactory] = {
    buildSocketFactory()
      .map(sf => {
        log.info("Loading new socket factory")
        sfw.set(sf)
        sf
      })
  }

  def getSocketFactory(): SSLSocketFactory = {
    Option(sfw.get()) match {
      case Some(sf) => sf
      case None => Await.result(reload(), timeout)
    }
  }
}
