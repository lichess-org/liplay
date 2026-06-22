// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

import BuildSettings._
import Dependencies._
import Generators._
import sbt.Keys.parallelExecution
import sbt._
import sbt.io.Path._

lazy val SbtRoutesCompilerProject = PlaySbtProject("Sbt-Routes-Compiler", "dev-mode/routes-compiler")
  .enablePlugins(SbtTwirl)
  .settings(
    libraryDependencies ++= routesCompilerDependencies(scalaVersion.value),
    TwirlKeys.templateFormats := Map("twirl" -> "play.routes.compiler.ScalaFormat")
  )

lazy val PlayExceptionsProject = PlayNonCrossBuiltProject("Play-Exceptions", "core/play-exceptions")

lazy val SbtPluginProject = PlaySbtPluginProject("Sbt-Plugin", "dev-mode/sbt-plugin")
  .enablePlugins(SbtPlugin)
  .settings(
    libraryDependencies ++= sbtDependencies((pluginCrossBuild / sbtVersion).value, scalaVersion.value),
    (Compile / sourceGenerators) += Def.task {
      PlayVersion(
        version.value,
        (SbtRoutesCompilerProject / scalaVersion).value,
        sbtVersion.value,
        (Compile / sourceManaged).value
      )
    }.taskValue
  )
  .dependsOn(SbtRoutesCompilerProject, PlayExceptionsProject)

// Provides the ScriptedTools auto plugin used by the sbt-plugin scripted tests.
lazy val SbtScriptedToolsProject = PlaySbtPluginProject("Sbt-Scripted-Tools", "dev-mode/sbt-scripted-tools")
  .enablePlugins(SbtPlugin)
  .dependsOn(SbtPluginProject)

lazy val PlayFramework = Project("Play-Framework", file("."))
  .settings(
    playCommonSettings,
    scalaVersion := scala3,
    crossScalaVersions := Nil,
    (Global / concurrentRestrictions) += Tags.limit(Tags.Test, 1),
    commands += Commands.quickPublish,
    publish / skip := true,
  )
  .aggregate(
    PlayExceptionsProject,
    SbtRoutesCompilerProject,
    SbtPluginProject,
    SbtScriptedToolsProject,
  )
