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

package com.hortonworks.dataplane.http.routes

import javax.inject.Inject

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import com.hortonworks.dataplane.CSConstants
import com.hortonworks.dataplane.commons.domain.Constants
import com.hortonworks.dataplane.commons.service.api.ServiceNotFound
import com.hortonworks.dataplane.cs.{ClusterDataApi, CredentialInterface}
import com.hortonworks.dataplane.cs.services.RangerService
import com.hortonworks.dataplane.http.BaseRoute
import com.hortonworks.dataplane.http.JsonSupport._
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class RangerRoute @Inject()(rangerService: RangerService, clusterDataApi: ClusterDataApi, credentialInterface: CredentialInterface) extends BaseRoute {

  val logger = Logger(classOf[RangerRoute])

  val rangerAudit =
    path("cluster" / Segment / "ranger" / "audit" / Segment / Segment) {
      (clusterId, dbName, tableName) =>
        extractRequest { request =>
          parameterMap { params =>
              get {
                implicit val token = extractToken(request)
                onComplete(
                  for {
                    url <- clusterDataApi.getRangerUrl(clusterId.toLong)
                    credential <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
                    json <- rangerService.requestRangerForAudit(clusterId, url, credential, dbName, tableName, params)
                  } yield json
                ) {
                    case Success(json) => complete(success(json))
                    case Failure(th) =>
                      th match {
                        case th: ServiceNotFound =>
                          complete(StatusCodes.NotFound, errors(404, "cluster.ranger.service-not-found", "Unable to find Ranger configured for this cluster.", th))
                        case _ =>
                          complete(StatusCodes.InternalServerError, errors(500, "cluster.ranger.generic", "A generic error occured while communicating to Ranger.", th))
                      }
                  }
              }
          }
        }
    }

  val rangerPolicy =
    path("cluster" / Segment / "ranger" / "policies") { clusterId =>
      extractRequest { request =>
        parameterMap { params =>
            get {
              implicit val token = extractToken(request)
              onComplete(
                for {
                  url <- clusterDataApi.getRangerUrl(clusterId.toLong)
                  credential <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
                  json <- rangerService.requestRangerForPolicies(clusterId, url, credential, params)
                } yield json
              ) {
                  case Success(json) => complete(success(json))
                  case Failure(th) =>
                    th match {
                      case th: ServiceNotFound => complete(StatusCodes.NotFound, errors(404, "cluster.ranger.service-not-found", "Unable to find Ranger configured for this cluster.", th))
                      case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.ranger.generic", "A generic error occured while communicating to Ranger.", th))
                    }
                }
            }
        }
      }
    }

  private def extractToken(httpRequest: HttpRequest): Option[String] = {
    val tokenHeader = httpRequest.getHeader(Constants.DPTOKEN)
    if (tokenHeader.isPresent) Some(tokenHeader.get().value()) else None
  }

//  private def handleResponse(eventualValue: Future[JsValue]) = {
//    onComplete(eventualValue) {
//      case Success(entities) => complete(success(entities))
//      case Failure(th) =>
//        th match {
//          case ex: WrappedErrorException => complete(ex.error.status -> Json.toJson(Errors(Seq(ex.error))))
//          case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.ranger.generic", "A generic error occured while communicating with Ranger.", th))
//        }
//    }
//  }

}
