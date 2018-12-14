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

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.{CertificateException, CertificateFactory}
import javax.inject.Inject

import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.cs.Webservice.ConfigurationUtilityService
import com.hortonworks.dataplane.db.Webservice.CertificateService
import com.typesafe.scalalogging.Logger
import models.JsonResponses
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import services.SslContextManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class Settings @Inject()(@Named("certificateService") certificateService: CertificateService,
                         @Named("clusterUtilityService") clusterUtilityService: ConfigurationUtilityService,
                         sslContextManager: SslContextManager,
                         configuration: Configuration) extends Controller {

  private val logger = Logger(classOf[Settings])

  def createCert = AuthenticatedAction.async(parse.json) { req =>
    req.body.validate[Certificate].map { certificate =>
      validateCertificate(certificate.data).flatMap { cert =>
        certificateService.create(certificate.copy(createdBy = req.user.id))
          .map { createdCert =>
            refreshHook()
            Ok(Json.toJson(createdCert))
          }
      }.recoverWith{
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
    }.getOrElse(Future.successful(BadRequest))
  }

  def updateCert(certificateId: String) = AuthenticatedAction.async(parse.json) { req =>
    req.body.validate[Certificate].map { certificate =>
      validateCertificate(certificate.data).flatMap { cert =>
          certificateService.update(certificateId, certificate)
            .map { updatedCert =>
              refreshHook()
              Ok(Json.toJson(updatedCert))
            }
        }.recoverWith{
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
    }.getOrElse(Future.successful(BadRequest))
  }

  private def validateCertificate(certificateData: String) = {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val inputStream = new ByteArrayInputStream(certificateData.getBytes("UTF8"))
    val certTry = Try(certificateFactory.generateCertificate(inputStream))
    certTry match {
      case Success(certificate) => Future.successful(certificate)
      case Failure(f) => {
        logger.error(f.getMessage, f.getStackTrace)
        Future.failed(WrappedErrorException(Error(500, "Invalid Certificate Format")))
      }
    }
  }

  def deleteCert(certificateId: String) = AuthenticatedAction.async { req =>
    certificateService.delete(certificateId)
      .map { response =>
        refreshHook()
        Ok(Json.toJson(response))
      }
      .recoverWith {
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
  }

  def listCerts(name: Option[String]) = AuthenticatedAction.async { request =>
    certificateService.list(name = name)
      .map { certs =>
        Ok(Json.toJson(certs))
      }
      .recoverWith {
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
  }

  private def refreshHook() = {
    val futures = Seq(clusterUtilityService.doReloadCertificates(), sslContextManager.reload())
    futures
      .foreach {
        _.onFailure {
            case ex: WrappedErrorException => logger.error(s"Failed with ${ex.error}", ex)
            case ex: Throwable => logger.error("Failed with {}", ex)
        }
      }
  }

}
