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

package com.hortonworks.dataplane.restmock

import akka.actor.ActorRef
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.hortonworks.dataplane.restmock.httpmock.RequestAssertion

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class Return(builder: RequestBuilder,
                  response: HttpResponse => HttpResponse)

case class Clear()

case class RequestBuilder(pathVerifier: RequestAssertion)(
    implicit materializer: Materializer) {

  private val verifiers = ListBuffer[RequestAssertion](pathVerifier)

  def withParams(params: (String, String)*): RequestBuilder = {
    verifiers += { req =>
      req.uri.query() == Query(params: _*)
    }
    this
  }

  def withBody(body: String) = {
    verifiers += { req =>
      val eventualString = req.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8"))
      val eventualBoolean = eventualString.map(s => s == body)
      val result = Await.result(eventualBoolean,5.seconds)
      result
    }
    this
  }

  def withHeaders(headerList: (String, String)*) = {
    verifiers += { req =>
      val headers = headerList
        .map { h =>
          val pr = HttpHeader.parse(h._1, h._2)
          val ok = pr.asInstanceOf[ParsingResult.Ok]
          ok.header
        }

      headers.toSet.subsetOf(req.headers.toSet)
    }
    this
  }

  def getContentType(contentType: Option[(String, String)]): String = {
    contentType.map(c => c._2).getOrElse("application/json")
  }

  def thenRespond(statusCode: Int,
                  responseBody: String,
                  headers: (String, String)*)(implicit actor: ActorRef): Unit =
    actor ! Return(
      this, { r =>
        // Check if content type was defined
        val contentType: Option[(String, String)] =
          headers.find(p => p._1.toLowerCase.trim == "content-type")
        r.copy(
          StatusCode.int2StatusCode(Option(statusCode).getOrElse(200)),
          headers
            .map { h =>
              val pr = HttpHeader.parse(h._1, h._2)
              val ok = pr.asInstanceOf[ParsingResult.Ok]
              ok.header
            }
            .to[collection.immutable.Seq],
          HttpEntity(ContentType(MediaTypes.`application/json`),Option(responseBody).getOrElse(""))
        )
      }
    )

  def verify(req: HttpRequest): Boolean =
    verifiers.toList.map(_(req)).foldRight(true)((k, v) => k && v)

}
