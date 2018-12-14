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

package services

import java.io.InputStream

import com.hortonworks.dataplane.commons.domain.Entities.{Error, PluginManifest, WrappedErrorException}
import com.typesafe.config.{Config, ConfigValue}
import com.typesafe.scalalogging.Logger
import javax.inject.{Inject, Singleton}
import org.apache.commons.lang.exception.ExceptionUtils
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import com.hortonworks.dataplane.commons.domain.JsonFormatters._

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Success, Try}


@Singleton
class PluginManifestService @Inject()(val config: Config) {

  private lazy val logger = Logger(classOf[PluginManifestService])

  private val manifests =
    config.getStringList("dp.plugin_manifests")
      .asScala
      .map(cPath => {
        logger.info(s"reading $cPath")
        readFile(cPath).get
      })

  private def readFile(path: String): Try[PluginManifest] =
    Try({

      val stream: InputStream = getClass.getResourceAsStream(path)
      val source = Source.fromInputStream(stream)
      var jsonString: String = ""
      try {
        jsonString = source.getLines.mkString("\n")
      } finally {
        source.close()
      }

      jsonString
    })
    .recover {
      case ex: Exception => throw WrappedErrorException(Error(500, s"Failed to read file at $path. Please check if file exists and accessible", "core.io-error.unable-to-read-file", Some(ExceptionUtils.getFullStackTrace(ex))))
    }
    .flatMap { jsonString => Json.parse(jsonString).asOpt[PluginManifest].map(Success(_)).getOrElse(Failure(WrappedErrorException(Error(500, s"Failed to parse $path. Please verify that configuration is valid JSON.", "core.json-error.unable-to-parse-json")))) }

  def list(): Seq[PluginManifest] = manifests

  def retrieve(name: String): Option[PluginManifest] = manifests.filter(_.name == name).headOption

  def retrieveById(id: Long): Option[PluginManifest] = manifests.filter(_.id == id).headOption

  def getRequiredDependencies(name: String): Seq[String] =
    retrieve(name)
      .map(_.require_cluster_services.filter(_.`type` == "REQUIRED").map(_.name))
      .getOrElse(Nil)

  def getDependencies(name: String): Seq[String] =
    retrieve(name)
      .map(_.require_cluster_services.map(_.name))
      .getOrElse(Nil)

  def isVersionCompatible(name: String, version: String): Boolean =
    retrieve(name)
      .map(_.require_ambari_version)
      .map {
        _
          .filter(cRange => compareVersions(cRange.min, version) <= 0 && compareVersions(version, cRange.max) <= 0)
          .nonEmpty
      }
      .getOrElse(false)

  private def compareVersions(versionA: String, versionB: String): Int = {
    val aParts = versionA.split("\\.")
    val bParts = versionB.split("\\.")
    val length = Math.max(aParts.length, bParts.length)

    0 to (length - 1) foreach { i =>
      val aPart = if(i < aParts.length) { aParts(i).toString().toInt } else { 0 }
      val bPart = if(i < bParts.length) { bParts(i).toString().toInt } else { 0 }

      if(aPart < bPart) { return -1 }
      if(aPart > bPart) { return +1 }
    }
    return 0
  }
}
