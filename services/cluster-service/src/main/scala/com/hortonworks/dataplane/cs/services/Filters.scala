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

package com.hortonworks.dataplane.cs.services

import com.hortonworks.dataplane.commons.domain.Atlas.{
  AtlasFilter,
  AtlasSearchQuery
}

object Filters {

  private type Preprocessor = (AtlasFilter) => AtlasFilter

  private sealed case class Query(q: String)

  private val predicates = Seq(
    new StringEqualsPredicate(),
    new NonStringEqualsPredicate(),
    new LessThanPredicate(),
    new LessThanEqualsPredicate(),
    new GreaterThanPredicate(),
    new GreaterThanEqualsPredicate(),
    new NotEqualsPredicate(),
    new NotEqualsStringPredicate(),
    new StringContainsPredicate(),
    new StringStartsWithPredicate(),
    new StringEndsWithPredicate(),
    new TagEqualsWithPredicate()
  )

  def query(atlasFilters: AtlasSearchQuery, lowerCaseOperand: Boolean = true) = {
    val filters = atlasFilters.atlasFilters.map { af =>
      val toApply = predicates.find(p => p.isApplicable(af))
      toApply.map { ta =>
        ta.apply(af, { f =>
            if (lowerCaseOperand)
              f.copy(operand = af.operand.toLowerCase)
            else f
          })
          .q
      }
    }

    val filterList = filters.collect {
      case Some(str) => str
    }.toList

    // create a collection of 'where and ands'
    val fillers = "where" :: List.fill(filterList.size - 1)("and")
    // zip them together
    val zipped = intersperse(fillers, filterList)
    zipped.mkString(" ")

  }

  private def intersperse[A](a: List[A], b: List[A]): List[A] = a match {
    case first :: rest => first :: intersperse(b, rest)
    case _             => b
  }

  private sealed trait Predicate {

    def isApplicable(atlasFilter: AtlasFilter): Boolean

    protected def apply(atlasFilter: AtlasFilter): Query

    def apply(atlasFilter: AtlasFilter, preProcess: Preprocessor): Query = {
      apply(preProcess(atlasFilter))
    }
  }

  private class StringEqualsPredicate extends Predicate {

    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}='${atlasFilter.operand}'")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      atlasFilter.atlasAttribute.dataType == "string" && atlasFilter.operation == "equals"
    }
  }

  private class NonStringEqualsPredicate extends Predicate {

    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}=${atlasFilter.operand}")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType != "string" && dataType != "tag" && atlasFilter.operation == "equals"
    }
  }

  private class LessThanPredicate extends Predicate {
    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}<${atlasFilter.operand}")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType != "string" && dataType != "boolean" && atlasFilter.operation == "lt"
    }
  }

  private class GreaterThanPredicate extends Predicate {
    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}>${atlasFilter.operand}")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType != "string" && dataType != "boolean" && atlasFilter.operation == "gt"
    }
  }

  private class GreaterThanEqualsPredicate extends Predicate {
    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}>=${atlasFilter.operand}")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType != "string" && dataType != "boolean" && atlasFilter.operation == "gte"
    }
  }

  private class LessThanEqualsPredicate extends Predicate {
    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}<=${atlasFilter.operand}")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType != "string" && dataType != "boolean" && atlasFilter.operation == "lte"
    }
  }

  private class NotEqualsPredicate extends Predicate {
    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}!=${atlasFilter.operand}")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType != "string" && atlasFilter.operation == "nte"
    }
  }

  private class NotEqualsStringPredicate extends Predicate {
    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"${atlasFilter.atlasAttribute.name}!='${atlasFilter.operand}'")
    }

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType == "string" && atlasFilter.operation == "nte"
    }
  }

  private class StringContainsPredicate extends Predicate {

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType == "string" && atlasFilter.operation == "contains"
    }

    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(
        s"${atlasFilter.atlasAttribute.name} like '*${atlasFilter.operand}*'")
    }
  }

  private class StringStartsWithPredicate extends Predicate {

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType == "string" && atlasFilter.operation == "startsWith"
    }

    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(
        s"${atlasFilter.atlasAttribute.name} like '${atlasFilter.operand}*'")
    }
  }

  private class StringEndsWithPredicate extends Predicate {

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType == "string" && atlasFilter.operation == "endsWith"
    }

    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(
        s"${atlasFilter.atlasAttribute.name} like '*${atlasFilter.operand}'")
    }
  }

  private class TagEqualsWithPredicate extends Predicate {

    override def isApplicable(atlasFilter: AtlasFilter): Boolean = {
      val dataType = atlasFilter.atlasAttribute.dataType
      dataType == "tag" && atlasFilter.operation == "equals"
    }

    override def apply(atlasFilter: AtlasFilter): Query = {
      Query(s"hive_table isa ${atlasFilter.operand}")
    }
  }

}
