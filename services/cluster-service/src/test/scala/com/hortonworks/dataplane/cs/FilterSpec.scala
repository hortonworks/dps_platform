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

package com.hortonworks.dataplane.cs

import com.hortonworks.dataplane.commons.domain.Atlas.{
  AtlasAttribute,
  AtlasFilter,
  AtlasSearchQuery
}
import com.hortonworks.dataplane.cs.services.Filters
import org.scalatest._

class FilterSpec extends FlatSpec with Matchers {

  "Filters" should "construct a DSL query based on a single input" in {

    val output = Filters.query(AtlasSearchQuery(
      Seq(AtlasFilter(AtlasAttribute("owner", "string"), "equals", "admin"))))

    assert(output == "where owner='admin'")

  }


  "Filters" should "construct and lowercase a DSL query if not default" in {

    val output = Filters.query(AtlasSearchQuery(
      Seq(AtlasFilter(AtlasAttribute("owner", "string"), "equals", "Admin"))),false)

    assert(output == "where owner='Admin'")

  }


  it should "construct a DSL query by combining 2 filters as AND" in {

    val output = Filters.query(AtlasSearchQuery(
      Seq(AtlasFilter(AtlasAttribute("owner", "string"), "equals", "admin"),
        AtlasFilter(AtlasAttribute("name", "string"), "equals", "trucks"))))

    assert(output == "where owner='admin' and name='trucks'")

  }

  it should "construct a DSL query by combining more than 2 filters as AND" in {

    val output = Filters.query(AtlasSearchQuery(
      Seq(AtlasFilter(AtlasAttribute("owner", "string"), "equals", "admin"),
        AtlasFilter(AtlasAttribute("name", "string"), "equals", "trucks"),
        AtlasFilter(AtlasAttribute("created", "date"), "gte", "21-01-2017"))))

    assert(output == "where owner='admin' and name='trucks' and created>=21-01-2017")

  }


  it should "construct DSL queries for non string equalities" in {
    val output = Filters.query(AtlasSearchQuery(
      Seq(AtlasFilter(AtlasAttribute("owner", "string"), "equals", "admin"),
        AtlasFilter(AtlasAttribute("temporary", "boolean"), "equals", "false"))))

    assert(output == "where owner='admin' and temporary=false")
  }


  it should "construct DSL queries for partial String matches" in {
    val output = Filters.query(AtlasSearchQuery(
      Seq(AtlasFilter(AtlasAttribute("owner", "string"), "contains", "dmi"),AtlasFilter(AtlasAttribute("owner", "string"), "startsWith", "adm"),AtlasFilter(AtlasAttribute("owner", "string"), "endsWith", "min"),
        AtlasFilter(AtlasAttribute("temporary", "boolean"), "equals", "false"))))

    assert(output == "where owner like '*dmi*' and owner like 'adm*' and owner like '*min' and temporary=false")
  }

}
