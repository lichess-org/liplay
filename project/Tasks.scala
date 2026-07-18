/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.Keys._
import sbt._

object Generators {
  // Generates a scala file that contains the play version for use at runtime.
  def PlayVersion(
      version: String,
      scalaVersion: String,
      sbtVersion: String,
      pekkoVersion: String,
      dir: File
  ): Seq[File] = {
    val file = dir / "PlayVersion.scala"
    val scalaSource =
      s"""|package play.core
          |
          |object PlayVersion {
          |  val current = "$version"
          |  val scalaVersion = "$scalaVersion"
          |  val sbtVersion = "$sbtVersion"
          |  val pekkoVersion = "$pekkoVersion"
          |}
          |""".stripMargin

    if (!file.exists() || IO.read(file) != scalaSource) {
      IO.write(file, scalaSource)
    }

    Seq(file)
  }
}
