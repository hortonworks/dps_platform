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

package com.hortonworks.dataplane.knoxagent

import java.time.LocalDateTime

case class KnoxConfig(
    ldapUrl: Option[String] = None,
    bindDn: Option[String] = None,
    userDnTemplate: Option[String] = None,
    userSearchAttributeName : Option[String]=None,
    userSearchBase : Option[String]=None,
    userObjectClass: Option[String]=None,
    //domains: Option[String],/*list of urls from which app is accessible*/
    domains: Option[Seq[String]],/*list of urls from which app is accessible*/
    signedTokenTtl : Option[Long], /* None will make it -1 and valid forever. Give this in minutes*/
    allowHttpsOnly : Option[Boolean]=Some(false), /* the app has to be on https for more security*/
    password: Option[String]
)

case class Certificate(id: Option[String] = None,
                       name: String,
                       format: String,
                       data: String,
                       active: Boolean,
                       createdBy: Option[Long],
                       created: Option[LocalDateTime])
object Formatters {

  import play.api.libs.json.Json
  implicit val knoxConfigFormat = Json.format[KnoxConfig]
  implicit val certificateFormat = Json.format[Certificate]

}
