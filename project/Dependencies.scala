/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt._
import Keys._

object Dependencies {

  // sync with documentation/project/plugins.sbt
  val sbtTwirlVersion: String = sys.props.getOrElse("twirl.version", "1.6.0-M7")
  // The sbt-2 twirl plugin moved to the org.playframework.twirl org (see sbtDependencies).
  val sbt2TwirlVersion = "2.1.0-M9"
  // when updating sbtNativePackager version, be sure to also update the documentation links in
  // documentation/manual/working/commonGuide/production/Deploying.md
  val sbtNativePackagerVersion = "1.11.4"


  val logback = "ch.qos.logback" % "logback-classic" % "1.4.4"

  val specs2Version = "4.17.0"
  val specs2CoreDeps = Seq(
    "specs2-core",
    "specs2-junit"
  ).map("org.specs2" %% _ % specs2Version)
  val specs2Deps = specs2CoreDeps ++ Seq(
    "specs2-mock"
  ).map("org.specs2" %% _ % specs2Version)

  val specsMatcherExtra = "org.specs2" %% "specs2-matcher-extra" % specs2Version

  val scalacheckDependencies = Seq(
    "org.specs2"     %% "specs2-scalacheck" % specs2Version % Test,
    "org.scalacheck" %% "scalacheck"        % "1.17.0"      % Test
  )

  val slf4jVersion = "2.0.3"
  val slf4j        = Seq("slf4j-api", "jul-to-slf4j", "jcl-over-slf4j").map("org.slf4j" % _ % slf4jVersion)
  val slf4jSimple  = "org.slf4j" % "slf4j-simple" % slf4jVersion

  def scalaParserCombinators(scalaVersion: String) =
    Seq("org.scala-lang.modules" %% "scala-parser-combinators" % {
      CrossVersion.partialVersion(scalaVersion) match {
        case Some((2, _)) => "1.1.2"
        case _            => "2.1.1"
      }
    })

  def routesCompilerDependencies(scalaVersion: String) = {
    specs2CoreDeps.map(_ % Test) ++ Seq(specsMatcherExtra % Test) ++ scalaParserCombinators(scalaVersion) ++ (logback % Test :: Nil)
  }

  private def sbtPluginDep(moduleId: ModuleID, sbtVersion: String, scalaVersion: String) = {
    Defaults.sbtPluginExtra(
      moduleId,
      CrossVersion.binarySbtVersion(sbtVersion),
      CrossVersion.binaryScalaVersion(scalaVersion)
    )
  }

  val typesafeConfig = "com.typesafe" % "config" % "1.4.2"

  def sbtDependencies(sbtVersion: String, scalaVersion: String) = {
    def sbtDep(moduleId: ModuleID) = sbtPluginDep(moduleId, sbtVersion, scalaVersion)

    Seq(
      typesafeConfig,
      slf4jSimple,
      // sbt-2 twirl plugin lives under the org.playframework.twirl org (was com.typesafe.play on sbt-1).
      sbtDep("org.playframework.twirl" % "sbt-twirl"           % sbt2TwirlVersion),
      sbtDep("com.github.sbt"          % "sbt-native-packager" % sbtNativePackagerVersion),
      // NB: sbt-web is intentionally dropped — it has no sbt-2 build and the ported plugin sources
      // no longer use it.
      logback             % Test
      // specs2CoreDeps (not specs2Deps): specs2-mock has no Scala-3 build at 4.17.0, and the
      // sbt-plugin has no test sources using it.
    ) ++ specs2CoreDeps.map(_ % Test)
  }

}
