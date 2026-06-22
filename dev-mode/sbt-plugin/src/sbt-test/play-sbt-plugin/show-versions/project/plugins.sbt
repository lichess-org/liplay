// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

addSbtPlugin("org.lichess.play" % "sbt-scripted-tools" % sys.props("project.version"))
lazy val plugins = (project in file(".")).settings(scalaVersion := "3.8.4")
