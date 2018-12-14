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

import javax.inject.{Inject, Singleton}

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import com.hortonworks.dataplane.CSConstants
import com.hortonworks.dataplane.commons.domain.Constants
import com.hortonworks.dataplane.commons.domain.Entities.{Errors, WrappedErrorException}
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.cs.{ClusterDataApi, CredentialInterface}
import com.hortonworks.dataplane.cs.services.AtlasService
import com.hortonworks.dataplane.http.BaseRoute
import com.typesafe.scalalogging.Logger
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class AtlasRoute @Inject()(atlas: AtlasService, clusterDataApi: ClusterDataApi, credentialInterface: CredentialInterface)
    extends BaseRoute {

  import com.hortonworks.dataplane.commons.domain.Atlas._
  import com.hortonworks.dataplane.http.JsonSupport._

  val logger = Logger(classOf[AtlasRoute])

  val hiveAttributes =
    path("cluster" / Segment / "atlas" / "hive" / "attributes") { clusterId =>
      extractRequest { request =>
        get {
          implicit val token = extractToken(request)
          onComplete(
            for {
              urls <- clusterDataApi.getAtlasUrl(clusterId.toLong)
              cred <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
              res <- atlas.getEntityTypes(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), "hive_table")
            } yield res
          ) {
            case Success(att) => complete(success(att))
            case Failure(th) => complete(StatusCodes.InternalServerError, errors(500, "cluster.atlas.generic", "A generic error occured while communicating with Atlas.", th))
          }
        }
      }
    }

  val hiveTables = {
    path("cluster" / Segment / "atlas" / "hive" / "search") { clusterId =>
      extractRequest { request =>
        post {
          entity(as[AtlasSearchQuery]) { query =>
            implicit val token = extractToken(request)
            onComplete(
              for {
                urls <- clusterDataApi.getAtlasUrl(clusterId.toLong)
                cred <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
                res <- atlas.query(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), query)
              } yield res
            ) {
              case Success(entities) => complete(success(entities))
              case Failure(th) =>
                th match {
                  case ex: WrappedErrorException => complete(ex.error.status -> Json.toJson(Errors(Seq(ex.error))))
                  case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.atlas.generic", "A generic error occured while communicating with Atlas.", th))
                }
            }
          }
        }
      }
    }
  }

  val atlasEntity = path("cluster" / Segment / "atlas" / "guid" / Segment) {
    (clusterId, guid) =>
      extractRequest { request =>
        get {
          implicit val token = extractToken(request)
          handleResponse(
            for {
              urls <- clusterDataApi.getAtlasUrl(clusterId.toLong)
              cred <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
              res <- atlas.getEntity(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), guid)
            } yield res
          )
        }
      }
  }

  val atlasEntities = path("cluster" / Segment / "atlas" / "guid") { clusterId =>
    pathEndOrSingleSlash {
      extractRequest { request =>
        parameters('query.*) { guids =>
          get {
            implicit val token = extractToken(request)
            handleResponse(
              for {
                urls <- clusterDataApi.getAtlasUrl(clusterId.toLong)
                cred <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
                res <- atlas.getEntities(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), guids.toList)
              } yield res
            )
          }
        }
      }
    }
  }

  val atlasLineage =
    path("cluster" / Segment / "atlas" / Segment / "lineage") {
      (clusterId, guid) =>
        pathEndOrSingleSlash {
          extractRequest { request =>
            parameters("depth".?) { depth =>
              get {
                implicit val token = extractToken(request)
                onComplete(
                  for {
                    urls <- clusterDataApi.getAtlasUrl(clusterId.toLong)
                    cred <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
                    res <- atlas.getLineage(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), guid, depth)
                  } yield res
                ) {
                  case Success(entities) => complete(success(entities))
                  case Failure(th) =>
                    th match {
                      case ex: WrappedErrorException => complete(ex.error.status -> Json.toJson(Errors(Seq(ex.error))))
                      case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.atlas.generic", "A generic error occured while communicating with Atlas.", th))
                    }
                }
              }
            }
          }
        }
    }

  val atlasTypeDefs =
    path("cluster" / Segment / "atlas" / "typedefs" / "type" / Segment) {
      (clusterId, typeDef) =>
        extractRequest { request =>
          get {
            implicit val token = extractToken(request)
            onComplete(
              for {
                urls <- clusterDataApi.getAtlasUrl(clusterId.toLong)
                cred <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
                res <- atlas.getTypes(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), typeDef)
              } yield res
            ) {
              case Success(typeDefs) => complete(success(typeDefs))
              case Failure(th) => complete(StatusCodes.InternalServerError, errors(500, "cluster.atlas.generic", "A generic error occured while communicating with Atlas.", th))
            }
          }
        }
    }

    val postEntityClassifications =
    path("cluster" / Segment / "atlas" / "entity" / "guid" / Segment / "classifications") {
      (clusterId, guid) =>
        extractRequest { request =>
          post {
            implicit val token = extractToken(request)
            entity(as[BodyToModifyAtlasClassification]) { body =>
              onComplete(
                for {
                  urls <- clusterDataApi.getAtlasUrl(clusterId.toLong)
                  cred <- credentialInterface.getCredential(CSConstants.ATLAS_CREDENTIAL_KEY)
                  res <- body.postData match {
                    case None => Future.successful(Json.obj("success" -> true))
                    case Some(seqOfClasfion:Seq[AtlasClassification]) =>
                      atlas.postClassification(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), guid, seqOfClasfion)
                  }
                  res <- body.putData match {
                    case None => Future.successful(Json.obj("success" -> true))
                    case Some(seqOfClasfion:Seq[AtlasClassification]) =>
                      atlas.putClassification(urls, clusterId, cred.user.getOrElse("admin"), cred.pass.getOrElse("admin"), guid, seqOfClasfion)
                  }
                } yield res
              ) {
                case Success(jsVs) => complete(success(jsVs))
                case Failure(th) => complete(StatusCodes.InternalServerError, errors(500, "cluster.atlas.generic", "A generic error occured while communicating with Atlas.", th))
              }
            }
          }
        }
    }

  private def extractToken(httpRequest: HttpRequest):Option[String] = {
    val tokenHeader = httpRequest.getHeader(Constants.DPTOKEN)
    if (tokenHeader.isPresent) Some(tokenHeader.get().value()) else None
  }

  private def handleResponse(eventualValue: Future[JsValue]) = {
    onComplete(eventualValue) {
      case Success(entities) => complete(success(entities))
      case Failure(th) =>
        th match {
          case ex: WrappedErrorException => complete(ex.error.status -> Json.toJson(Errors(Seq(ex.error))))
          case _ => complete(StatusCodes.InternalServerError, errors(500, "cluster.atlas.generic", "A generic error occured while communicating with Atlas.", th))
        }
    }
  }
}
