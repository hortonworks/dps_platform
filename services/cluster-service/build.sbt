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

import sbt.ExclusionRule

name := """cluster-service"""

version := "1.0"

scalaVersion := "2.11.12"

enablePlugins(JavaAppPackaging)

resolvers += "Private HW nexus" at "http://nexus-private.hortonworks.com/nexus/content/groups/public/"

mainClass in Compile := Some("com.hortonworks.dataplane.cs.ClusterService")

libraryDependencies ++= Seq(
  "com.google.inject" % "guice" % "4.1.0",
  "com.typesafe.play" % "play-ws_2.11" % "2.5.13",
  "com.typesafe.play" % "play-json_2.11" % "2.6.0-M3",
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-http" % "10.0.5",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.5",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "com.hortonworks.dataplane" %% "rest-mock" % "1.0" % "test",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.25",
  "org.scalatest" % "scalatest_2.11" % "3.0.1" % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % Test,
  "org.scala-lang" % "scala-reflect" % "2.11.12", // https://www.scala-lang.org/news/security-update-nov17.html
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.5", // https://github.com/FasterXML/jackson-databind/issues/1931
  "ch.qos.logback" % "logback-classic" % "1.2.3" // https://issues.apache.org/jira/browse/ARROW-1240
  )

libraryDependencies :=
  libraryDependencies.value
    .map(
      _.excludeAll(
        ExclusionRule("com.google.code.findbugs", "annotations"),
        ExclusionRule("org.xerial.snappy", "snappy-java")
      )
    )
