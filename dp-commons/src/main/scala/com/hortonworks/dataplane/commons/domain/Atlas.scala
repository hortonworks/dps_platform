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

package com.hortonworks.dataplane.commons.domain

import play.api.libs.json.{JsObject, JsValue, Json}

object Atlas {

  case class AtlasClassification ( typeName : String, attributes: JsObject )

  case class BodyToModifyAtlasClassification (
       postData : Option[Seq[AtlasClassification]],
       putData : Option[Seq[AtlasClassification]]
  )

  case class AtlasAttribute(name: String, dataType: String)

  case class Entity(typeName: Option[String],
                    attributes: Option[Map[String, String]],
                    guid: Option[String],
                    status: Option[String],
                    displayText: Option[String],
                    tags : Option[Seq[String]],
                    datasetId: Option[Long],
                    datasetName: Option[String])

  case class EntityDatasetRelationship(guid: String,
                                       datasetId: Long,
                                       datasetName: String)

  case class AtlasEntities(entities: Option[List[Entity]])

  case class AssetProperties(typeName: Option[String],
                             attributes: JsObject,
                             guid: Option[String],
                             status: Option[String],
                             createdBy: Option[String],
                             updatedBy: Option[String],
                             createTime: Option[Long],
                             updateTime: Option[Long],
                             version: Option[Long],
                             classifications: Option[Seq[JsObject]]
                            )
  {
    private val requiredKeySet = Set("createTime", "name", "owner", "qualifiedName")

    def getEntity() = {
      val entityAttr: Map[String,String] = attributes.fields
        .filter(e => requiredKeySet.contains(e._1))
        .map(e => (e._1, try e._2.as[String] catch{case a: Throwable => e._2.toString()})).toMap
      Entity(typeName,
        Some(entityAttr),
        guid, status, entityAttr.get("name"), classifications.map(_.map( j => (j \ "typeName").as[String])), None, None)
    }
  }

  /**
    *
    * @param atlasAttribute
    * @param operation - one of [equals,lt,gt,lte,gte,nte]
    * @param operand - String representation of the expression RHS
    *
    *  A valid submission would be
    *  {"atlasAttribute":{"name":"owner","dataType":"string"},"operation":"equals","operand":"admin"}
    *
    */
  case class AtlasFilter(atlasAttribute: AtlasAttribute,
                         operation: String,
                         operand: String)

  /**
    * List of filters to be combined into a atlas DSL statement

    */
  case class AtlasSearchQuery(atlasFilters: Seq[AtlasFilter],
                              limit: Option[Int] = None,
                              offset: Option[Int] = None)

  implicit val atlasClassificationReads = Json.reads[AtlasClassification]
  implicit val atlasClassificationWrites = Json.writes[AtlasClassification]

  implicit val bodyToModifyAtlasClassificationReads = Json.reads[BodyToModifyAtlasClassification]
  implicit val odyToModifyAtlasClassificationWrites = Json.writes[BodyToModifyAtlasClassification]

  implicit val atlasAttributeReads = Json.reads[AtlasAttribute]
  implicit val atlasAttributeWrites = Json.writes[AtlasAttribute]

  implicit val atlasFilterReads = Json.reads[AtlasFilter]
  implicit val atlasFilterWrites = Json.writes[AtlasFilter]

  implicit val atlasFiltersReads = Json.reads[AtlasSearchQuery]
  implicit val atlasFiltersWrites = Json.writes[AtlasSearchQuery]

  implicit val entityReads = Json.reads[Entity]
  implicit val entityWrites = Json.writes[Entity]

  implicit val entityDatasetRelationshipReads = Json.reads[EntityDatasetRelationship]
  implicit val entityDatasetRelationshipWrites = Json.writes[EntityDatasetRelationship]

  implicit val atlasEntitiesReads = Json.reads[AtlasEntities]
  implicit val atlasEntitiesWrites = Json.writes[AtlasEntities]

  implicit val assetPropertiesReads = Json.reads[AssetProperties]
  implicit val assetPropertiesWrites = Json.writes[AssetProperties]

}
