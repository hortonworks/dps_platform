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


import com.google.inject.Inject
import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.db.Webservice.SkuService
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext.Implicits.global
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import models.JsonResponses

import scala.concurrent.Future
import scala.util.Left
import play.api.Configuration
import services.ConsulHealthService

class ServicesManager @Inject()(@Named("skuService") val skuService:SkuService,
                                @Named("healthService") val healthService: ConsulHealthService,
                                private val configuration: Configuration) extends Controller{

  private val smartSenseRegex: String = configuration.underlying.getString("smartsense.regex")

  def getServiceHealth(skuName: String) = Action.async { request =>
    healthService.getServiceHealth(skuName)
      .map {
      statusResponse => Ok(Json.toJson(statusResponse))
    }.recoverWith {
      case e: Throwable =>
        Future.successful(
          InternalServerError(JsonResponses.statusError(e.getMessage)))
    }
  }

  def getServices = Action.async { request =>
    getDpServicesInternal().map {
      case Left(errors) => handleErrors(errors)
      case Right(services) => Ok(Json.toJson(services))
    }
  }

  def getEnabledServices = Action.async { request =>
    getDpServicesInternal().map {
      case Left(errors) => handleErrors(errors)
      case Right(services) => {
        val enabledServices = services.filter(_.enabled == true)
        Ok(Json.toJson(enabledServices))
      }
    }
  }

  def getDependentServices(skuName: String) = Action.async {
    val mandatoryDependentServices = configuration.getStringSeq(s"$skuName.dependent.services.mandatory").getOrElse(Nil)
    val optionalDependentServices = configuration.getStringSeq(s"$skuName.dependent.services.optional").getOrElse(Nil)

    Future.successful(Ok(Json.toJson(ServiceDependency(skuName, mandatoryDependentServices, optionalDependentServices))))
  }

  def verifySmartSense = Action.async(parse.json) { request =>
    val smartSenseId = request.getQueryString("smartSenseId");
    if (smartSenseId.isEmpty) {
      Future.successful(BadRequest("smartSenseId is required"))
    } else {
      if (verifySmartSenseCode(smartSenseId.get)) {
        //TODO meaningful regex from config
        Future.successful(Ok(Json.obj("isValid" -> true)))
      } else {
        Future.successful(Ok(Json.obj("isValid" -> false)))
      }
    }
  }

  def getSkuByName = Action.async { request =>
    val skuNameOpt = request.getQueryString("skuName")
    if (skuNameOpt.isEmpty) {
      Future.successful(BadRequest("skuName not provided"))
    } else {
      skuService.getSku(skuNameOpt.get).map {
        case Left(errors) => handleErrors(errors)
        case Right(services) => {
          Ok(Json.toJson(services))
        }
      }
    }
  }

  private def verifySmartSenseCode(smartSenseId: String) = {
    smartSenseId.matches(smartSenseRegex)
  }

  def enableService=AuthenticatedAction.async(parse.json) { request =>
    request.body.validate[DpServiceEnableConfig].map{config=>
      if (!verifySmartSenseCode(config.smartSenseId)){
        Future.successful(BadRequest("Invalid Smart SenseId"))
      } else {
        skuService.getSku(config.skuName).flatMap {
          case Left(errors) => Future.successful(handleErrors(errors))
          case Right(sku) => {
            val enabledSku = EnabledSku(
              skuId = sku.id.get,
              enabledBy = request.user.id.get,
              smartSenseId = config.smartSenseId,
              subscriptionId = config.smartSenseId //TODO check subscription id later.
            )
            skuService.enableSku(enabledSku).map {
              case Left(errors) => handleErrors(errors)
              case Right(enabledSku) => Ok(Json.toJson(enabledSku))
            }
          }
        }
      }
    }.getOrElse(Future.successful(BadRequest))
  }

  def getEnabledServiceDetail = Action.async { request =>
    //TODO imiplementation
    Future.successful(Ok)
  }

  private def getDpServicesInternal(): Future[Either[Errors, Seq[DpService]]] = {
    skuService.getAllSkus().flatMap {
      case Left(errors) => Future.successful(Left(errors))
      case Right(skus) => {
        skuService.getEnabledSkus().map {
          case Left(errors) => Left(errors)
          case Right(enabledSkus) => {
            val enabledSkusIdMap = enabledSkus.map { enabledSku =>
              (enabledSku.skuId, enabledSku)
            }.toMap

            val dpServices = skus.map { sku =>
              DpService(
                skuName = sku.name,
                enabled = enabledSkusIdMap.contains(sku.id.get),
                sku = sku,
                enabledSku = enabledSkusIdMap.get(sku.id.get)
              )
            }
            Right(dpServices)
          }
        }
      }
    }
  }

  private def handleErrors(errors: Errors) = {
    if (errors.errors.exists(_.status == "400"))
      BadRequest(Json.toJson(errors))
    else if (errors.errors.exists(_.status == "403"))
      Forbidden(Json.toJson(errors))
    else
      InternalServerError(Json.toJson(errors))
  }

}
