/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.sbt

import java.nio.file.Path

import play.sbt.PlayInternalKeys._
import sbt.Keys._
import sbt._
import sbt.librarymanagement.Configurations.Compile
import sbt.ProjectExtra.extract

object PlayCommands {
  private val slash = new sbt.SlashSyntax {}
  import slash._

  // ----- Play prompt

  val playPrompt = { (state: State) =>
    val extracted = Project.extract(state)
    import extracted._

    (currentRef / name)
      .get(structure.data)
      .map { name =>
        "[" + Colors.cyan(name) + "] $ "
      }
      .getOrElse("> ")
  }

  // ----- Play commands

  private[this] var commonClassLoader: ClassLoader = _

  val playCommonClassloaderTask = Def.task {
    val conv      = fileConverter.value
    val classpath = (Compile / dependencyClasspath).value.map(e => conv.toPath(e.data).toFile)
    val log       = streams.value.log
    lazy val commonJars: PartialFunction[java.io.File, java.net.URL] = {
      case jar if jar.getName.startsWith("h2-") || jar.getName == "h2.jar" => jar.toURI.toURL
    }

    if (commonClassLoader == null) {
      // The parent of the system classloader *should* be the extension classloader:
      // http://www.onjava.com/pub/a/onjava/2005/01/26/classloading.html
      // We use this because this is where things like Nashorn are located. We don't use the system classloader
      // because it will be polluted with the sbt launcher and dependencies of the sbt launcher.
      // See https://github.com/playframework/playframework/issues/3420 for discussion.
      val parent = ClassLoader.getSystemClassLoader.getParent
      log.debug("Using parent loader for play common classloader: " + parent)

      commonClassLoader = new java.net.URLClassLoader(classpath.collect(commonJars).toArray, parent) {
        override def toString = "Common ClassLoader: " + getURLs.map(_.toString).mkString(",")
      }
    }

    commonClassLoader
  }

  val h2Command = Command.command("h2-browser") { (state: State) =>
    try {
      val extracted    = Project.extract(state)
      val commonLoader = EvaluateTask(
        extracted.structure,
        playCommonClassloader.scopedKey,
        state,
        extracted.currentRef
      ).get._2.toEither.toOption.get
      val h2ServerClass = commonLoader.loadClass("org.h2.tools.Server")
      h2ServerClass.getMethod("main", classOf[Array[String]]).invoke(null, Array.empty[String])
    } catch {
      case _: ClassNotFoundException =>
        state.log.error(
          s"""|H2 Dependency not loaded, please add H2 to your Classpath!
              |Take a look at https://www.playframework.com/documentation/${play.core.PlayVersion.current}/Developing-with-the-H2-Database#H2-database on how to do it.""".stripMargin
        )
      case e: Exception => e.printStackTrace()
    }
    state
  }
}
