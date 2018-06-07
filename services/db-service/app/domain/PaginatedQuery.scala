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

import slick.ast.Ordering
import slick.lifted.{ColumnOrdered, Query, Rep}

case class PaginatedQuery(offset: Int, size: Int, sortQuery: Option[SortQuery] = None)

case class SortQuery(sortCol: String, order: String) {
  def ordering: Ordering =
    if (order == "asc") Ordering(Ordering.Asc) else Ordering(Ordering.Desc)
}

trait ColumnSelector {
  val select: Map[String, Rep[_]]
}

object PaginationSupport {

  implicit class PaginationQuery[A <: ColumnSelector, B, C[_]](query: Query[A, B, C]) {
    def paginate(pqo: Option[PaginatedQuery]): Query[A, B, C] = {
      pqo match {
        case Some(pq) => paginate(pq)
        case None => query
      }
    }

    def paginate(pq: PaginatedQuery): Query[A, B, C] = {
      val sortQuery = pq.sortQuery match {
        case Some(sq) =>
          query.sortBy(_.select(sq.sortCol))(ColumnOrdered(_, sq.ordering))
        case None => query
      }
      sortQuery.drop(pq.offset).take(pq.size)
    }
  }

}
