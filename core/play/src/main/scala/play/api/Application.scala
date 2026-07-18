/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api

import java.io.*

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.stream.Materializer
import play.api.http.*
import play.api.inject.ApplicationLifecycle
import play.api.internal.libs.concurrent.CoordinatedShutdownSupport
import play.api.libs.Files.*
import play.api.libs.concurrent.PekkoComponents
import play.api.libs.concurrent.PekkoTypedComponents
import play.api.libs.concurrent.CoordinatedShutdownProvider
import play.api.libs.crypto.*
import play.api.mvc.*
import play.api.mvc.request.DefaultRequestFactory
import play.api.mvc.request.RequestFactory
import play.api.routing.Router

import scala.annotation.implicitNotFound
import scala.concurrent.Future

/**
 * A Play application.
 *
 * Application creation is handled by the framework engine.
 *
 * If you need to create an ad-hoc application, for example in case of unit testing, you can easily achieve
 * this using:
 * {{{
 * val application = new DefaultApplication(new File("."), this.getClass.getClassloader, None, Play.Mode.Dev)
 * }}}
 *
 * This will create an application using the current classloader.
 */
@implicitNotFound(
  msg =
    "You do not have an implicit Application in scope. If you want to bring the current running Application into context, please use dependency injection."
)
trait Application:

  /**
   * The absolute path hosting this application, mainly used by the `getFile(path)` helper method
   */
  def path: File

  /**
   * The application's classloader
   */
  def classloader: ClassLoader

  /**
   * `Dev`, `Prod` or `Test`
   */
  def mode: Mode = environment.mode

  /**
   * The application's environment
   */
  def environment: Environment

  private[play] def isDev = mode == Mode.Dev
  private[play] def isTest = mode == Mode.Test
  private[play] def isProd = mode == Mode.Prod

  def configuration: Configuration

  private[play] lazy val httpConfiguration =
    HttpConfiguration.fromConfiguration(configuration, environment)

  /**
   * The default ActorSystem used by the application.
   */
  def actorSystem: ActorSystem

  /**
   * The default Materializer used by the application.
   */
  implicit def materializer: Materializer

  /**
   * The default CoordinatedShutdown to stop the Application
   */
  def coordinatedShutdown: CoordinatedShutdown

  /**
   * The factory used to create requests for this application.
   */
  def requestFactory: RequestFactory

  /**
   * The HTTP request handler
   */
  def requestHandler: HttpRequestHandler

  /**
   * The HTTP error handler
   */
  def errorHandler: HttpErrorHandler

  /**
   * Stop the application. The returned future will be redeemed when all stop hooks have been run.
   */
  def stop(): Future[?]

class DefaultApplication(
    override val environment: Environment,
    applicationLifecycle: ApplicationLifecycle,
    override val configuration: Configuration,
    override val requestFactory: RequestFactory,
    override val requestHandler: HttpRequestHandler,
    override val errorHandler: HttpErrorHandler,
    override val actorSystem: ActorSystem,
    override val materializer: Materializer,
    override val coordinatedShutdown: CoordinatedShutdown
) extends Application:
  def this(
      environment: Environment,
      applicationLifecycle: ApplicationLifecycle,
      configuration: Configuration,
      requestFactory: RequestFactory,
      requestHandler: HttpRequestHandler,
      errorHandler: HttpErrorHandler,
      actorSystem: ActorSystem,
      materializer: Materializer
  ) = this(
    environment,
    applicationLifecycle,
    configuration,
    requestFactory,
    requestHandler,
    errorHandler,
    actorSystem,
    materializer,
    CoordinatedShutdownProvider.build(actorSystem, applicationLifecycle)
  )

  override def path: File = environment.rootPath

  override def classloader: ClassLoader = environment.classLoader

  override def stop(): Future[?] =
    CoordinatedShutdownSupport.asyncShutdown(actorSystem, ApplicationStoppedReason)

private[play] case object ApplicationStoppedReason extends CoordinatedShutdown.Reason

/**
 * Helper to provide the Play built in components.
 */
trait BuiltInComponents extends PekkoComponents with PekkoTypedComponents:

  /** The application's environment, e.g. it's [[ClassLoader]] and root path. */
  def environment: Environment

  /** The application's configuration. */
  def configuration: Configuration

  /**
   * A registry to receive application lifecycle events, e.g. to close resources when the application stops.
   */
  def applicationLifecycle: ApplicationLifecycle

  /** The router that's used to pass requests to the correct handler. */
  def router: Router

  lazy val playBodyParsers: PlayBodyParsers =
    PlayBodyParsers(tempFileCreator, httpErrorHandler, httpConfiguration.parser)(using materializer)
  lazy val defaultBodyParser: BodyParser[AnyContent] = playBodyParsers.default
  lazy val defaultActionBuilder: DefaultActionBuilder = DefaultActionBuilder(defaultBodyParser)

  lazy val httpConfiguration: HttpConfiguration =
    HttpConfiguration.fromConfiguration(configuration, environment)
  lazy val requestFactory: RequestFactory = new DefaultRequestFactory(httpConfiguration)
  lazy val httpErrorHandler: HttpErrorHandler =
    new DefaultHttpErrorHandler(environment, configuration, Some(router))

  /**
   * List of filters, typically provided by mixing in play.filters.HttpFiltersComponents or
   * play.api.NoHttpFiltersComponents.
   *
   * In most cases you will want to mixin HttpFiltersComponents and append your own filters:
   *
   * {{{
   * class MyComponents(context: ApplicationLoader.Context)
   *   extends BuiltInComponentsFromContext(context)
   *   with play.filters.HttpFiltersComponents {
   *
   *   lazy val loggingFilter = new LoggingFilter()
   *   override def httpFilters = {
   *     super.httpFilters :+ loggingFilter
   *   }
   * }
   * }}}
   *
   * If you want to filter elements out of the list, you can do the following:
   *
   * {{{
   * class MyComponents(context: ApplicationLoader.Context)
   *   extends BuiltInComponentsFromContext(context)
   *   with play.filters.HttpFiltersComponents {
   *   override def httpFilters = {
   *     super.httpFilters.filterNot(_.getClass == classOf[CSRFFilter])
   *   }
   * }
   * }}}
   */
  def httpFilters: Seq[EssentialFilter]

  lazy val httpRequestHandler: HttpRequestHandler =
    new DefaultHttpRequestHandler(
      () => router,
      httpErrorHandler,
      httpConfiguration,
      httpFilters
    )

  lazy val application: Application = new DefaultApplication(
    environment,
    applicationLifecycle,
    configuration,
    requestFactory,
    httpRequestHandler,
    httpErrorHandler,
    actorSystem,
    materializer,
    coordinatedShutdown
  )

  lazy val cookieSigner: CookieSigner = new DefaultCookieSigner(httpConfiguration.secret)

  lazy val tempFileReaper: TemporaryFileReaper =
    new DefaultTemporaryFileReaper(
      actorSystem,
      TemporaryFileReaperConfiguration.fromConfiguration(configuration)
    )
  lazy val tempFileCreator: TemporaryFileCreator =
    new DefaultTemporaryFileCreator(applicationLifecycle, tempFileReaper, configuration)

  lazy val fileMimeTypes: FileMimeTypes = new DefaultFileMimeTypes(httpConfiguration.fileMimeTypes)

  // NOTE: the following helpers are declared as protected since they are only meant to be used inside BuiltInComponents
  // This also makes them not conflict with other methods of the same type when used with Macwire.

  /**
   * Alias method to [[defaultActionBuilder]]. This just helps to keep the idiom of using `Action` when
   * creating `Router`s using the built in components.
   *
   * @return
   *   the default action builder.
   */
  protected def Action: DefaultActionBuilder = defaultActionBuilder

  /**
   * Alias method to [[playBodyParsers]].
   */
  protected def parse: PlayBodyParsers = playBodyParsers

/**
 * A component to mix in when no default filters should be mixed in to BuiltInComponents.
 *
 * @see
 *   [[BuiltInComponents.httpFilters]]
 */
trait NoHttpFiltersComponents:
  val httpFilters: Seq[EssentialFilter] = Nil
