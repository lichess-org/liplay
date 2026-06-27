// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

lazy val plugins = (project in file(".")).settings(
)

enablePlugins(BuildInfoPlugin)

val scalafmt         = "2.4.6"
val sbtTwirl: String = sys.props.getOrElse("twirl.version", "1.6.1") // sync with documentation/project/plugins.sbt

buildInfoKeys := Seq[BuildInfoKey](
  "sbtTwirlVersion" -> sbtTwirl,
)

logLevel := Level.Warn

scalacOptions ++= Seq("-deprecation", "-language:_")

addSbtPlugin("com.typesafe.play" % "sbt-twirl"       % sbtTwirl)
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"    % scalafmt)
