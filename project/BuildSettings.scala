/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
import java.util.regex.Pattern
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport._
import sbt.plugins.SbtPlugin

import scala.sys.process.stringToProcess
import scala.util.control.NonFatal

object BuildSettings {

  val scala3 = "3.8.4"
  val sbt2 = "2.0.1"

  /** These settings are used by all projects. */
  def playCommonSettings: Seq[Setting[?]] = Def.settings(
    organization := "com.github.lichess-org.liplay",
    scalaVersion := scala3,
    (Compile / doc / scalacOptions) := Seq("-no-java-comments"),
    (Test / fork) := true,
    (Test / parallelExecution) := false,
    (Test / test / testListeners) := Nil,
    (Test / javaOptions) ++= Seq("-XX:MaxMetaspaceSize=384m", "-Xmx512m", "-Xms128m"),
    testOptions ++= Seq(
      Tests.Argument(TestFrameworks.Specs2, "showtimes"),
      Tests.Argument(TestFrameworks.JUnit, "-v")
    ),
    version := "3.2.0.1-SNAPSHOT"
  )

  def playScriptedSettings: Seq[Setting[?]] = Seq(
    // Don't automatically publish anything.
    // The test-sbt-plugins-* scripts publish before running the scripted tests.
    // When developing the sbt plugins:
    // * run a publishLocal in the root project to get everything
    // * run a publishLocal in the changes projects for fast feedback loops
    scriptedDependencies := (()), // drop Test/compile & publishLocal
    scriptedBufferLog := false,
    // The scripted test projects reference the plugin via sys.props("project.version"), and
    // ScriptedTools.scalaVersionFromJavaProperties() reads scala.version/scala.crossversions.
    // interplay used to provide these; reproduce them from the build (single source of truth).
    scriptedLaunchOpts += s"-Dproject.version=${version.value}",
    scriptedLaunchOpts += s"-Dscala.version=${scalaVersion.value}",
    scriptedLaunchOpts += s"-Dscala.crossversions=${crossScalaVersions.value.mkString(" ")}",
    scriptedLaunchOpts ++= Seq(
      s"-Dsbt.boot.directory=${file(sys.props("user.home")) / ".sbt" / "boot"}",
      "-Xmx512m",
      "-XX:MaxMetaspaceSize=512m",
      "-XX:HeapDumpPath=/tmp/",
      "-XX:+HeapDumpOnOutOfMemoryError"
    ),
    scripted := scripted.tag(Tags.Test).evaluated
  )

  /** A project that runs in the sbt runtime. */
  def PlaySbtProject(name: String, dir: String): Project = {
    Project(name, file(dir))
      .settings(
        playCommonSettings,
        scalaVersion := scala3
      )
  }

  /** A project that *is* an sbt plugin. */
  def PlaySbtPluginProject(name: String, dir: String): Project = {
    Project(name, file(dir))
      .enablePlugins(SbtPlugin)
      .settings(
        playCommonSettings,
        playScriptedSettings,
        scalaVersion := scala3,
        (pluginCrossBuild / sbtVersion) := sbt2,
        (Test / fork) := false
      )
  }

}
