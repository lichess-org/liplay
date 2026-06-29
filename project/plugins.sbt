// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

lazy val plugins = (project in file(".")).settings(
)

enablePlugins(BuildInfoPlugin)

addSbtPlugin("com.typesafe.play" % "sbt-twirl" % "1.6.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
