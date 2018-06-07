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

package com.hortonworks.dataplane.http

import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors}
import org.apache.commons.lang3.exception.ExceptionUtils
import play.api.libs.json.Json
import play.api.libs.json.Json.JsValueWrapper

import scala.concurrent.Future

trait BaseRoute {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def success(data: JsValueWrapper) = Json.obj("results" -> Json.obj("data" -> data))

  def errors(status: Int = 500,
             code: String = "cluster.generic",
             message: String,
             exception: Throwable) =
    Json.toJson(
      Errors(
        Seq(
          Error(status = status,
                code = code,
                message = message,
                trace = Some(ExceptionUtils.getStackTrace(exception))))))

  def notFound =
    Json.obj("error" -> Json.obj("message" -> "Not found", "trace" -> ""))

  def badRequest =
    Json.obj("error" -> Json.obj("message" -> "BadRequest", "trace" -> ""))

  def serverError =
    Json.obj("error" -> Json.obj("message" -> "ServerError", "trace" -> ""))

}
