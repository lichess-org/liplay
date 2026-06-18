// Copyright (C) Lightbend Inc. <https://www.lightbend.com>

lazy val plugins = (project in file("."))

val mima              = "1.1.1"
val scalafmt          = "2.4.6"
val sbtTwirl: String  = sys.props.getOrElse("twirl.version", "1.6.0-M7") // sync with documentation/project/plugins.sbt
val interplay: String = sys.props.getOrElse("interplay.version", "3.1.0-RC5")

logLevel := Level.Warn

scalacOptions ++= Seq("-deprecation", "-language:_")

addSbtPlugin("com.typesafe.play" % "interplay"       % interplay)
addSbtPlugin("com.typesafe.play" % "sbt-twirl"       % sbtTwirl)
addSbtPlugin("com.typesafe"      % "sbt-mima-plugin" % mima)
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"    % scalafmt)

resolvers += Resolver.typesafeRepo("releases")
