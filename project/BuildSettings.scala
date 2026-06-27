/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
import java.util.regex.Pattern
// import interplay._
// import interplay.PlayBuildBase.autoImport._
// import interplay.ScalaVersions._
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport._

import scala.sys.process.stringToProcess
import scala.util.control.NonFatal

object BuildSettings {

  val playVersion = "2.8.18-lila_3.22"

  val SourcesApplication = config("sources").hide

  /** These settings are used by all projects. */
  def playCommonSettings: Seq[Setting[_]] = Def.settings(
    scalaVersion    := "3.7.0",
    javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:unchecked", "-Xlint:deprecation", "--release", "21"),
    (Compile / scalacOptions)       := Seq("-release:21"),
    (Compile / doc / scalacOptions) := Seq("-no-java-comments"),
    (Test / fork)                   := true,
    (Test / parallelExecution)      := false,
    (Test / test / testListeners)   := Nil,
    (Test / javaOptions) ++= Seq("-XX:MaxMetaspaceSize=384m", "-Xmx512m", "-Xms128m"),
    testOptions ++= Seq(
      Tests.Argument(TestFrameworks.Specs2, "showtimes"),
      Tests.Argument(TestFrameworks.JUnit, "-v")
    ),
    version := playVersion
  )

  /** A project that is in the Play runtime. */
  def PlayCrossBuiltProject(name: String, dir: String): Project = {
    Project(name, file(dir))
    .settings(playCommonSettings)
  }

}
