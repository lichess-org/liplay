/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.sbt

import scala.jdk.CollectionConverters._

import sbt._
import sbt.Keys._
import sbt.io.syntax._
import sbt.librarymanagement.UpdateLogging
import sbt.librarymanagement.Configurations.{ Compile, Runtime, Test }
import sbt.librarymanagement.syntax._

import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys._
import play.sbt.PlayInternalKeys._
import play.sbt.routes.RoutesKeys
import play.sbt.routes.RoutesCompiler.autoImport._
import play.twirl.sbt.Import.TwirlKeys._

import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.Keys._

object PlaySettings {
  private val slash = new sbt.SlashSyntax {}
  import slash._

  lazy val defaultScalaSettings = Seq.empty[Def.Setting[?]]

  lazy val serviceGlobalSettings: Seq[Def.Setting[?]] = Seq()

  // Settings for a Play service (not a web project)
  lazy val serviceSettings: Seq[Def.Setting[?]] = Def.settings(
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-encoding", "utf8"),
    Compile / javacOptions ++= Seq("-encoding", "utf8", "-g"),
    playPlugin := false,
    Compile / doc / javacOptions := List("-encoding", "utf8"),
    libraryDependencies += {
      if (playPlugin.value)
        "com.typesafe.play" %% "play" % PlayVersion.current % "provided"
      else
        "com.typesafe.play" %% "play-server" % PlayVersion.current
    },
    Test / fork := true,
    shellPrompt := PlayCommands.playPrompt,
    // all dependencies from outside the project (all dependency jars)
    playDependencyClasspath := Def.uncached((Runtime / externalDependencyClasspath).value),
    playCommonClassloader := Def.uncached(PlayCommands.playCommonClassloaderTask.value),
    ivyLoggingLevel := UpdateLogging.DownloadOnly,
    playDefaultPort := 9000,
    playDefaultAddress := "0.0.0.0",
    // Settings
    devSettings := Nil,
    // Native packaging
    Compile / mainClass := Some("play.core.server.ProdServerStart"),
    // Adds the Play application directory to the command line args passed to Play
    bashScriptExtraDefines += "addJava \"-Duser.dir=$(realpath \"$(cd \"${app_home}/..\"; pwd -P)\"  $(is_cygwin && echo \"fix\"))\"\n",
    // by default, compile any routes files in the root named "routes" or "*.routes"
    Compile / RoutesKeys.routes / sources ++= {
      val dirs = (Compile / unmanagedResourceDirectories).value
      (dirs * "routes").get() ++ (dirs * "*.routes").get()
    },
  )

  lazy val webSettings = Seq[Def.Setting[?]](
    routesImport ++= Seq("controllers.Assets.Asset")
  )
}
