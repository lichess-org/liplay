/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.concurrent

import org.apache.pekko.Done
import org.apache.pekko.actor.setup.ActorSystemSetup
import org.apache.pekko.actor.setup.Setup
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.BootstrapSetup
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import org.slf4j.LoggerFactory
import play.api.*
import play.api.inject.ApplicationLifecycle

import scala.concurrent.*
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.annotation.nowarn

/**
 * Components for configuring Pekko.
 */
trait PekkoComponents:
  def environment: Environment

  def configuration: Configuration

  @deprecated("Since Play 2.7.0 this is no longer required to create an ActorSystem.", "2.7.0")
  def applicationLifecycle: ApplicationLifecycle

  lazy val actorSystem: ActorSystem =
    ActorSystemProvider.start(environment.classLoader, configuration, Nil*)

  lazy val classicActorSystemProvider: ClassicActorSystemProvider = actorSystem

  @nowarn
  lazy val coordinatedShutdown: CoordinatedShutdown =
    CoordinatedShutdownProvider.build(actorSystem, applicationLifecycle)

  implicit lazy val materializer: Materializer = Materializer.matFromSystem(using actorSystem)

  implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher

/**
 * Pekko Typed components.
 */
trait PekkoTypedComponents:
  def actorSystem: ActorSystem
  implicit lazy val scheduler: Scheduler =
    import org.apache.pekko.actor.typed.scaladsl.adapter.*
    actorSystem.scheduler.toTyped

object ActorSystemProvider:
  type StopHook = () => Future[?]

  private val logger = LoggerFactory.getLogger("play.api.libs.concurrent.ActorSystemProvider")

  case object ApplicationShutdownReason extends CoordinatedShutdown.Reason

  /**
   * Start an ActorSystem, using the given configuration, ClassLoader, and optional additional ActorSystem
   * Setups.
   *
   * @return
   *   The ActorSystem and a function that can be used to stop it.
   */
  def start(classLoader: ClassLoader, config: Configuration, additionalSetups: Setup*): ActorSystem =
    val exitJvmPath = "pekko.coordinated-shutdown.exit-jvm"
    if config.get[Boolean](exitJvmPath) then
      // When this setting is enabled, there'll be a deadlock at shutdown. Therefore, we
      // prevent the creation of the Actor System.
      val errorMessage =
        s"""Can't start Play: detected "$exitJvmPath = on". """ +
          s"""Using "$exitJvmPath = on" in Play may cause a deadlock when shutting down. """ +
          s"""Please set "$exitJvmPath = off""""
      logger.error(errorMessage)
      throw config.reportError(exitJvmPath, errorMessage)

    val pekkoConfig: Config =
      // normalize timeout values for Pekko's use
      // TODO: deprecate this setting (see https://github.com/playframework/playframework/issues/8442)
      val playTimeoutKey = "play.pekko.shutdown-timeout"
      val playTimeoutDuration = Try(config.get[Duration](playTimeoutKey)).getOrElse(Duration.Inf)

      // Typesafe config used internally by Pekko doesn't support "infinite".
      // Also, the value expected is an integer so can't use Long.MaxValue.
      // Finally, Pekko requires the delay to be less than a certain threshold.
      val pekkoMaxDelay = Int.MaxValue / 1000
      val pekkoMaxDuration = Duration(pekkoMaxDelay, "seconds")
      val normalisedDuration = playTimeoutDuration.min(pekkoMaxDuration)
      val pekkoTimeoutDuration = java.time.Duration.ofMillis(normalisedDuration.toMillis)

      val pekkoTimeoutKey = "pekko.coordinated-shutdown.phases.actor-system-terminate.timeout"
      // Need to manually merge and override pekkoTimeoutKey because `null` is meaningful in playTimeoutKey
      config.underlying.withValue(pekkoTimeoutKey, ConfigValueFactory.fromAnyRef(pekkoTimeoutDuration))

    val name = config.get[String]("play.pekko.actor-system")

    val bootstrapSetup = BootstrapSetup(Some(classLoader), Some(pekkoConfig), None)
    val actorSystemSetup = ActorSystemSetup(bootstrapSetup +: additionalSetups*)

    logger.debug(s"Starting application default Pekko system: $name")
    ActorSystem(name, actorSystemSetup)

private[play] object CoordinatedShutdownProvider:
  private val logger = LoggerFactory.getLogger("play.api.libs.concurrent.CoordinatedShutdownProvider")

  /**
   * Build the [[CoordinatedShutdown]] for the given actor system, registering the application lifecycle stop
   * hooks as a shutdown phase.
   */
  @nowarn // for applicationLifecycle.stop()
  def build(actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle): CoordinatedShutdown =
    logWarningWhenRunPhaseConfigIsPresent(actorSystem)

    implicit val ec = actorSystem.dispatcher

    val cs = CoordinatedShutdown(actorSystem)
    // Once the ActorSystem is built we can register the ApplicationLifecycle stopHooks as a CoordinatedShutdown phase.
    //
    CoordinatedShutdown(actorSystem)
      .addTask(CoordinatedShutdown.PhaseServiceStop, "application-lifecycle-stophook")(() =>
        applicationLifecycle.stop().map(_ => Done)
      )

    cs

  private def logWarningWhenRunPhaseConfigIsPresent(actorSystem: ActorSystem): Unit =
    val config = actorSystem.settings.config
    if config.hasPath("play.pekko.run-cs-from-phase") then
      logger.warn(
        "Configuration 'play.pekko.run-cs-from-phase' was deprecated and has no effect. Play now runs all the CoordinatedShutdown phases."
      )
