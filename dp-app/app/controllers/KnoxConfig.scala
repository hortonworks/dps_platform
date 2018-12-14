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
import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors}
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.typesafe.scalalogging.Logger
import com.hortonworks.dataplane.commons.auth.{AuthenticatedAction, AuthenticatedRequest}
import com.hortonworks.dataplane.db.Webservice.ConfigService
import models.{KnoxConfigInfo, KnoxConfigUpdateInfo, KnoxConfiguration}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, Controller}
import services.{KnoxConfigurator, LdapService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.google.inject.name.Named

class KnoxConfig @Inject()(
    val ldapService: LdapService,
    val knoxConfigurator: KnoxConfigurator,
    @Named("configService") configService: ConfigService)
    extends Controller {
  val logger = Logger(classOf[KnoxConfig])

  def handleErrors(errors: Errors) = {
    if (errors.errors.exists(_.status == 400))
      BadRequest(Json.toJson(errors.errors))
    else
      InternalServerError(Json.toJson(errors.errors))
  }

  def getRequestHost(request: AuthenticatedRequest[JsValue]) = {
    request.headers.get("X-Forwarded-Host") match {
      case Some(host) => host.split(",")(0)
      case None => request.host.split(",")(0)
    }
  }

  def configure = AuthenticatedAction.async(parse.json) { request =>
    request.body
      .validate[KnoxConfigInfo]
      .map { ldapConfigInfo: KnoxConfigInfo =>
        val requestHost: String = getRequestHost(request)
        ldapService
          .configure(ldapConfigInfo, requestHost)
          .map {
            case Left(errors) => {
              handleErrors(errors)
            }
            case Right(isCreated) => {
              knoxConfigurator.configure()
              Ok(Json.obj("configured" -> isCreated))
            }
          }
      }
      .getOrElse(
        Future.successful(BadRequest("LDAP config is empty or has missing attributes"))
      )
  }
  def updateLdapConfig= AuthenticatedAction.async(parse.json) { request =>
    request.body
      .validate[KnoxConfigUpdateInfo]
      .map { knoxConfig =>
        ldapService.updateKnoxConfig(knoxConfig).map{
          case Left(errors) =>handleErrors(errors)
          case Right(isCreated) =>{
            knoxConfigurator.configure()
            Ok(Json.obj("configured" -> isCreated))
          }
        }
      }.getOrElse(
        Future.successful(BadRequest)
      )
  }

  def getLdapConfiguration = AuthenticatedAction.async {
    ldapService.getConfiguredLdap.map {
      case Left(errors) => handleErrors(errors)
      case Right(ldapConfigs) =>
        ldapConfigs.length match {
          case 0 => Ok(Json.obj())
          case _ => Ok(Json.toJson(ldapConfigs.head))
        }
    }
  }

  def isKnoxConfigured = Action.async {
    ldapService.getConfiguredLdap.map {
      case Left(errors) => handleErrors(errors)
      case Right(ldapConfigs) =>
        ldapConfigs.length match {
          case 0 => Ok(Json.obj("configured" -> false))
          case _ => Ok(Json.obj("configured" -> true))
        }
    }
  }

  def validate = AuthenticatedAction.async(parse.json) { request =>
    request.body
      .validate[KnoxConfigInfo]
      .map { ldapConf =>
        ldapService
          .validateBindDn(ldapConf.ldapUrl, ldapConf.useAnonymous, ldapConf.bindDn,ldapConf.password, ldapConf.referral)
          .map {
            case Left(errors) => handleErrors(errors)
            case Right(booleanRes) => Ok(Json.toJson(true))
          }
      }
      .getOrElse(
        Future.successful(BadRequest)
      )
  }
  //Configuration is not authenticated as it will be called fro other service from knox agent
  def configuration = Action.async { req =>
    for {
      ldapConfig <- ldapService.getConfiguredLdap
      whitelists <- configService.getConfig("dp.knox.whitelist")
      signedTokenTtl <- configService.getConfig("dp.session.timeout.minutes")
    } yield {
      ldapConfig match {
        case Left(errors) => handleErrors(errors)
        case Right(ldapConfigs) => {
          val whiteListdomains: Option[Seq[String]] = whitelists match {
            case Some(whitelist) => Some(whitelist.configValue.split(","))
            case None => None
          }
          ldapConfigs.headOption match {
            case None =>
              handleErrors(
                Errors(
                  Seq(Error(500, "No ldap configuration found"))))
            case Some(ldapConfig) => {

              val userDnTemplate =
                s"${ldapConfig.userSearchAttributeName.get}={0},${ldapConfig.userSearchBase.get}"
              val password=ldapService.getPassword()
              val knoxLdapConfig =
                KnoxConfiguration(ldapUrl = ldapConfig.ldapUrl.get,
                                  bindDn = ldapConfig.bindDn,
                                  userDnTemplate = Some(userDnTemplate),
                                  domains = whiteListdomains,
                                  userSearchAttributeName = ldapConfig.userSearchAttributeName,
                                  userSearchBase = ldapConfig.userSearchBase,
                                  userObjectClass = ldapConfig.userObjectClass,
                                  useAnonymous = ldapConfig.useAnonymous,
                                  password=password,
                  signedTokenTtl= signedTokenTtl match {
                    case Some(ttl) => Option(ttl.configValue.toLong)
                    case None => None
                  })
              Ok(Json.toJson(knoxLdapConfig))
            }
          }
        }
      }
    }
  }
}
