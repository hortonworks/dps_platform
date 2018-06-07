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

package com.hortonworks.dataplane.db

import com.google.common.base.Strings
import com.hortonworks.dataplane.commons.domain.Entities.{ClusterService => ClusterData, _}
import com.hortonworks.dataplane.commons.domain.Ambari.ClusterServiceWithConfigs
import com.hortonworks.dataplane.commons.domain.Atlas.{AtlasAttribute, AtlasEntities, AtlasSearchQuery, EntityDatasetRelationship}
import play.api.Logger
import play.api.libs.json.{JsObject, JsResult, JsSuccess, Json}
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.util.{Success, Try}

object Webservice {

  trait DbClientService {

    import com.hortonworks.dataplane.commons.domain.JsonFormatters._

    protected  def createEmptyErrorResponse = {
      Left(Errors(Seq(Error(status=404, message = "No response from server"))))
    }

    protected def extractEntity[T](res: WSResponse,
                                   f: WSResponse => T): Either[Errors, T] = {
      Right(f(res))
    }

    protected def extractError(res: WSResponse,
                               f: WSResponse => JsResult[Errors]): Errors = {
      if (res.body.isEmpty)
        Errors()
      else f(res).map(r => r).getOrElse(Errors())
    }

    protected def mapErrors(res: WSResponse) = {
      Left(extractError(res, r => r.json.validate[Errors]))
    }

    protected def mapResponseToError(res: WSResponse, loggerMsg: Option[String]= None) = {
      val errorsObj = Try(res.json.validate[Errors])

      errorsObj match {
        case Success(e :JsSuccess[Errors]) =>
          printLogs(res,loggerMsg)
          throw new WrappedErrorException(e.get.errors.head)
        case _ =>
          val msg = if(Strings.isNullOrEmpty(res.body)) res.statusText else  res.body
          val logMsg = loggerMsg.map { lmsg =>
            s"""$lmsg | $msg""".stripMargin
          }.getOrElse(s"In db-client: Failed with $msg")
          printLogs(res,Option(logMsg))
          throw new WrappedErrorException(Error(res.status, msg, code = "database.generic"))
      }
    }

    private def printLogs(res: WSResponse,msg: Option[String]) ={
      val logMsg = msg.getOrElse(s"Could not get expected response status from service. Response status ${res.statusText}")
      Logger.warn(logMsg)
    }
  }

  trait UserService extends DbClientService {

    def loadUser(username: String): Future[Either[Errors, User]]

    def loadUserById(id: String): Future[Either[Errors, User]]

    def getUserRoles(userName: String): Future[Either[Errors, UserRoles]]

    def addUser(user: User): Future[Either[Errors, User]]

    def updateUser(user: User): Future[Either[Errors, User]]

    def addRole(role: Role): Future[Either[Errors, Role]]

    def addUserRole(userRole: UserRole): Future[Either[Errors, UserRole]]

    def getUsers(): Future[Either[Errors,Seq[User]]]

    def getUsersWithRoles(offset: Option[String], pageSize: Option[String], searchTerm: Option[String]): Future[Either[Errors,UsersList]]

    def getRoles():  Future[Either[Errors,Seq[Role]]]

    def addUserWithRoles(userInfo: UserInfo): Future[Either[Errors, UserInfo]]

    def getUserDetail(userName:String): Future[Either[Errors,UserInfo]]

    def updateActiveAndRoles(userInfo: UserInfo): Future[Either[Errors,Boolean]]

    def addUserWithGroups(userGroupInfo: UserGroupInfo): Future[Either[Errors,UserGroupInfo]]
    def updateUserWithGroups(userLdapGroups: UserLdapGroups): Future[Either[Errors,UserContext]]
    def getUserContext(userName:String): Future[Either[Errors,UserContext]]

  }

  trait GroupService extends DbClientService {

    def getGroups(offset: Option[String], pageSize: Option[String], searchTerm: Option[String]): Future[Either[Errors, GroupsList]]

    def getAllActiveGroups(): Future[Either[Errors,Seq[Group]]]

    def addGroupWithRoles(groupInfo: GroupInfo): Future[Either[Errors,GroupInfo]]

    def updateGroupInfo(groupInfo: GroupInfo): Future[Either[Errors,Boolean]]

    def getGroupByName(groupName: String): Future[Either[Errors,GroupInfo]]

    def getRolesForGroups(groupIds:Seq[Long]): Future[Either[Errors,Seq[String]]]
  }

  trait DataSetService extends DbClientService {

    def list(name: Option[String]): Future[Either[Errors, Seq[Dataset]]]

    def create(dataSetAndTags: DatasetAndTags): Future[RichDataset]

    def create(datasetReq: DatasetCreateRequest): Future[Either[Errors, DatasetAndCategories]]

    def update(dataSetAndTags: DatasetAndTags): Future[RichDataset]

    def addAssets(id: Long, dataAssets: Seq[DataAsset]) : Future[RichDataset]

    def removeAssets(datasetId: Long, queryString: String) : Future[RichDataset]

    def removeAllAssets(id: Long) : Future[RichDataset]

    def beginEdition(id: Long, userId: Long) : Future[RichDataset]

    def saveEdition(id: Long) : Future[RichDataset]

    def cancelEdition(id: Long) : Future[RichDataset]

    def listRichDataset(queryString : String,userId:Long): Future[Either[Errors, Seq[RichDataset]]]

    def getRichDatasetById(id: Long,userId:Long): Future[RichDataset]

    def listRichDatasetByTag(tagName: String, queryString : String,userId:Long): Future[Either[Errors, Seq[RichDataset]]]

    def getDataAssetByDatasetId(id: Long, queryName: String, offset: Long, limit: Long, state: String): Future[Either[Errors, AssetsAndCounts]]

    def getDatasetsByNames(names: String): Future[Either[Errors, Seq[Dataset]]]

    def retrieve(dataSetId: String): Future[Either[Errors, DatasetAndCategories]]

    def updateDataset(datasetId : String, dataset: Dataset): Future[Dataset]

    def delete(dataSetId: String): Future[Either[Errors, Long]]
  }

  trait CategoryService extends DbClientService {

    def list(): Future[Either[Errors, Seq[Category]]]

    def search(searchText: String, size: Option[Long]): Future[Either[Errors, Seq[Category]]]

    def listWithCount(queryString: String, userId: Long): Future[Either[Errors, Seq[CategoryCount]]]

    def listWithCount(categoryName: String): Future[Either[Errors, CategoryCount]]

    def create(category: Category): Future[Either[Errors, Category]]

    def retrieve(categoryId: String): Future[Either[Errors, Category]]

    def delete(categoryId: String): Future[Either[Errors, Category]]
  }

  trait DataSetCategoryService extends DbClientService {

    def getListWithDataSetId(
                              dataSetId: String): Future[Either[Errors, Seq[DatasetCategory]]]

    def getListWithCategoryId(
                               categoryId: String): Future[Either[Errors, Seq[DatasetCategory]]]

    def create(dataSetCategory: DatasetCategory)
    : Future[Either[Errors, DatasetCategory]]

    def delete(dataSetId: String,
               categoryId: String): Future[Either[Errors, DatasetCategory]]
  }

  trait DpClusterService extends DbClientService {

    def list(): Future[Either[Errors, Seq[DataplaneCluster]]]

    def create(dpCluster: DataplaneCluster): Future[Either[Errors, DataplaneCluster]]

    def retrieve(dpClusterId: String): Future[Either[Errors, DataplaneCluster]]

    def retrieveServiceInfo(dpClusterId: String): Future[Either[Errors, Seq[ClusterData]]]

    def checkExistenceByUrl(ambariUrl: String): Future[Either[Errors, Boolean]]

    def update(dpClusterId: String,
               dpCluster: DataplaneCluster): Future[Either[Errors, DataplaneCluster]]

    def update(dpCluster: DataplaneCluster): Future[Either[Errors, DataplaneCluster]]


    def updateStatus(dpCluster: DataplaneCluster): Future[Either[Errors, Boolean]]

    def delete(dpClusterId: String): Future[Either[Errors, Boolean]]

  }

  trait LocationService extends DbClientService {

    def list(query: Option[String]): Future[Either[Errors, Seq[Location]]]

    def retrieve(locationId: Long): Future[Either[Errors, Location]]

  }

  trait CommentService extends DbClientService {

    def add(comment: Comment): Future[CommentWithUser]

    def getByObjectRef(queryString: String): Future[Seq[CommentWithUser]]

    def deleteById(commentId: String,userId: Long): Future[String]

    def update(commentText: String, commentId: String): Future[CommentWithUser]

    def deleteByObjectRef(objectId: String, objectType: String): Future[String]

    def getByParentId(parentId: String, queryString: String): Future[Seq[CommentWithUser]]

    def getCommentsCount(objectId: Long, objectType: String): Future[JsObject]

  }

  trait RatingService extends DbClientService {

    def add(rating: Rating): Future[Rating]

    def get(queryString: String, userId: Long): Future[Rating]

    def getAverage(queryString: String): Future[JsObject]

    def update(ratingId: String, ratingUserTuple: (Float, Long)): Future[Rating]

    def deleteByObjectRef(objectId: String, objectType: String): Future[String]

  }

  trait FavouriteService extends DbClientService {

    def add(favourite: Favourite): Future[FavouriteWithTotal]

    def deleteById(userId: Long,id: Long,objectId: Long, objectType: String): Future[JsObject]

  }

  trait BookmarkService extends DbClientService {

    def add(bookmark: Bookmark): Future[Bookmark]

    def deleteById(userId: Long, bmId:Long): Future[JsObject]

  }

  trait ClusterService extends DbClientService {

    def list(): Future[Either[Errors, Seq[Cluster]]]

    def getLinkedClusters(dpClusterId: Long): Future[Either[Errors, Seq[Cluster]]]

    def create(cluster: Cluster): Future[Either[Errors, Cluster]]

    def retrieve(clusterId: String): Future[Either[Errors, Cluster]]

  }

  // Maps to ClusterService
  trait ClusterComponentService extends DbClientService {

    def create(clusterService: ClusterData): Future[Either[Errors, ClusterData]]

    def getServiceByName(clusterId: Long, serviceName: String): Future[Either[Errors, ClusterData]]

    def updateServiceByName(clusterData: ClusterData): Future[Either[Errors, Boolean]]

    def addClusterHosts(clusterServiceHosts: Seq[ClusterServiceHost] = Seq()): Future[Seq[Either[Errors, ClusterServiceHost]]]

    def updateClusterHosts(clusterServiceHosts: Seq[ClusterServiceHost] = Seq()): Future[Seq[Either[Errors, Boolean]]]

    def getEndpointsForCluster(clusterId: Long, service: String): Future[Either[Errors, ClusterServiceWithConfigs]]

    def getAllServiceEndpoints(serviceName: String): Future[Either[Errors, Seq[ClusterServiceWithConfigs]]]
  }

  trait ClusterHostsService extends DbClientService {
    def getHostByClusterAndName(
                                 clusterId: Long,
                                 hostName: String): Future[Either[Errors, ClusterHost]]

    def getHostsByCluster(
                           clusterId: Long): Future[Either[Errors, Seq[ClusterHost]]]

    def createOrUpdate(host: ClusterHost): Future[Option[Errors]]

  }

  trait ConfigService extends DbClientService {

    def getConfig(key: String): Future[Option[DpConfig]]

    def addConfig(dpConfig: DpConfig): Future[Either[Errors, DpConfig]]

    def setConfig(key: String,value:String): Future[Either[Errors, DpConfig]]

  }

  trait LdapConfigService extends DbClientService{

    def create(ldapConfig:LdapConfiguration): Future[Either[Errors, LdapConfiguration]]

    def get(): Future[Either[Errors, Seq[LdapConfiguration]]]

    def update(ldapConfig: LdapConfiguration): Future[Either[Errors, Boolean]]

  }

  trait WorkspaceService extends DbClientService {
    def list(): Future[Either[Errors, Seq[WorkspaceDetails]]]

    def retrieve(name: String): Future[Either[Errors, WorkspaceDetails]]

    def create(workspace: Workspace): Future[Either[Errors, Workspace]]

    def delete(name: String): Future[Either[Errors, Int]]

  }

  trait AssetWorkspaceService extends DbClientService {
    def list(workspaceId: Long): Future[Either[Errors, Seq[DataAsset]]]

    def create(assetReq: AssetWorkspaceRequest): Future[Either[Errors, Seq[DataAsset]]]

    def delete(workspaceId: Long): Future[Either[Errors, Int]]
  }

  trait NotebookWorkspaceService extends DbClientService {
    def list(workspaceId: Long): Future[Either[Errors, Seq[NotebookWorkspace]]]

    def create(notebookWorkspace: NotebookWorkspace): Future[Either[Errors, NotebookWorkspace]]

    def delete(notebookId: String): Future[Either[Errors, Int]]
  }

  trait DataAssetService extends DbClientService {
    def findManagedAssets(clusterId:Long, assets: Seq[String]): Future[Either[Errors, Seq[EntityDatasetRelationship]]]
    def findAssetByGuid(guid: String): Future[Either[Errors, DataAsset]]
  }

  trait SkuService extends DbClientService {
    def getAllSkus():Future[Either[Errors,Seq[Sku]]]
    def getSku(name:String): Future[Either[Errors,Sku]]
    def getEnabledSkus():Future[Either[Errors,Seq[EnabledSku]]]
    def enableSku(enabledSku: EnabledSku):Future[Either[Errors,EnabledSku]]
  }

  trait CertificateService extends DbClientService {
    def list(active: Option[Boolean] = None, name: Option[String] = None): Future[Seq[Certificate]]
    def create(certificate: Certificate): Future[Certificate]
    def update(certificateId: String, certificate: Certificate): Future[Certificate]
    def delete(certificateId: String): Future[Long]
  }
}
