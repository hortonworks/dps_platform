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

package domain

import java.time.{Clock, LocalDateTime}
import javax.inject._

import com.hortonworks.dataplane.commons.domain.Atlas.EntityDatasetRelationship
import com.hortonworks.dataplane.commons.domain.Constants
import com.hortonworks.dataplane.commons.domain.Entities._
import domain.API.{AlreadyExistsError, EntityNotFound}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import slick.lifted.ColumnOrdered

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DatasetRepo @Inject()(
                             protected val datasetCategoryRepo: DatasetCategoryRepo,
                             protected val categoryRepo: CategoryRepo,
                             protected val dataAssetRepo: DataAssetRepo,
                             protected val datasetEditDetailsRepo: DatasetEditDetailsRepo,
                             protected val userRepo: UserRepo,
                             protected val clusterRepo: ClusterRepo,
                             protected val favouriteRepo: FavouriteRepo,
                             protected val bookmarkRepo: BookmarkRepo,
                             protected val commentRepo: CommentRepo,
                             protected val ratingRepo: RatingRepo,
                             protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._
  import PaginationSupport._

  val Datasets = TableQuery[DatasetsTable].filter(_.active)

  val DatasetsWritable = TableQuery[DatasetsTable]

  def all(): Future[List[Dataset]] = db.run {
    Datasets.to[List].result
  }

  def doSafeInsert(dataset: Dataset) = (
    Datasets.filter(_.name === dataset.name).exists.result.flatMap { exists =>
      if (!exists) {
        DatasetsWritable returning DatasetsWritable += dataset.copy(version = 0)
      } else {
        DBIO.failed(new AlreadyExistsError()) // no-op
      }
    }
  )

  def count(search: Option[String], userId: Option[Long], filterParam: Option[String]): Future[Int] = {
    def DatasetsWithVersion = Datasets.filter(_.version > 0)
    val filterQuery = userId.map { uid =>
      val filterQueryOnStatus = DatasetsWithVersion.filter(t => (t.sharedStatus === SharingStatus.PUBLIC.id) || (t.createdBy === uid))
      filterParam.map { value =>
        if (value.equalsIgnoreCase("bookmark")) {
          filterQueryOnStatus.join(bookmarkRepo.Bookmarks).on((ds, bm) => (ds.id === bm.objectId && bm.objectType === Constants.AssetCollectionObjectType && bm.userId === uid)).map(_._1)
        } else {
          filterQueryOnStatus
        }
      }.getOrElse(filterQueryOnStatus)
    }.getOrElse(DatasetsWithVersion)

    val query = search
      .map(s => filterQuery.join(userRepo.Users).on(_.createdBy === _.id)
        .join(clusterRepo.Clusters).on(_._1.dpClusterId === _.dpClusterid)
        .filter(m => filterDatasets(m, s)))
      .getOrElse(filterQuery)
    db.run(query.length.result)
  }

  def filterDatasets(m: ((DatasetsTable, userRepo.UsersTable), clusterRepo.ClustersTable), s: String) = {
    val searchTextLowerCase = s.toLowerCase
    (m._1._1.name.toLowerCase like s"%${searchTextLowerCase}%") || (m._1._1.description.toLowerCase like s"%${searchTextLowerCase}%") || (m._1._2.username.toLowerCase like s"%${searchTextLowerCase}%") ||
      (m._2.name.toLowerCase like s"%${searchTextLowerCase}%")
  }

  def findById(datasetId: Long): Future[Option[Dataset]] = {
    db.run(Datasets.filter(_.id === datasetId).result.headOption)
  }

  def findByName(name: String): Future[List[Dataset]] = {
    db.run(Datasets.filter(_.name === name).to[List].result)
  }

  def findByNames(names: Seq[String]): Future[List[Dataset]] = {
    db.run(Datasets.filter(_.name inSetBind names).to[List].result)
  }

  def archiveById(datasetId: Long): Future[Int] = {
    db.run(Datasets.filter(_.id === datasetId).map(_.active).update(false))
  }

  def findByIdWithCategories(datasetId: Long): Future[Option[DatasetAndCategories]] = {
    val datasetQuery = Datasets.filter(_.id === datasetId)
    val categoriesQuery = for {
      datasetCategories <- datasetCategoryRepo.DatasetCategories if datasetCategories.datasetId === datasetId
      categories <- categoryRepo.Categories if categories.id === datasetCategories.categoryId
    } yield (categories)

    val query = for {
      dataset <- datasetQuery.result
      categories <- categoriesQuery.result
    } yield (dataset, categories)

    db.run(query).map {
      case (datasets, categories) =>
        datasets.headOption.map {
          dataset =>
            DatasetAndCategories(dataset, categories)
        }
    }
  }

  def addAssets(datasetId: Long, assets: Seq[DataAsset]): Future[RichDataset] = {
    val assetsToSave = assets.map(_.copy(datasetId = Some(datasetId), state = Some("Edit"), editFlag = Some("Mark_Add")))
    val assetGuIds = assets.map(_.guid)
    var query = for {
      _       <- datasetEditDetailsRepo.Table.filter(_.datasetId === datasetId).result.head
      exstAss <- dataAssetRepo.AllDatasetAssets.filter(_.datasetId === datasetId).filter(_.guid inSet assetGuIds).result
      _       <- dataAssetRepo.AllDatasetAssets.filter(_.id inSet exstAss.map(_.id.get)).map(_.editFlag).update(Some("Mark_Add"))
      _       <- dataAssetRepo.AllDatasetAssets ++= assetsToSave.filterNot(row => exstAss.map(_.guid).contains(row.guid))
    } yield ()
    db.run(query.transactionally).flatMap {
      case _ => getRichDataset(Datasets.filter(_.id === datasetId), None, None).map(_.head)
    }
  }

  def removeAssets(datasetId: Long, assetGuIds: Seq[String]): Future[RichDataset] = {
    var query = for {
      _ <- datasetEditDetailsRepo.Table.filter(_.datasetId === datasetId).result.head
      _ <- dataAssetRepo.AllDatasetAssets.filter(_.datasetId === datasetId).filter(_.guid inSet assetGuIds).map(_.editFlag).update(Some("Mark_Delete"))
    } yield ()
    db.run(query.transactionally).flatMap {
      case _ => getRichDataset(Datasets.filter(_.id === datasetId), None, None).map(_.head)
    }
  }

  def removeAllAssets(datasetId: Long): Future[RichDataset] = {
    var query = for {
      _ <- datasetEditDetailsRepo.Table.filter(_.datasetId === datasetId).result.head
      _ <- dataAssetRepo.AllDatasetAssets.filter(_.datasetId === datasetId).map(_.editFlag).update(Some("Mark_Delete"))
    } yield ()
    db.run(query.transactionally).flatMap {
      case _ => getRichDataset(Datasets.filter(_.id === datasetId), None, None).map(_.head)
    }
  }

  def beginEdit(datasetId:Long, userId:Long) :Future[RichDataset] = {
    var query = for {
      _ <- datasetEditDetailsRepo.Table.filter(_.datasetId === datasetId).result.headOption.map {
        case None => None
        case Some(details) => DBIO.failed(throw new AlreadyExistsError)
      }
        // DB References will make sure datasetId and userId are valid
      _ <- datasetEditDetailsRepo.Table returning datasetEditDetailsRepo.Table +=
        DatasetEditDetails(None, datasetId, userId, Some(LocalDateTime.now(Clock.systemUTC())))
    } yield ()
    db.run(query.transactionally).flatMap {
      case _ => getRichDataset(Datasets.filter(_.id === datasetId), None, None, Some(userId)).map(_.head)
    }
  }

  def saveEdit(datasetId:Long) :Future[RichDataset] = {
    var query = for {
      _ <- dataAssetRepo.AllDatasetAssets.filter(_.datasetId === datasetId).filter(_.editFlag === "Mark_Delete").delete
      _ <- dataAssetRepo.AllDatasetAssets.filter(_.datasetId === datasetId).map(_.state).update(Some("Active"))
      _ <- datasetEditDetailsRepo.Table.filter(_.datasetId === datasetId).delete
      _ <- Datasets.filter(_.id === datasetId).map(_.version).update(1)
    } yield ()
    db.run(query.transactionally).flatMap {
      case _ => getRichDataset(Datasets.filter(_.id === datasetId), None, None).map(_.head)
    }
  }

  def revertEdit(datasetId:Long) :Future[RichDataset] = {
    var query = for {
      _ <- dataAssetRepo.AllDatasetAssets.filter(_.datasetId === datasetId).filter(_.state === "Edit").delete
      _ <- dataAssetRepo.AllDatasetAssets.filter(_.datasetId === datasetId).map(_.editFlag).update(Some("Mark_Add"))
      _ <- datasetEditDetailsRepo.Table.filter(_.datasetId === datasetId).delete
      v <- Datasets.filter(_.id === datasetId).result.head.map(_.version)
      _ <- v match {
        case 0 => Datasets.filter(_.id === datasetId).map(_.active).update(false)
        case _ => Datasets.filter(_.id === datasetId).map(_.version).update(1)
      }
    } yield ()
    db.run(query.transactionally).flatMap {
        case _ => getRichDataset(DatasetsWritable.filter(_.id === datasetId), None, None).map(_.head)
    }
  }

  def create(datasetCreateRequest: DatasetCreateRequest) = {
    val tags = datasetCreateRequest.tags
    val query = for {
      existingCategories <- categoryRepo.Categories.filter(_.name.inSet(tags)).to[List].result
      _ <- {
        val catNames = existingCategories.map(_.name)
        categoryRepo.Categories ++= tags.filter(t => !catNames.contains(t)).map(t => Category(None, t, t))
      }
      savedDataset <- doSafeInsert(datasetCreateRequest.dataset)
      _ <- datasetEditDetailsRepo.Table returning datasetEditDetailsRepo.Table +=
              DatasetEditDetails(None, savedDataset.id.get, savedDataset.createdBy.get, Some(LocalDateTime.now()))
      categories <- categoryRepo.Categories.filter(_.name.inSet(tags)).to[List].result
      _ <- datasetCategoryRepo.DatasetCategories ++= categories.map(c => DatasetCategory(c.id.get, savedDataset.id.get))
      _ <- dataAssetRepo.AllDatasetAssets ++= datasetCreateRequest.dataAssets.map(a => a.copy(datasetId = Some(savedDataset.id.get)))
    } yield (DatasetAndCategories(savedDataset, categories))

    db.run(query.transactionally)
  }

  private def getDatasetWithNameQuery(inputQuery: Query[DatasetsTable, Dataset, Seq], searchText: Option[String]) = {
    val query = (inputQuery.join(userRepo.Users).on(_.createdBy === _.id))
      .join(clusterRepo.Clusters).on(_._1.dpClusterId === _.dpClusterid)
    for {
      ((dataset, user), cluster) <-
      searchText.map(st => query.filter(m => filterDatasets(m, st))).getOrElse(query)
    } yield (dataset, user.username, cluster.name, cluster.id)
  }

  private def getDatasetAssetCount(datasetIds: Seq[Long]) = {
    for {
      ((datasetId, assetType), result) <- dataAssetRepo.DatasetAssets.filter(_.datasetId.inSet(datasetIds))
        .groupBy(a => (a.datasetId, a.assetType))
    } yield (datasetId, assetType, "Active", result.length)
  }

  private def getDatasetEditAssetCount(datasetIds: Seq[Long]) = {
    for {
      ((datasetId, assetType), result) <- dataAssetRepo.DatasetEditAssets.filter(_.datasetId.inSet(datasetIds))
        .groupBy(a => (a.datasetId, a.assetType))
    } yield (datasetId, assetType, "Edit", result.length)
  }


  private def getDatasetCategories(datasetIds: Seq[Long]) = {
    for {
      (datasetCategory, category) <- datasetCategoryRepo.DatasetCategories.filter(_.datasetId.inSet(datasetIds))
        .join(categoryRepo.Categories).on(_.categoryId === _.id)
    } yield (datasetCategory.datasetId, category.name)
  }

  private def getFavIds(datasetIds: Seq[Long], userId: Long) = {
    for {
      ((datasetId, favId), res) <- favouriteRepo.getFavInfoByUidForListQuery(datasetIds, userId, Constants.AssetCollectionObjectType)
    } yield (datasetId, favId)
  }

  private def getBookmarkIds(datasetIds: Seq[Long], userId: Long) = {
    for {
      ((datasetId, bmId), res) <- bookmarkRepo.getBookmarkInfoForObjListQuery(datasetIds, userId, Constants.AssetCollectionObjectType)
    } yield (datasetId, bmId)
  }

  private def getFavCounts(datasetIds: Seq[Long]) = {
    for {
      (datasetId, favs) <- {
        favouriteRepo.getFavInfoForListQuery(datasetIds, Constants.AssetCollectionObjectType)
      }
    } yield (datasetId, favs.length)
  }

  private def getCommentsCount(datasetIds: Seq[Long]) = {
    for {
      (datasetId, comments) <- {
        commentRepo.getCommentsInfoForListQuery(datasetIds, Constants.AssetCollectionObjectType)
      }
    } yield (datasetId, comments.length)
  }

  private def getAverageRating(datasetIds: Seq[Long]) = {
    for {
      (datasetId, ratings) <- {
        ratingRepo.getRatingForListQuery(datasetIds, Constants.AssetCollectionObjectType)
      }
    } yield (datasetId, ratings.map(_.rating).avg)
  }

  def sortByDataset(paginationQuery: Option[PaginatedQuery],
                    query: Query[(DatasetsTable, Rep[String], Rep[String], Rep[Option[Long]]), (Dataset, String, String, Option[Long]), Seq]) = {
    paginationQuery.map {
      pq =>
        val q = pq.sortQuery.map {
          sq =>
            query.sortBy {
              oq =>
                sq.sortCol match {
                  case "id" => oq._1.id
                  case "name" => oq._1.name
                  case "createdOn" => oq._1.createdOn
                  case "cluster" => oq._3
                  case "user" => oq._2
                }
            }(ColumnOrdered(_, sq.ordering))
        }.getOrElse(query)
        q.drop(pq.offset).take(pq.size)
    }.getOrElse(query)
  }

  private def getRichDataset(inputQuery: Query[DatasetsTable, Dataset, Seq],
                             paginatedQuery: Option[PaginatedQuery], searchText: Option[String], userId: Option[Long] = None): Future[Seq[RichDataset]] = {
    if (userId.isDefined) {
      getRichDatasetWithFavInfo(inputQuery, paginatedQuery, searchText, userId.get)
    } else {
      getRichDatasetFromQuery(inputQuery, paginatedQuery, searchText)
    }

  }

  private def getRichDatasetFromQuery(inputQuery: Query[DatasetsTable, Dataset, Seq],
                                      paginatedQuery: Option[PaginatedQuery], searchText: Option[String]) = {
    val query = for {
      datasetWithUsername <- sortByDataset(paginatedQuery, getDatasetWithNameQuery(inputQuery, searchText)).to[List].result
      datasetAssetCount <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getDatasetAssetCount(datasetIds).to[List].result
      }
      datasetEditAssetCount <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getDatasetEditAssetCount(datasetIds).to[List].result
      }
      datasetCategories <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getDatasetCategories(datasetIds).to[List].result
      }
      datasetEditDetails <- datasetEditDetailsRepo.Table.filter(_.datasetId inSet datasetWithUsername.map(_._1.id.get)).result
    } yield (datasetWithUsername, datasetAssetCount, datasetEditAssetCount, datasetCategories, datasetEditDetails)

    db.run(query).map {
      result =>
        val dsEditDetails = result._5
        val datasetWithAssetCountMap = result._2.groupBy(_._1.get).mapValues { e =>
          e.map {
            v => DataAssetCount(v._2, v._3, v._4)
          }
        }
        val datasetWithEditAssetCountMap = result._3.groupBy(_._1.get).mapValues { e =>
          e.map {
            v => DataAssetCount(v._2, v._3, v._4)
          }
        }
        val datasetWithCategoriesMap = result._4.groupBy(_._1).mapValues(_.map(_._2))
        result._1.map {
          case (dataset, user, cluster, clusterId) =>
            RichDataset(
              dataset,
              datasetWithCategoriesMap.getOrElse(dataset.id.get, Nil),
              user,
              cluster,
              clusterId.get,
              datasetWithAssetCountMap.getOrElse(dataset.id.get, Nil) ++ datasetWithEditAssetCountMap.getOrElse(dataset.id.get, Nil),
              dsEditDetails.find(_.datasetId == dataset.id.get)
            )
        }.toSeq
    }
  }

  private def getRichDatasetWithFavInfo(inputQuery: Query[DatasetsTable, Dataset, Seq],
                                        paginatedQuery: Option[PaginatedQuery], searchText: Option[String], userId: Long): Future[Seq[RichDataset]] = {
    val query = for {
      datasetWithUsername <- sortByDataset(paginatedQuery, getDatasetWithNameQuery(inputQuery, searchText)).to[List].result
      datasetAssetCount <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getDatasetAssetCount(datasetIds).to[List].result
      }

      datasetEditAssetCount <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getDatasetEditAssetCount(datasetIds).to[List].result
      }

      datasetCategories <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getDatasetCategories(datasetIds).to[List].result
      }

      favId <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getFavIds(datasetIds, userId).to[List].result
      }

      bmId <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getBookmarkIds(datasetIds, userId).to[List].result
      }

      favCount <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getFavCounts(datasetIds).to[List].result
      }

      commentsCounts <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getCommentsCount(datasetIds).to[List].result
      }
      avgRatings <- {
        val datasetIds = datasetWithUsername.map(_._1.id.get)
        getAverageRating(datasetIds).to[List].result
      }
      datasetEditDetails <- datasetEditDetailsRepo.Table.filter(_.datasetId inSet datasetWithUsername.map(_._1.id.get)).result
    } yield (datasetWithUsername, datasetAssetCount, datasetEditAssetCount, datasetCategories, favId, favCount, bmId, commentsCounts, avgRatings, datasetEditDetails)

    db.run(query).map {
      result =>
        val dsEditDetails = result._10
        val datasetWithAssetCountMap = result._2.groupBy(_._1.get).mapValues { e =>
          e.map {
            v => DataAssetCount(v._2, v._3, v._4)
          }
        }
        val datasetWithEditAssetCountMap = result._3.groupBy(_._1.get).mapValues { e =>
          e.map {
            v => DataAssetCount(v._2, v._3, v._4)
          }
        }
        val datasetWithCategoriesMap = result._4.groupBy(_._1).mapValues(_.map(_._2))
        val favIdMap: Map[Long, Option[Long]] = result._5.groupBy(_._1).mapValues(_.map(_._2).head)
        val favCountMap = result._6.groupBy(_._1).mapValues(_.map(_._2).head)
        val bmIdMap: Map[Long, Option[Long]] = result._7.groupBy(_._1).mapValues(_.map(_._2).head)
        val commentsCountsMap = result._8.groupBy(_._1).mapValues(_.map(_._2).head)
        val avgRatingsMap = result._9.groupBy(_._1).mapValues(_.map(_._2).head)

        result._1.map {
          case (dataset, user, cluster, clusterId) =>
            RichDataset(
              dataset,
              datasetWithCategoriesMap.getOrElse(dataset.id.get, Nil),
              user,
              cluster,
              clusterId.get,
              datasetWithAssetCountMap.getOrElse(dataset.id.get, Nil) ++ datasetWithEditAssetCountMap.getOrElse(dataset.id.get, Nil),
              dsEditDetails.find(_.datasetId == dataset.id.get),
              favIdMap.get(dataset.id.get).flatten,
              favCountMap.get(dataset.id.get),
              bmIdMap.get(dataset.id.get).flatten,
              commentsCountsMap.get(dataset.id.get),
              avgRatingsMap.get(dataset.id.get).flatten
            )
        }.toSeq
    }
  }


  def getRichDataSet(searchText: Option[String], paginatedQuery: Option[PaginatedQuery] = None, userId: Long, filterParam: Option[String]): Future[Seq[RichDataset]] = {
    val query = Datasets.filter(_.version > 0).filter(m => (m.createdBy === userId) || (m.sharedStatus === SharingStatus.PUBLIC.id))
    val filterQuery = filterParam.map { value =>
      if (value.equalsIgnoreCase("bookmark")) {
        query.join(bookmarkRepo.Bookmarks).on((ds, bm) => (ds.id === bm.objectId && bm.objectType === Constants.AssetCollectionObjectType && bm.userId === userId))
          .map(_._1)
      } else {
        query
      }
    }.getOrElse(query)

    getRichDataset(filterQuery, paginatedQuery, searchText, Some(userId))
  }

  def getRichDatasetById(id: Long,userId:Long): Future[Option[RichDataset]] = {
    val query = Datasets.filter(_.id === id).filter(m => m.createdBy === userId || m.sharedStatus === SharingStatus.PUBLIC.id)
    getRichDataset(query, None, None, Some(userId)).map(_.headOption)
  }

  def getRichDatasetByTag(tagName: String, searchText: Option[String], paginatedQuery: Option[PaginatedQuery] = None, userId: Long, filterParam: Option[String]): Future[Seq[RichDataset]] = {

    val query = categoryRepo.Categories.filter(_.name === tagName)
      .join(datasetCategoryRepo.DatasetCategories).on(_.id === _.categoryId)
      .join(Datasets).on(_._2.datasetId === _.id)

    val filterQuery = filterParam.map { value =>
      if (value.equalsIgnoreCase("bookmark")) {
        query.join(bookmarkRepo.Bookmarks).on((ds, bm) => (ds._2.id === bm.objectId && bm.objectType === Constants.AssetCollectionObjectType && bm.userId === userId))
          .map(_._1._2).filter(_.version > 0).filter(m => (m.createdBy === userId) || (m.sharedStatus === SharingStatus.PUBLIC.id))
      } else {
        query.map(_._2).filter(_.version > 0).filter(m => (m.createdBy === userId) || (m.sharedStatus === SharingStatus.PUBLIC.id))
      }
    }.getOrElse {
      query.map(_._2).filter(_.version > 0).filter(m => (m.createdBy === userId) || (m.sharedStatus === SharingStatus.PUBLIC.id))
    }
    getRichDataset(filterQuery, paginatedQuery, searchText, Some(userId))
  }

  def insertWithCategories(dsNtags: DatasetAndTags): Future[RichDataset] = {

    val tags = dsNtags.tags

    val query = (for {
      existingCategories <- categoryRepo.Categories.filter(_.name.inSet(tags)).to[List].result
      _ <- {
        val catNames = existingCategories.map(_.name)
        categoryRepo.Categories ++= tags.filterNot(catNames.contains(_)).map(t => Category(None, t, t))
      }
      savedDataset <- doSafeInsert(dsNtags.dataset)
      _ <- datasetEditDetailsRepo.Table returning datasetEditDetailsRepo.Table +=
              DatasetEditDetails(None, savedDataset.id.get, savedDataset.createdBy.get, Some(LocalDateTime.now()))
      categories <- categoryRepo.Categories.filter(_.name.inSet(tags)).to[List].result
      _ <- datasetCategoryRepo.DatasetCategories ++= categories.map(c => DatasetCategory(c.id.get, savedDataset.id.get))
    } yield (savedDataset)).transactionally

    db.run(query).flatMap {
      case sDset => getRichDataset(Datasets.filter(_.id === sDset.id.get), None, None).map(_.head)
    }
  }

  def updateWithCategories(dsNtags: DatasetAndTags): Future[RichDataset] = {
    val tags = dsNtags.tags
    val query = (for {
      _ <- Datasets.filter(_.id === dsNtags.dataset.id).update(dsNtags.dataset)
      _ <- datasetCategoryRepo.DatasetCategories.filter(_.datasetId === dsNtags.dataset.id).delete
      existingCategories <- categoryRepo.Categories.filter(_.name.inSet(tags)).to[List].result
      _ <- {
        val catNames = existingCategories.map(_.name)
        categoryRepo.Categories ++= tags.filter(t => !catNames.contains(t)).map(t => Category(None, t, t))
      }
      savedDataset <- Datasets.filter(_.id === dsNtags.dataset.id).result.head
      categories <- categoryRepo.Categories.filter(_.name.inSet(tags)).to[List].result
      _ <- datasetCategoryRepo.DatasetCategories ++= categories.map(c => DatasetCategory(c.id.get, savedDataset.id.get))
    } yield (savedDataset)).transactionally
    db.run(query).flatMap {
      case sDset => getRichDataset(Datasets.filter(_.id === sDset.id.get), None, None).map(_.head)
    }
  }

  def updateDatset(datasetId: Long, dataset: Dataset) = {
    val datasetCopy = dataset.copy(lastModified = LocalDateTime.now)
    val query = (for {
      _ <- Datasets.filter(_.id === datasetId).update(datasetCopy)
      ds <- Datasets.filter(_.id === datasetId).result.headOption
    } yield (ds)).transactionally

    db.run(query)
  }

  def getCategoriesCount(searchText: Option[String], userId: Long, filter: Option[String]): Future[List[CategoryCount]] = {
    val countQuery = datasetCategoryRepo.DatasetCategories.join(Datasets.filter(e => e.sharedStatus === SharingStatus.PUBLIC.id || e.createdBy === userId))
      .on(_.datasetId === _.id).map(_._1)

    def countQueryWithFilter(st: String) = {
      datasetCategoryRepo.DatasetCategories.join(
        Datasets.filter(_.version > 0)
          .filter(e => e.sharedStatus === SharingStatus.PUBLIC.id || e.createdBy === userId)
          .join(userRepo.Users).on(_.createdBy === _.id)
          .join(clusterRepo.Clusters).on(_._1.dpClusterId === _.dpClusterid)
          .filter(m => filterDatasets(m, st))
          .map(_._1._1)
      ).on(_.datasetId === _.id).map(_._1)
    }

    val countQueryWithSearchOption = searchText.map(st => countQueryWithFilter(st)).getOrElse(countQuery)

    val queryWithBookmarkOption = filter match {
      case Some(fv) =>
        countQueryWithSearchOption
          .join(bookmarkRepo.Bookmarks.filter(bm => bm.objectType === Constants.AssetCollectionObjectType && bm.userId === userId))
          .on(_.datasetId === _.objectId)
          .map(_._1)
      case None => countQueryWithSearchOption
    }

    val groupByCatId = queryWithBookmarkOption
      .groupBy(_.categoryId)
      .map { case (catId, results) => (catId -> results.length) }


    val statement = groupByCatId.to[List].result.statements

    val query = for {
      ((catId, count), cat) <- groupByCatId.join(categoryRepo.Categories).on(_._1 === _.id)
    } yield (cat.name, count)

    db.run(query.to[List].result).map {
      rows =>
        rows.map {
          case (name, count) =>
            CategoryCount(name, count)
        }.sortBy(_.name)
    }
  }

  def queryManagedAssets(clusterId: Long, assets: Seq[String]): Future[Seq[EntityDatasetRelationship]] = {
    val query = for {
      (dataAsset, dataset) <- dataAssetRepo.DatasetEditAssets.filter(record => record.guid.inSet(assets) /* && record.clusterId === clusterId */) join Datasets on (_.datasetId === _.id)
    } yield (dataAsset.guid, dataset.id, dataset.name)

    db.run(query.to[Seq].result).map {
      results =>
        results.map {
          case (guid, datasetId, datasetName) => EntityDatasetRelationship(guid, datasetId.get, datasetName)
        }
    }

  }


  final class DatasetsTable(tag: Tag)
    extends Table[Dataset](tag, Some("dataplane"), "datasets") with ColumnSelector {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def description = column[Option[String]]("description")

    def dpClusterId = column[Long]("dp_clusterid")

    def createdBy = column[Option[Long]]("createdby")

    def createdOn = column[LocalDateTime]("createdon")

    def lastmodified = column[LocalDateTime]("lastmodified")

    def active = column[Boolean]("active")

    def version = column[Int]("version")

    def sharedStatus = column[Int]("sharedstatus")

    def customprops = column[Option[JsValue]]("custom_props")

    val select = Map("id" -> this.id, "name" -> this.name)

    def * =
      (id,
        name,
        description,
        dpClusterId,
        createdBy,
        createdOn,
        lastmodified,
        active,
        version,
        sharedStatus,
        customprops
      ) <> ((Dataset.apply _).tupled, Dataset.unapply)

  }

}


