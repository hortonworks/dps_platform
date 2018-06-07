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

package com.hortonworks.dataplane.commons.domain.profiler.parsers

import com.hortonworks.dataplane.commons.domain.profiler.models.Assets.AssetType.AssetType
import com.hortonworks.dataplane.commons.domain.profiler.models.Assets.{Asset, AssetType, HDFSAssetDefinition, HiveAssetDefinition}

import play.api.libs.functional.syntax._
import play.api.libs.json._

object AssetParser {

  private val assetTypeIdentifier = "assetType"

  private val assetDefinitionIdentifier = "definition"

  private implicit val assetTypeFormat: Format[AssetType] = new Format[AssetType] {
    def reads(json: JsValue) = JsSuccess(AssetType.withName(json.as[String]))

    def writes(myEnum: AssetType) = JsString(myEnum.toString)
  }
  private implicit val hiveAssetFormat: Format[HiveAssetDefinition] = Json.format[HiveAssetDefinition]
  private implicit val hdfsAssetFormat: Format[HDFSAssetDefinition] = Json.format[HDFSAssetDefinition]
  private implicit val assetRead: Reads[Either[Asset, ErrorMessage]] = ((JsPath \ assetTypeIdentifier).read[AssetType]
    and (JsPath \ assetDefinitionIdentifier).read[JsValue]) (
    (assetType, definition) => {
      assetType match {
        case AssetType.Hive => Left(Asset(AssetType.Hive, definition.as[HiveAssetDefinition]))
        case AssetType.HDFS => Left(Asset(AssetType.HDFS, definition.as[HDFSAssetDefinition]))
        case _ => Right(s"unsupported asset type ${assetType.toString}")
      }
    }
  )

  implicit val assetFormat: Format[Asset] = new Format[Asset] {

    def reads(json: JsValue) =
      json.as[Either[Asset, ErrorMessage]] match {
        case Left(asset) => JsSuccess(asset)
        case Right(error) => JsError(error)
      }


    def writes(asset: Asset) = asset.definition match {
      case definition: HiveAssetDefinition => Json.toJson(Map(assetTypeIdentifier -> JsString(asset.assetType.toString), assetDefinitionIdentifier -> Json.toJson(definition)))
      case definition: HDFSAssetDefinition => Json.toJson(Map(assetTypeIdentifier -> JsString(asset.assetType.toString), assetDefinitionIdentifier -> Json.toJson(definition)))
      case _ => JsNull
    }
  }


}



