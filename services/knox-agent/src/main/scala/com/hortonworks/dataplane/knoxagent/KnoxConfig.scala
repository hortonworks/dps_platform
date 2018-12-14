/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
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
    password: Option[String],
    useAnonymous: Option[Boolean] = None,
    referral: Option[String] = None
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
