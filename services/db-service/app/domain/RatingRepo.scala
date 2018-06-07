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

import java.sql.Date
import java.time.{LocalDate, LocalDateTime}
import javax.inject.{Inject, Singleton}

import com.hortonworks.dataplane.commons.domain.Entities.{Comment, CommentWithUser, Rating}
import domain.API.EntityNotFound
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RatingRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider,
                            protected val userRepo: UserRepo) extends HasDatabaseConfigProvider[DpPgProfile] {


  import profile.api._

  val Ratings = TableQuery[RatingsTable]

  def add(rating: Rating): Future[Rating] = {
    val query = for {
      rating <- Ratings returning Ratings += rating
    } yield(rating)

    db.run(query)
  }

  def getAverage(objectId: Long, objectType: String): Future[(Float, Int)] ={
    val queryString = Ratings.filter(m => (m.objectId === objectId && m.objectType === objectType)).map(_.rating)
    val query = (queryString.avg, queryString.length)

    db.run(query.result). map { res =>
      (res._1.getOrElse(0), res._2)
    }

  }

  def update(id: Long, userId: Long, rating: Float): Future[Rating] = {
    val query = (for {
      _ <- Ratings.filter(m => (m.id === id && m.createdBy === userId)).map(_.rating).update(rating)
      rating <- Ratings.filter(_.id === id).result.head
    } yield (rating)).transactionally

    db.run(query)
  }

  def get(userId: Long, objectId: Long, objectType: String): Future[Rating] = {
    val query = Ratings.filter(m => (m.createdBy === userId && m.objectId === objectId && m.objectType === objectType)).result.headOption
    db.run(query).map { (result: Option[Rating]) =>
      if(result.isEmpty){
        throw new EntityNotFound
      }else {
        result.get
      }
    }
  }

  def deleteByObjectRef(objectId:Long, objectType:String)={
    db.run(Ratings.filter(m =>(m.objectId === objectId && m.objectType === objectType)).delete)
  }

  def getRatingForListQuery(objectIds: Seq[Long], objectType:String)={
    Ratings.filter(t => (t.objectId.inSet(objectIds) && t.objectType === objectType)).groupBy(a => a.objectId)
  }

  final class RatingsTable(tag: Tag) extends Table[Rating](tag, Some("dataplane"), "ratings") {
    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def rating = column[Float]("rating")

    def objectType = column[String]("object_type")

    def objectId = column[Long]("object_id")

    def createdBy = column[Long]("createdby")

    def * = (id, rating, objectType, objectId, createdBy) <> ((Rating.apply _).tupled, Rating.unapply)
  }

}
