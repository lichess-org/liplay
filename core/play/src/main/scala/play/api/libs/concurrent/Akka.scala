/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.concurrent

import akka.Done
import akka.actor.setup.ActorSystemSetup
import akka.actor.setup.Setup
import akka.actor.typed.Scheduler
import akka.actor.ActorSystem
import akka.actor.BootstrapSetup
import akka.actor.ClassicActorSystemProvider
import akka.actor.CoordinatedShutdown
import akka.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import org.slf4j.LoggerFactory
import play.api.*
import play.api.inject.ApplicationLifecycle

import scala.concurrent.*
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.annotation.nowarn

/**
 * Components for configuring Akka.
 */
trait AkkaComponents:
  def environment: Environment

  def configuration: Configuration

  @deprecated("Since Play 2.7.0 this is no longer required to create an ActorSystem.", "2.7.0")
  def applicationLifecycle: ApplicationLifecycle

  lazy val actorSystem: ActorSystem = new ActorSystemProvider(environment, configuration).get

  lazy val classicActorSystemProvider: ClassicActorSystemProvider = new ClassicActorSystemProviderProvider(
    actorSystem
  ).get

  @nowarn
  lazy val coordinatedShutdown: CoordinatedShutdown =
    new CoordinatedShutdownProvider(actorSystem, applicationLifecycle).get

  implicit lazy val materializer: Materializer = Materializer.matFromSystem(using actorSystem)

  implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher

/**
 * Akka Typed components.
 */
trait AkkaTypedComponents:
  def actorSystem: ActorSystem
  implicit lazy val scheduler: Scheduler = new AkkaSchedulerProvider(actorSystem).get

/**
 * Provider for the actor system
 */
@Singleton
class ActorSystemProvider @Inject() (environment: Environment, configuration: Configuration)
    extends Provider[ActorSystem]:
  lazy val get: ActorSystem = ActorSystemProvider.start(environment.classLoader, configuration, Nil*)

/**
 * Provider for a classic actor system provide
 */
@Singleton
class ClassicActorSystemProviderProvider @Inject() (actorSystem: ActorSystem)
    extends Provider[ClassicActorSystemProvider]:
  lazy val get: ClassicActorSystemProvider = actorSystem

/**
 * Provider for an [[akka.actor.typed.Scheduler Akka Typed Scheduler]].
 */
@Singleton
class AkkaSchedulerProvider @Inject() (actorSystem: ActorSystem) extends Provider[Scheduler]:
  import akka.actor.typed.scaladsl.adapter.*
  override lazy val get: Scheduler = actorSystem.scheduler.toTyped

object ActorSystemProvider:
  type StopHook = () => Future[?]

  private val logger = LoggerFactory.getLogger(classOf[ActorSystemProvider])

  case object ApplicationShutdownReason extends CoordinatedShutdown.Reason

  /**
   * Start an ActorSystem, using the given configuration and ClassLoader.
   *
   * @return
   *   The ActorSystem and a function that can be used to stop it.
   */
  @deprecated("Use start(ClassLoader, Configuration, Setup*) instead", "2.8.0")
  protected[ActorSystemProvider] def start(classLoader: ClassLoader, config: Configuration): ActorSystem =
    start(classLoader, config, Nil*)

  /**
   * Start an ActorSystem, using the given configuration, ClassLoader, and additional ActorSystem Setup.
   *
   * @return
   *   The ActorSystem and a function that can be used to stop it.
   */
  @deprecated("Use start(ClassLoader, Configuration, Setup*) instead", "2.8.0")
  protected[ActorSystemProvider] def start(
      classLoader: ClassLoader,
      config: Configuration,
      additionalSetup: Setup
  ): ActorSystem =
    start(classLoader, config, Seq(additionalSetup)*)

  /**
   * Start an ActorSystem, using the given configuration, ClassLoader, and optional additional ActorSystem
   * Setups.
   *
   * @return
   *   The ActorSystem and a function that can be used to stop it.
   */
  def start(classLoader: ClassLoader, config: Configuration, additionalSetups: Setup*): ActorSystem =
    val exitJvmPath = "akka.coordinated-shutdown.exit-jvm"
    if config.get[Boolean](exitJvmPath) then
      // When this setting is enabled, there'll be a deadlock at shutdown. Therefore, we
      // prevent the creation of the Actor System.
      val errorMessage =
        s"""Can't start Play: detected "$exitJvmPath = on". """ +
          s"""Using "$exitJvmPath = on" in Play may cause a deadlock when shutting down. """ +
          s"""Please set "$exitJvmPath = off""""
      logger.error(errorMessage)
      throw config.reportError(exitJvmPath, errorMessage)

    val akkaConfig: Config =
      // normalize timeout values for Akka's use
      // TODO: deprecate this setting (see https://github.com/playframework/playframework/issues/8442)
      val playTimeoutKey = "play.akka.shutdown-timeout"
      val playTimeoutDuration = Try(config.get[Duration](playTimeoutKey)).getOrElse(Duration.Inf)

      // Typesafe config used internally by Akka doesn't support "infinite".
      // Also, the value expected is an integer so can't use Long.MaxValue.
      // Finally, Akka requires the delay to be less than a certain threshold.
      val akkaMaxDelay = Int.MaxValue / 1000
      val akkaMaxDuration = Duration(akkaMaxDelay, "seconds")
      val normalisedDuration = playTimeoutDuration.min(akkaMaxDuration)
      val akkaTimeoutDuration = java.time.Duration.ofMillis(normalisedDuration.toMillis)

      val akkaTimeoutKey = "akka.coordinated-shutdown.phases.actor-system-terminate.timeout"
      // Need to manually merge and override akkaTimeoutKey because `null` is meaningful in playTimeoutKey
      config.underlying.withValue(akkaTimeoutKey, ConfigValueFactory.fromAnyRef(akkaTimeoutDuration))

    val name = config.get[String]("play.akka.actor-system")

    val bootstrapSetup = BootstrapSetup(Some(classLoader), Some(akkaConfig), None)
    val actorSystemSetup = ActorSystemSetup(bootstrapSetup +: additionalSetups*)

    logger.debug(s"Starting application default Akka system: $name")
    ActorSystem(name, actorSystemSetup)

private object CoordinatedShutdownProvider:
  private val logger = LoggerFactory.getLogger(classOf[CoordinatedShutdownProvider])

/**
 * Provider for the coordinated shutdown
 */
@Singleton
class CoordinatedShutdownProvider @Inject() (
    actorSystem: ActorSystem,
    applicationLifecycle: ApplicationLifecycle
) extends Provider[CoordinatedShutdown]:
  import CoordinatedShutdownProvider.logger

  @nowarn // for applicationLifecycle.stop()
  lazy val get: CoordinatedShutdown =
    logWarningWhenRunPhaseConfigIsPresent()

    implicit val ec = actorSystem.dispatcher

    val cs = CoordinatedShutdown(actorSystem)
    // Once the ActorSystem is built we can register the ApplicationLifecycle stopHooks as a CoordinatedShutdown phase.
    //
    CoordinatedShutdown(actorSystem)
      .addTask(CoordinatedShutdown.PhaseServiceStop, "application-lifecycle-stophook")(() =>
        applicationLifecycle.stop().map(_ => Done)
      )

    cs

  private def logWarningWhenRunPhaseConfigIsPresent(): Unit =
    val config = actorSystem.settings.config
    if config.hasPath("play.akka.run-cs-from-phase") then
      logger.warn(
        "Configuration 'play.akka.run-cs-from-phase' was deprecated and has no effect. Play now runs all the CoordinatedShutdown phases."
      )
