// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

import BuildSettings._
import Dependencies._
import Generators._
import sbt.Keys.parallelExecution
import sbt._
import sbt.io.Path._

lazy val RoutesCompilerProject = PlayDevelopmentProject("Routes-Compiler", "dev-mode/routes-compiler")
  .enablePlugins(SbtTwirl)
  .settings(
    libraryDependencies ++= routesCompilerDependencies(scalaVersion.value),
    TwirlKeys.templateFormats := Map("twirl" -> "play.routes.compiler.ScalaFormat")
  )

lazy val SbtRoutesCompilerProject = PlaySbtProject("Sbt-Routes-Compiler", "dev-mode/routes-compiler")
  .enablePlugins(SbtTwirl)
  .settings(
    target := target.value / "sbt-routes-compiler",
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
        Dependencies.akkaVersion,
        Dependencies.akkaHttpVersion,
        (Compile / sourceManaged).value
      )
    }.taskValue
  )
  .dependsOn(SbtRoutesCompilerProject, PlayExceptionsProject)

// Provides the ScriptedTools auto plugin used by the sbt-plugin scripted tests.
lazy val SbtScriptedToolsProject = PlaySbtPluginProject("Sbt-Scripted-Tools", "dev-mode/sbt-scripted-tools")
  .enablePlugins(SbtPlugin)
  .dependsOn(SbtPluginProject)

// These projects are aggregate by the root project and every
// task (compile, test, publish, etc) executed for the root
// project will also be executed for them:
// https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Aggregation
//
// Keep in mind that specific configurations (like skip in publish) will be respected.
lazy val userProjects = Seq[ProjectReference](
  RoutesCompilerProject,
  PlayExceptionsProject,
)
lazy val nonUserProjects = Seq[ProjectReference](
  SbtRoutesCompilerProject,
  SbtPluginProject,
  SbtScriptedToolsProject,
)

lazy val PlayFramework = Project("Play-Framework", file("."))
  .settings(
    playCommonSettings,
    scalaVersion := (SbtRoutesCompilerProject / scalaVersion).value,
    crossScalaVersions := Nil,
    (Global / concurrentRestrictions) += Tags.limit(Tags.Test, 1),
    libraryDependencies ++= runtime(scalaVersion.value),
    commands += Commands.quickPublish,
    publish / skip := true,
  )
  .aggregate((userProjects ++ nonUserProjects): _*)
