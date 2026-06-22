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

  val playVersion = "2.0.0-RC2"

  // Scala/sbt versions previously provided by interplay's ScalaVersions/SbtVersions.
  val scala213 = "2.13.10"
  // The sbt-plugin and sbt-routes-compiler are built against the sbt 2.0 API (Scala 3). The
  // meta-build itself stays on sbt 1.12 (project/build.properties); only the plugin project's
  // `pluginCrossBuild / sbtVersion` selects the sbt-2 jars to compile against.
  val scala3 = "3.8.4"
  val sbt2   = "2.0.0"

  /** File header settings.  */
  private def fileUriRegexFilter(pattern: String): FileFilter = new FileFilter {
    val compiledPattern = Pattern.compile(pattern)
    override def accept(pathname: File): Boolean = {
      val uriString = pathname.toURI.toString
      compiledPattern.matcher(uriString).matches()
    }
  }

  private val VersionPattern = """^(\d+).(\d+).(\d+)(-.*)?""".r

  def evictionSettings: Seq[Setting[_]] = Seq(
    // This avoids a lot of dependency resolution warnings to be showed.
    (update / evictionWarningOptions) := EvictionWarningOptions.default
      .withWarnTransitiveEvictions(false)
      .withWarnDirectEvictions(false)
  )

  val SourcesApplication = config("sources").hide

  /** These settings are used by all projects. */
  def playCommonSettings: Seq[Setting[_]] = Def.settings(
    organization := "org.lichess.play",
    ivyLoggingLevel := UpdateLogging.DownloadOnly,
    resolvers ++= Resolver.sonatypeOssRepos("releases"), // sync ScriptedTools.scala
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
      Resolver.typesafeIvyRepo("releases"),
      Resolver.sbtPluginRepo("releases"), // weird sbt-pgp/play docs/vegemite issue
    ),
    evictionSettings,
    ivyConfigurations ++= Seq(SourcesApplication),
    javacOptions ++= Seq("-encoding", "UTF-8"),
    // -Xlint is a javac flag that javadoc rejects, so scope it to compile (keeps `doc`/publishLocal working).
    (Compile / compile / javacOptions) ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    (Compile / doc / scalacOptions) := {
      // disable the new scaladoc feature for scala 2.12+ (https://github.com/scala/scala-dev/issues/249 and https://github.com/scala/bug/issues/11340)
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v >= 12 => Seq("-no-java-comments")
        case _                       => Seq()
      }
    },
    (Test / fork) := true,
    (Test / parallelExecution) := false,
    (Test / test / testListeners) := Nil,
    (Test / javaOptions) ++= Seq("-XX:MaxMetaspaceSize=384m", "-Xmx512m", "-Xms128m"),
    testOptions ++= Seq(
      Tests.Argument(TestFrameworks.Specs2, "showtimes"),
      Tests.Argument(TestFrameworks.JUnit, "-v")
    ),
    version := playVersion
  )

  /**
   * These settings are used by all projects that are part of the runtime, as opposed to the development mode of Play.
   */
  def playRuntimeSettings: Seq[Setting[_]] = Def.settings(
    playCommonSettings,
    (Compile / unmanagedSourceDirectories) += {
      val suffix = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((x, y)) => s"$x.$y"
        case None         => scalaBinaryVersion.value
      }
      (Compile / sourceDirectory).value / s"scala-$suffix"
    }
  )

  def disablePublishing = Def.settings(
    (publish / skip) := true,
    publishLocal := {},
  )

  /** A project that is shared between the sbt runtime and the Play runtime. */
  def PlayNonCrossBuiltProject(name: String, dir: String): Project = {
    Project(name, file(dir))
      .settings(playRuntimeSettings: _*)
      .settings(
        autoScalaLibrary := false,
        crossPaths := false,
        scalaVersion := scala213,
        crossScalaVersions := Seq(scala213)
      )
  }

  def playScriptedSettings: Seq[Setting[_]] = Seq(
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
      "-XX:+HeapDumpOnOutOfMemoryError",
    ),
    scripted := scripted.tag(Tags.Test).evaluated,
  )

  /** A project that runs in the sbt runtime. */
  def PlaySbtProject(name: String, dir: String): Project = {
    Project(name, file(dir))
      .settings(
        playCommonSettings,
        scalaVersion := scala3,
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
        // Compile the plugin against the sbt 2.0 API even though the launcher is sbt 1.12.
        (pluginCrossBuild / sbtVersion) := sbt2,
        (Test / fork) := false,
      )
  }
}
