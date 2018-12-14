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

import java.time.LocalDateTime

import com.hortonworks.dataplane.commons.domain.Atlas.{AtlasSearchQuery, BodyToModifyAtlasClassification}
import com.hortonworks.dataplane.commons.domain.Entities.ClusterType.ClusterType
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

/**
  * Data plane main domain entities
  * add objects here which should be persisted
  */
object Entities {

  import scala.reflect.runtime.universe._

  // Routine to get field names for a class
  def fieldsNames[T: TypeTag]: Set[String] =
    typeOf[T].members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.fullName
    }.toSet

  object SharingStatus extends Enumeration {
    val PUBLIC = Value(1)
    val PRIVATE = Value(2)
  }

  object ClusterType extends Enumeration {
    type ClusterType = Value
    val HDF, HDP = Value
  }

  object Status extends Enumeration {
    val DISABLED = Value(1)
    val SETTING_UP = Value(2)
    val ENABLED = Value(3)
  }

  case class ServiceHealth(installed: Boolean = false, healthy: Boolean = false)

  case class HJwtToken(token: String)

  case class InnerError(code: String, trace: Option[String] = None, innererror: Option[InnerError] = None)

  case class Error(status: Int,
                   message: String,
                   code: String = "generic",
                   target: Option[String] = None,
                   trace: Option[String] = None,
                   details: Option[Seq[Error]] = None,
                   innererror: Option[InnerError] = None)

  case class WrappedErrorException(error: Error) extends Exception(error.message)

  case class Errors(errors: Seq[Error] = Seq()) {
    def combine(newErrors: Errors) = Errors(errors ++ newErrors.errors)

    def firstMessage: Int = errors.headOption.map(_.status).getOrElse(500)
  }

  // Pagination
  case class Pagination(page: Int, offset: Long, limit: Long)

  case class User(id: Option[Long] = None,
                  username: String,
                  password: String,
                  displayname: String,
                  avatar: Option[String],
                  active: Option[Boolean] = Some(true),
                  groupManaged: Option[Boolean] = Some(false),
                  created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                  updated: Option[LocalDateTime] = Some(LocalDateTime.now()))

  case class Group(id: Option[Long] = None,
                   groupName: String,
                   displayName: String,
                   active: Option[Boolean] = Some(true),
                   created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                   updated: Option[LocalDateTime] = Some(LocalDateTime.now()))

  case class GroupsList(total: Int, groups: Seq[GroupInfo])

  case class UsersList(total: Int, users: Seq[UserInfo])

  case class UserInfo(id: Option[Long] = None,
                      userName: String,
                      displayName: String,
                      password: Option[String] = None,
                      active: Option[Boolean] = Some(true),
                      roles: Seq[String] = Seq())

  case class UserGroupInfo(id: Option[Long] = None,
                           userName: String,
                           displayName: String,
                           password: Option[String] = None,
                           active: Option[Boolean] = Some(true),
                           groupIds: Seq[Long] = Seq())

  case class UserContext(id: Option[Long],
                         username: String,
                         avatar: Option[String],
                         roles: Seq[String],
                         services: Seq[String],
                         display: Option[String],
                         token: Option[String],
                         password: Option[String],
                         active: Option[Boolean] = Some(true),
                         dbManaged: Option[Boolean] = Some(true),
                         groupManaged: Option[Boolean] = Some(false),
                         updatedAt: Option[Long])

  case class UserLdapGroups(userName: String, ldapGroups: Seq[String])

  case class GroupInfo(id: Option[Long] = None,
                       groupName: String,
                       displayName: String,
                       active: Option[Boolean] = Some(true),
                       roles: Seq[String] = Seq())

  case class Role(id: Option[Long] = None,
                  roleName: String,
                  created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                  updated: Option[LocalDateTime] = Some(LocalDateTime.now()))

  case class Permission(
                         id: Option[Long] = None,
                         permission: String,
                         roleId: Option[Long],
                         created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                         updated: Option[LocalDateTime] = Some(LocalDateTime.now()))

  case class UserRole(id: Option[Long] = None,
                      userId: Option[Long],
                      roleId: Option[Long])

  case class UserGroup(id: Option[Long] = None,
                       userId: Option[Long],
                       groupId: Option[Long])

  case class UserGroups(username: String, groups: Seq[Group])

  case class UserRoles(username: String, roles: Seq[String])

  case class GroupRoles(groupName: String, roles: Seq[String])

  case class GroupRole(id: Option[Long] = None,
                       groupId: Option[Long],
                       roleId: Option[Long])

  case class RolePermission(role: String, permissions: Seq[String])

  case class UserPermission(username: String, rights: Seq[RolePermission])

  //Data lake
  case class Location(
                       id: Option[Long] = None,
                       city: String,
                       province: String,
                       country: String,
                       latitude: Float,
                       longitude: Float
                     )

  case class DataplaneClusterIdentifier(id: Long)

  case class DataplaneCluster(
                               id: Option[Long] = None,
                               name: String,
                               dcName: String,
                               description: String,
                               ambariUrl: String,
                               ambariIpAddress: String,
                               location: Option[Long],
                               createdBy: Option[Long],
                               properties: Option[JsValue],
                               // state should be used to figure out the status of the cluster
                               state: Option[String] = Some("TO_SYNC"),
                               isDatalake: Option[Boolean] = Some(false),
                               knoxEnabled: Option[Boolean] = Some(false),
                               allowUntrusted: Boolean = false,
                               behindGateway: Boolean = false,
                               knoxUrl: Option[String],
                               clusterType: String = "HDP",
                               created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                               updated: Option[LocalDateTime] = Some(LocalDateTime.now())) {

    def getClusterType: ClusterType = ClusterType.withName(clusterType)


  }

  case class DpClusterWithDpServices(
                                      dataplaneCluster: DataplaneCluster,
                                      dpServices: Seq[String])

  case class Category(
                       id: Option[Long] = None,
                       name: String,
                       description: String,
                       created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                       updated: Option[LocalDateTime] = Some(LocalDateTime.now())
                     )

  case class Cluster(
                      id: Option[Long] = None,
                      name: String,
                      clusterUrl: Option[String] = None,
                      secured: Option[Boolean] = Some(false),
                      kerberosuser: Option[String] = None,
                      kerberosticketLocation: Option[String] = None,
                      dataplaneClusterId: Option[Long] = None,
                      userid: Option[Long] = None,
                      properties: Option[JsValue] = None,
                      version: Option[String] = None // stack version from ambari
                    )

  case class ClusterService(
                             id: Option[Long] = None,
                             serviceName: String,
                             properties: Option[JsValue] = None,
                             clusterId: Option[Long] = None
                           )

  case class ClusterServiceHost(
                                 id: Option[Long] = None,
                                 host: String,
                                 serviceid: Option[Long] = None
                               )

  case class EnabledSku(
                         skuId: Long,
                         enabledBy: Long,
                         enabledOn: Option[LocalDateTime] = Some(LocalDateTime.now()),
                         smartSenseId: String,
                         subscriptionId: String,
                         created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                         updated: Option[LocalDateTime] = Some(LocalDateTime.now()))

  case class Sku(id: Option[Long] = None,
                 name: String,
                 description: String,
                 status: Option[Short] = Some(0),
                 created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                 updated: Option[LocalDateTime] = Some(LocalDateTime.now()))

  case class ClusterSku(id: Option[Long] = None,
                        dpClusterId: Long,
                        skuId: Long,
                        created: Option[LocalDateTime] = Some(LocalDateTime.now()),
                        updated: Option[LocalDateTime] = Some(LocalDateTime.now()))

  case class DpService(skuName: String,
                       enabled: Boolean,
                       sku: Sku,
                       enabledSku: Option[EnabledSku])

  case class DpClusterSku(id: Long,
                          name: String,
                          dcName: String,
                          clusterStatus: Option[String],
                          enabled: Boolean,
                          cluster: Option[Cluster],
                          compatible: Boolean)

  case class DpServiceEnableConfig(skuName: String, smartSenseId: String)

  case class ClusterHost(id: Option[Long] = None,
                         host: String,
                         ipaddr: String,
                         status: String,
                         properties: Option[JsValue] = None,
                         clusterId: Long)

  case class ClusterProperties(id: Option[Long] = None,
                               properties: Option[JsValue] = None,
                               clusterId: Long)

  case class Dataset(id: Option[Long] = None,
                     name: String,
                     description: Option[String],
                     dpClusterId: Long,
                     createdBy: Option[Long],
                     createdOn: LocalDateTime = LocalDateTime.now(),
                     lastModified: LocalDateTime = LocalDateTime.now(),
                     active: Boolean = true,
                     version: Int = 1,
                     sharedStatus: Int = SharingStatus.PUBLIC.id, // 1 - Public, 2 - Private
                     customProps: Option[JsValue] = None)

  case class DatasetCategory(categoryId: Long, datasetId: Long)

  case class UnclassifiedDataset(
                                  id: Option[Long],
                                  name: String,
                                  description: Option[String],
                                  dpClusterId: Long,
                                  createdBy: Long,
                                  createdOn: Option[LocalDateTime] = Some(LocalDateTime.now()),
                                  lastModified: Option[LocalDateTime] = Some(LocalDateTime.now()),
                                  customProps: Option[JsValue] = None)

  case class UnclassifiedDatasetCategory(categoryId: Long,
                                         unclassifiedDatasetId: Long)

  case class DataAsset(id: Option[Long],
                       assetType: String,
                       assetName: String,
                       guid: String,
                       assetProperties: JsValue,
                       clusterId: Long,
                       datasetId: Option[Long] = None,
                       state: Option[String] = None,
                       editFlag: Option[String] = None
                      )

  case class AssetsAndCounts(assets: Seq[DataAsset], count: Long)

  case class DatasetDetails(id: Option[Long],
                            details: Option[JsValue],
                            datasetId: Long)

  case class DatasetEditDetails(id: Option[Long],
                                datasetId: Long,
                                editorId: Long,
                                editBegin: Option[LocalDateTime] //= Some(LocalDateTime.now())
                               )

  case class DpConfig(id: Option[Long],
                      configKey: String,
                      configValue: String,
                      active: Option[Boolean] = Some(true),
                      // Special flag to allow exporting this key into ZK, or another
                      // should be implemented as a job to export all keys with this flag set
                      export: Option[Boolean] = Some(true))

  // classes as data conatiner for Rest Api

  case class DataAssetCount(assetType: String, assetState: String, count: Int)

  case class RichDataset(dataset: Dataset,
                         tags: Seq[String],
                         user: String,
                         cluster: String,
                         clusterId: Long,
                         counts: Seq[DataAssetCount],
                         editDetails: Option[DatasetEditDetails] = None,
                         favouriteId: Option[Long] = None,
                         favouriteCount: Option[Int] = None,
                         bookmarkId: Option[Long] = None,
                         totalComments: Option[Int] = None,
                         avgRating: Option[Float] = None)

  case class DatasetAndCategories(dataset: Dataset, categories: Seq[Category])

  case class DatasetAndTags(dataset: Dataset, tags: Seq[String])

  case class CategoryCount(name: String, count: Int)

  case class CategoriesCountAndTotal(categoies: Seq[CategoryCount], total: Int)

  case class DatasetCreateRequest(dataset: Dataset,
                                  clusterId: Long,
                                  tags: Seq[String],
                                  assetQueryModels: Seq[AtlasSearchQuery],
                                  dataAssets: Seq[DataAsset] = Nil)

  case class AddToBoxPrams(datasetId: Long,
                           clusterId: Long,
                           assetQueryModel: AtlasSearchQuery,
                           exceptions: Seq[String])

  case class BoxSelectionPrams(datasetId: Long,
                               clusterId: Long,
                               guids: Seq[String])

  case class ColumnInfoWitTags(
                                name: String,
                                guid: String,
                                classifications: BodyToModifyAtlasClassification
                              )

  case class BodyToModifyAssetColumnTags(
                                          databaseName: String, tableName: String, columns: Seq[ColumnInfoWitTags]
                                        )


  case class LdapConfiguration(
                                id: Option[Long],
                                ldapUrl: Option[String] = None,
                                bindDn: Option[String] = None,
                                userSearchBase: Option[String] = None,
                                userSearchAttributeName: Option[String] = None,
                                userObjectClass: Option[String] = None,
                                groupSearchBase: Option[String] = None,
                                groupSearchAttributeName: Option[String] = None,
                                groupObjectClass: Option[String] = None,
                                groupMemberAttributeName: Option[String] = None,
                                useAnonymous: Option[Boolean] = None,
                                referral: Option[String] = None
                              )

  case class BlacklistedToken(id: Option[Long], token: String, expiry: LocalDateTime)

  case class Comment(id: Option[Long] = None,
                     comment: Option[String],
                     objectType: String,
                     objectId: Long,
                     createdBy: Long,
                     createdOn: Option[LocalDateTime] = Some(LocalDateTime.now()),
                     lastModified: Option[LocalDateTime] = Some(LocalDateTime.now()),
                     parentCommentId: Option[Long],
                     numberOfReplies: Option[Long],
                     editVersion: Option[Int] = Some(0))

  case class CommentWithUser(comment: Comment,
                             userName: String)

  case class Rating(id: Option[Long] = None,
                    rating: Float,
                    objectType: String,
                    objectId: Long,
                    createdBy: Long)

  case class Favourite(id: Option[Long] = None,
                       userId: Long,
                       objectType: String,
                       objectId: Long)

  case class FavouriteWithTotal(favourite: Favourite, totalFavCount: Int)

  case class Bookmark(id: Option[Long] = None,
                      userId: Long,
                      objectType: String,
                      objectId: Long)

  case class Certificate(id: Option[String] = None,
                         name: String,
                         format: String,
                         data: String,
                         active: Boolean,
                         createdBy: Option[Long] = None,
                         created: Option[LocalDateTime] = Some(LocalDateTime.now()))


  case class RequiredClusterService(name: String, `type`: String, label: Option[String], is_agent: Boolean = false)
  case class VersionRange(min: String, max: String)

  case class PluginManifest(id: Long,
    prefix: String,
    name: String,
    label: String,
    version: String,
    flow_type: String,
    enablement_type: String,
    roles: Seq[String],
    require_ambari_version: Seq[VersionRange],
    require_platform_roles: Seq[String],
    require_cluster_services: Seq[RequiredClusterService],
    platform_events_endpoint: String,
    health_check_endpoint: String,
    logo_endpoints: Map[String, String])

}

object JsonFormatters {

  import com.hortonworks.dataplane.commons.domain.Entities._

  val defaultJson = Json.using[Json.WithDefaultValues]

  implicit val innerErrorFormat = Json.format[InnerError]

  implicit val errorWrites = Json.writes[Error]
  implicit val errorReads = Json.reads[Error]

  implicit val errorsWrites = Json.writes[Errors]
  implicit val errorsReads = Json.reads[Errors]

  implicit val wrappedErrorExceptionFormat = Json.format[WrappedErrorException]

  implicit val userWrites = Json.writes[User]
  implicit val userReads = Json.reads[User]

  implicit val roleWrites = Json.writes[Role]
  implicit val roleReads = Json.reads[Role]

  implicit val userRoleWrites = Json.writes[UserRole]
  implicit val userRoleReads = Json.reads[UserRole]

  implicit val userRolesWrites = Json.writes[UserRoles]
  implicit val userRolesReads = Json.reads[UserRoles]
  implicit val rolePermissionWrites = Json.writes[RolePermission]
  implicit val userPermissionWrites = Json.writes[UserPermission]

  implicit val permissionWrites = Json.writes[Permission]
  implicit val permissionReads = Json.reads[Permission]

  implicit val categoryWrites = Json.writes[Category]
  implicit val categoryReads = Json.reads[Category]

  implicit val locationWrites = Json.writes[Location]
  implicit val locationReads = Json.reads[Location]
  implicit val dpClusterWrites = Json.writes[DataplaneCluster]
  implicit val dpClusterReads = Json.reads[DataplaneCluster]

  implicit val dpClusterWithDpServicesWrites = Json.writes[DpClusterWithDpServices]
  implicit val dpClusterWithDpServicesReads = Json.reads[DpClusterWithDpServices]

  implicit val dpClusterIdentifierWrites =
    Json.writes[DataplaneClusterIdentifier]
  implicit val dpClusterIdentifierReads =
    Json.reads[DataplaneClusterIdentifier]

  implicit val skuWrites = Json.writes[Sku]
  implicit val skuReads = Json.reads[Sku]
  implicit val enabledSkuWrites = Json.writes[EnabledSku]
  implicit val enabledSkuReads = Json.reads[EnabledSku]

  implicit val clusterWrites = Json.writes[Cluster]
  implicit val clusterReads = Json.reads[Cluster]

//  implicit val clusterServiceWrites = Json.writes[ClusterService]
//  implicit val clusterServiceReads = Json.reads[ClusterService]
  implicit val clusterServiceWrites = (
    (JsPath \ "id").writeNullable[Long] and
      (JsPath \ "servicename").write[String] and
      (JsPath \ "properties").writeNullable[JsValue] and
      (JsPath \ "clusterId").writeNullable[Long]
    )(unlift(ClusterService.unapply))

  implicit val clusterServiceReads = (
    (JsPath \ "id").readNullable[Long] and
      (JsPath \ "servicename").read[String] and
      (JsPath \ "properties").readNullable[JsValue] and
      (JsPath \ "clusterId").readNullable[Long]
    )(ClusterService.apply _)

  implicit val hostWrites = Json.writes[ClusterServiceHost]
  implicit val hostReads = Json.reads[ClusterServiceHost]

  implicit val clusterHostWrites = Json.writes[ClusterHost]
  implicit val clusterHostReads = Json.reads[ClusterHost]

  implicit val clusterPropertiesWrites = Json.writes[ClusterProperties]
  implicit val clusterPropertiesReads = Json.reads[ClusterProperties]

  implicit val datasetWrites = Json.writes[Dataset]
  implicit val datasetReads = defaultJson.reads[Dataset]

  implicit val datasetCategoryWrites = Json.writes[DatasetCategory]
  implicit val datasetCategoryReads = Json.reads[DatasetCategory]

  implicit val unclassifiedDatasetWrites = Json.writes[UnclassifiedDataset]
  implicit val unclassifiedDatasetReads = Json.reads[UnclassifiedDataset]

  implicit val unclassifiedDatasetCategoryWrites =
    Json.writes[UnclassifiedDatasetCategory]
  implicit val unclassifiedDatasetCategoryReads =
    Json.reads[UnclassifiedDatasetCategory]

  implicit val dataAssetWrites = Json.writes[DataAsset]
  implicit val dataAssetReads = Json.reads[DataAsset]

  implicit val datasetDetailsWrites = Json.writes[DatasetDetails]
  implicit val datasetDetailsReads = Json.reads[DatasetDetails]

  implicit val datasetEditDetailsWrites = Json.writes[DatasetEditDetails]
  implicit val datasetEditDetailsReads = Json.reads[DatasetEditDetails]

  // classes as data conatiner for Rest Api
  implicit val categoriesCountReads = Json.reads[CategoryCount]
  implicit val categoriesCountWrites = Json.writes[CategoryCount]

  implicit val assetsAndCountReads = Json.reads[AssetsAndCounts]
  implicit val assetsAndCountWrites = Json.writes[AssetsAndCounts]

  implicit val dataAssetCountReads = Json.reads[DataAssetCount]
  implicit val dataAssetCountWrites = Json.writes[DataAssetCount]

  implicit val datasetResponseReads = Json.reads[DatasetAndCategories]
  implicit val datasetResponseWrites = Json.writes[DatasetAndCategories]

  implicit val datasetRequestReads = Json.reads[DatasetAndTags]
  implicit val datasetRequestWrites = Json.writes[DatasetAndTags]

  implicit val configReads = Json.reads[DpConfig]
  implicit val configWrites = Json.writes[DpConfig]

  implicit val categoriesCountAndTotalReads =
    Json.reads[CategoriesCountAndTotal]
  implicit val categoriesCountAndTotalWrites =
    Json.writes[CategoriesCountAndTotal]

  implicit val datasetCreateRequestReads = defaultJson.reads[DatasetCreateRequest]
  implicit val datasetCreateRequestWrites = Json.writes[DatasetCreateRequest]

  implicit val addToBoxPramsReads = defaultJson.reads[AddToBoxPrams]
  implicit val addToBoxPramsWrites = Json.writes[AddToBoxPrams]

  implicit val BoxSelectionPramsReads = defaultJson.reads[BoxSelectionPrams]
  implicit val BoxSelectionPramsWrites = Json.writes[BoxSelectionPrams]

  implicit val columnInfoWitTagsReads = defaultJson.reads[ColumnInfoWitTags]
  implicit val columnInfoWitTagsWrites = Json.writes[ColumnInfoWitTags]

  implicit val bodyForModifyAssetColumnTagsReads = defaultJson.reads[BodyToModifyAssetColumnTags]
  implicit val bodyForModifyAssetColumnTagsWrites = Json.writes[BodyToModifyAssetColumnTags]

  implicit val richDatasetReads = defaultJson.reads[RichDataset]
  implicit val richDatasetWrites = Json.writes[RichDataset]

  implicit val ldapConfigurationReads = Json.reads[LdapConfiguration]
  implicit val ldapConfigurationWrites = Json.writes[LdapConfiguration]

  implicit val userInfoReads = Json.reads[UserInfo]
  implicit val userInfoWrites = Json.writes[UserInfo]

  implicit val usersListWrites = Json.writes[UsersList]
  implicit val usersListReads = Json.reads[UsersList]

  implicit val groupReads = Json.reads[Group]
  implicit val groupWrites = Json.writes[Group]

  implicit val groupInfoReads = Json.reads[GroupInfo]
  implicit val groupInfoWrites = Json.writes[GroupInfo]

  implicit val groupsListWrites = Json.writes[GroupsList]
  implicit val groupsListReads = Json.reads[GroupsList]

  implicit val userContextWrites = Json.writes[UserContext]
  implicit val userContextReads = Json.reads[UserContext]

  implicit val userGroupInfoWrites = Json.writes[UserGroupInfo]
  implicit val userGroupInfoReads = Json.reads[UserGroupInfo]

  implicit val userLdapGroupsWrites = Json.writes[UserLdapGroups]
  implicit val userLdapGroupsReads = Json.reads[UserLdapGroups]

  implicit val dpServiceWrites = Json.writes[DpService]
  implicit val dpServiceReads = Json.reads[DpService]

  implicit val dpServiceEnableConfigWrites = Json.writes[DpServiceEnableConfig]
  implicit val dpServiceEnableConfigReads = Json.reads[DpServiceEnableConfig]

  implicit val commentWrites = Json.writes[Comment]
  implicit val commentReads = Json.reads[Comment]

  implicit val commentWithUserWrites = Json.writes[CommentWithUser]
  implicit val commentWithUserReads = Json.reads[CommentWithUser]

  implicit val ratingWrites = Json.writes[Rating]
  implicit val ratingReads = Json.reads[Rating]

  implicit val favouriteWrites = Json.writes[Favourite]
  implicit val favouriteReads = Json.reads[Favourite]

  implicit val favouriteWithTotalWrites = Json.writes[FavouriteWithTotal]
  implicit val favouriteWithTotalReads = Json.reads[FavouriteWithTotal]

  implicit val bookmarkWrites = Json.writes[Bookmark]
  implicit val bookmarkReads = Json.reads[Bookmark]

  implicit val serviceHealthWrites = Json.writes[ServiceHealth]
  implicit val serviceHealthReads = Json.reads[ServiceHealth]


  implicit val blacklistedTokenFormats = Json.format[BlacklistedToken]

  implicit val certificateFormats = Json.format[Certificate]

  implicit val clusterSkuFormats: OFormat[ClusterSku] = Json.format[ClusterSku]

  implicit val dpClusterSkuFormats: OWrites[DpClusterSku] = Json.format[DpClusterSku]


  implicit val requiredClusterServiceFormats = Json.using[Json.WithDefaultValues].format[RequiredClusterService]
  implicit val versionRangeFormats = Json.format[VersionRange]

  implicit val pluginManifestFormats = Json.format[PluginManifest]

}
