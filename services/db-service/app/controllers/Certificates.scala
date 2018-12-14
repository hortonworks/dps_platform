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

import com.hortonworks.dataplane.commons.domain.Entities.{Certificate, Error, WrappedErrorException}
import domain.CertificateRepo
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Certificates @Inject()(certificateRepo: CertificateRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def list(active: Option[Boolean], name: Option[String]) = Action.async {
    certificateRepo.list(active, name)
      .map(success(_))
      .recoverWith(apiError)
  }

  def create() = Action.async(parse.json) { request =>
    (request.body.validate[Certificate] match {
      case certificate: JsSuccess[Certificate] => certificateRepo.create(certificate.get)
      case ex: JsError => Future.failed(WrappedErrorException(Error(400, "Malformed body", "database.http.malformed-body")))
    })
    .map(success(_))
    .recoverWith(apiError)
  }

  def retrieve(certificateId: String) = Action.async {
    certificateRepo.retrieve(certificateId)
      .map(success(_))
      .recoverWith(apiError)
  }

  def update(certificateId: String) = Action.async(parse.json) { request =>
    (request.body.asOpt[Certificate] match {
      case Some(certificate) if(certificate.id.isDefined && certificate.id.get == certificateId) => certificateRepo.update(certificate)
      case Some(_) => Future.failed(WrappedErrorException(Error(400, "Malformed body", "database.http.malformed-body")))
      case None => Future.failed(WrappedErrorException(Error(400, "Malformed body", "database.http.malformed-body")))
    })
      .map(success(_))
      .recoverWith(apiError)
  }

  def delete(certificateId: String) = Action.async {
    certificateRepo.delete(certificateId)
      .map(success(_))
      .recoverWith(apiError)
  }


}
