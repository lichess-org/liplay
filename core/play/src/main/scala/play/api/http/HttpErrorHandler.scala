/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.http

import play.api.*
import play.api.http.Status.*
import play.api.libs.typedmap.TypedKey
import play.api.mvc.Results.*
import play.api.mvc.*
import play.api.routing.Router

import scala.annotation.tailrec
import scala.concurrent.*
import scala.util.control.NonFatal

/**
 * Component for handling HTTP errors in Play.
 *
 * @since 2.4.0
 */
trait HttpErrorHandler:

  /**
   * Invoked when a client error occurs, that is, an error in the 4xx series.
   *
   * @param request
   *   The request that caused the client error.
   * @param statusCode
   *   The error status code. Must be greater or equal to 400, and less than 500.
   * @param message
   *   The error message.
   */
  def onClientError(request: RequestHeader, statusCode: Int, message: String = ""): Future[Result]

  /**
   * Invoked when a server error occurs.
   *
   * @param request
   *   The request that triggered the server error.
   * @param exception
   *   The server error.
   */
  def onServerError(request: RequestHeader, exception: Throwable): Future[Result]

object HttpErrorHandler:

  /**
   * Request attributes used by the error handler.
   */
  object Attrs:
    val HttpErrorInfo: TypedKey[HttpErrorInfo] = TypedKey("HttpErrorInfo")

case class HttpErrorConfig(showDevErrors: Boolean = false, playEditor: Option[String] = None)

/**
 * The default HTTP error handler.
 *
 * This class is intended to be extended, allowing users to reuse some of the functionality provided here.
 *
 * @param router
 *   An optional router. If provided, in dev mode, will be used to display more debug information when a
 *   handler can't be found. This is a lazy parameter, to avoid circular dependency issues, since the router
 *   may well depend on this.
 */
class DefaultHttpErrorHandler(
    config: HttpErrorConfig = HttpErrorConfig(),
    router: => Option[Router] = None
) extends HttpErrorHandler:
  private val logger = Logger(getClass)

  /**
   * @param environment
   *   The environment
   * @param router
   *   An optional router. If provided, in dev mode, will be used to display more debug information when a
   *   handler can't be found. This is a lazy parameter, to avoid circular dependency issues, since the router
   *   may well depend on this.
   */
  def this(
      environment: Environment,
      configuration: Configuration,
      router: => Option[Router]
  ) =
    this(
      HttpErrorConfig(environment.mode != Mode.Prod, configuration.getOptional[String]("play.editor")),
      router
    )

  /**
   * Invoked when a client error occurs, that is, an error in the 4xx series.
   *
   * @param request
   *   The request that caused the client error.
   * @param statusCode
   *   The error status code. Must be greater or equal to 400, and less than 500.
   * @param message
   *   The error message.
   */
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    statusCode match
      case BAD_REQUEST => onBadRequest(request, message)
      case FORBIDDEN => onForbidden(request, message)
      case NOT_FOUND => onNotFound(request, message)
      case clientError if statusCode >= 400 && statusCode < 500 =>
        onOtherClientError(request, statusCode, message)
      case nonClientError =>
        throw new IllegalArgumentException(
          s"onClientError invoked with non client error status code $statusCode: $message"
        )

  /**
   * Invoked when a client makes a bad request.
   *
   * @param request
   *   The request that was bad.
   * @param message
   *   The error message.
   */
  protected def onBadRequest(request: RequestHeader, message: String): Future[Result] =
    Future.successful(BadRequest(s"bad request: $message"))

  /**
   * Invoked when a client makes a request that was forbidden.
   *
   * @param request
   *   The forbidden request.
   * @param message
   *   The error message.
   */
  protected def onForbidden(request: RequestHeader, message: String): Future[Result] =
    Future.successful(Forbidden(s"forbidden: $message"))

  /**
   * Invoked when a handler or resource is not found.
   *
   * @param request
   *   The request that no handler was found to handle.
   * @param message
   *   A message.
   */
  protected def onNotFound(request: RequestHeader, message: String): Future[Result] =
    Future.successful {
      NotFound(s"not found: $message")
    }

  /**
   * Invoked when a client error occurs, that is, an error in the 4xx series, which is not handled by any of
   * the other methods in this class already.
   *
   * @param request
   *   The request that caused the client error.
   * @param statusCode
   *   The error status code. Must be greater or equal to 400, and less than 500.
   * @param message
   *   The error message.
   */
  protected def onOtherClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    Future.successful {
      Results.Status(statusCode)(s"client error: $message")
    }

  /**
   * Invoked when a server error occurs.
   *
   * By default, the implementation of this method delegates to [[onProdServerError]] when in prod mode, and
   * [[onDevServerError]] in dev mode. It is recommended, if you want Play's debug info on the error page in
   * dev mode, that you override [[onProdServerError]] instead of this method.
   *
   * @param request
   *   The request that triggered the server error.
   * @param exception
   *   The server error.
   */
  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    try
      val usefulException =
        HttpErrorHandlerExceptions.throwableToUsefulException(!config.showDevErrors, exception)

      logServerError(request, usefulException)

      onProdServerError(request, usefulException)
    catch
      case NonFatal(e) =>
        logger.error("Error while handling error", e)
        Future.successful(InternalServerError(fatalErrorMessage(request, e)))

  /**
   * Invoked when handling a server error with this error handler failed.
   *
   * <p>As a last resort this method allows you to return a (simple) error message that will be send along
   * with a "500 Internal Server Error" response. It's highly recommended to just return a simple string,
   * without doing any fancy processing inside the method (like accessing files,...) that could throw
   * exceptions. This is your last chance to send a meaningful error message when everything else failed.
   *
   * @param request
   *   The request that triggered the server error.
   * @param exception
   *   The server error.
   * @return
   *   An error message which will be send as last resort in case handling a server error with this error
   *   handler failed.
   */
  protected def fatalErrorMessage(request: RequestHeader, exception: Throwable): String = ""

  /**
   * Responsible for logging server errors.
   *
   * This can be overridden to add additional logging information, eg. the id of the authenticated user.
   *
   * @param request
   *   The request that triggered the server error.
   * @param usefulException
   *   The server error.
   */
  protected def logServerError(request: RequestHeader, usefulException: UsefulException): Unit =
    logger.error(
      """
        |
        |! @%s - Internal server error, for (%s) [%s] ->
        | """.stripMargin.format(usefulException.id, request.method, request.uri),
      usefulException
    )

  /**
   * Invoked in prod mode when a server error occurs.
   *
   * Override this rather than [[onServerError]] if you don't want to change Play's debug output when logging
   * errors in dev mode.
   *
   * @param request
   *   The request that triggered the error.
   * @param exception
   *   The exception.
   */
  protected def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] =
    Future.successful {
      InternalServerError("server error")
    }

/**
 * Extracted so the Java default error handler can reuse this functionality
 */
object HttpErrorHandlerExceptions:

  /**
   * Convert the given exception to an exception that Play can report more information about.
   *
   * This will generate an id for the exception, and in dev mode, will load the source code for the code that
   * threw the exception, making it possible to report on the location that the exception was thrown from.
   */
  @tailrec def throwableToUsefulException(
      isProd: Boolean,
      throwable: Throwable
  ): UsefulException = throwable match
    case useful: UsefulException => useful
    case e: ExecutionException => throwableToUsefulException(isProd, e.getCause)
    case prodException if isProd => UnexpectedException(unexpected = Some(prodException))
    case other =>
      val desc = s"[${other.getClass.getSimpleName}: ${other.getMessage}]"
      new PlayException.ExceptionSource("Execution exception", desc, other):
        def line = null
        def position = null
        def input = null
        def sourceName = null

/**
 * A default HTTP error handler that can be used when there's no application available.
 *
 * Note: this HttpErrorHandler should ONLY be used in DEV or TEST. The way this displays errors to the user is
 * generally not suitable for a production environment.
 */
object DefaultHttpErrorHandler
    extends DefaultHttpErrorHandler(HttpErrorConfig(showDevErrors = true, playEditor = None), None)
