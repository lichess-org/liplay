// Copyright (C) Lightbend Inc. <https://www.lightbend.com>

lazy val plugins = (project in file("."))

val scalafmt          = "2.4.6"
val sbtTwirl: String  = sys.props.getOrElse("twirl.version", "1.6.0-M7") // sync with documentation/project/plugins.sbt

logLevel := Level.Warn

scalacOptions ++= Seq("-deprecation", "-language:_")

// We only build/test the sbt plugin here — no publishing. Interplay used to provide the Play*
// project bases, but it transitively pulls in a publishing toolchain (sbt-ci-release ->
// sbt-dynver, sbt-pgp, Sonatype) that is anchored to the now-defunct repo.scala-sbt.org /
// repo.typesafe.com resolvers and partly absent from Maven Central, so it fails to resolve on a
// cold CI cache. Its build settings are reproduced as plain sbt settings in BuildSettings.scala,
// and the plugin itself is the built-in sbt SbtPlugin.
addSbtPlugin("com.typesafe.play" % "sbt-twirl"    % sbtTwirl)
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % scalafmt)
