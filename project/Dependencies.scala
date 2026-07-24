/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt._
import Keys._
import snapshot4s.BuildInfo.snapshot4sVersion

object Dependencies {
  val pekkoVersion = "1.6.0"

  val logback = "ch.qos.logback" % "logback-classic" % "1.6.0"

  val specs2Version = "4.23.0"
  val specs2CoreDeps = Seq(
    "specs2-core",
    "specs2-junit"
  ).map("org.specs2" %% _ % specs2Version)
  val specs2Deps = specs2CoreDeps

  val specsMatcherExtra = "org.specs2" %% "specs2-matcher-extra" % specs2Version

  val scalacheckDependencies = Seq(
    "org.specs2" %% "specs2-scalacheck" % specs2Version % Test,
    "org.scalacheck" %% "scalacheck" % "1.19.0" % Test
  )

  val playJson = "org.playframework" %% "play-json" % "3.0.6"

  val slf4jVersion = "2.0.18"
  val slf4j = Seq("slf4j-api", "jul-to-slf4j", "jcl-over-slf4j").map("org.slf4j" % _ % slf4jVersion)
  val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % slf4jVersion

  val guava = "com.google.guava" % "guava" % "33.6.0-jre"
  val findBugs = "com.google.code.findbugs" % "jsr305" % "3.0.2" // Needed by guava
  val mockitoAll = "org.mockito" % "mockito-core" % "4.11.0"

  val scalaParserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0"

  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.4.0"

  val junitInterface = "com.github.sbt" % "junit-interface" % "0.13.3"
  val junit = "junit" % "junit" % "4.13.2"

  def runtime(scalaVersion: String) =
    slf4j ++
      Seq("pekko-actor", "pekko-actor-typed", "pekko-slf4j")
        .map("org.apache.pekko" %% _ % pekkoVersion) ++
      Seq("pekko-testkit", "pekko-actor-testkit-typed")
        .map("org.apache.pekko" %% _ % pekkoVersion % Test) ++
      Seq(
        playJson,
        guava,
        scalaXml,
        scalaParserCombinators
      ) ++ specs2Deps.map(_ % Test)

  val nettyVersion = "4.2.16.Final"

  val netty = Seq(
    "org.playframework.netty" % "netty-reactive-streams-http" % "3.1.0-M1",
    ("io.netty" % "netty-transport-native-epoll" % nettyVersion).classifier("linux-x86_64")
  ) ++ specs2Deps.map(_ % Test)

  val cookieEncodingDependencies = slf4j

  val jimfs = "com.google.jimfs" % "jimfs" % "1.3.1"

  val typesafeConfig = "com.typesafe" % "config" % "1.4.9"

  val streamsDependencies = Seq(
    "org.reactivestreams" % "reactive-streams" % "1.0.4",
    "org.apache.pekko" %% "pekko-stream" % pekkoVersion
  ) ++ specs2CoreDeps.map(_ % Test)

  val playServerDependencies = specs2Deps.map(_ % Test) ++ Seq(
    guava % Test,
    logback % Test
  )

  // ----- Development-mode tooling (routes compiler + sbt-2 plugin) -----

  val snapshot4sMunit = "com.siriusxm" %% "snapshot4s-munit" % snapshot4sVersion

  val routesCompilerDependencies =
    Seq(scalaParserCombinators, logback % Test) ++
      specs2CoreDeps.map(_ % Test) ++ Seq(specsMatcherExtra % Test, snapshot4sMunit % Test)

  private def sbtPluginDep(moduleId: ModuleID, sbtVersion: String, scalaVersion: String) = {
    Defaults.sbtPluginExtra(
      moduleId,
      CrossVersion.binarySbtVersion(sbtVersion),
      CrossVersion.binaryScalaVersion(scalaVersion)
    )
  }

  val sbtDependencies =
    Seq(
      slf4jSimple,
      logback % Test
    ) ++ specs2CoreDeps.map(_ % Test)

}
