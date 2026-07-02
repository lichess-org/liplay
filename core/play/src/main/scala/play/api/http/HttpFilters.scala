/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.http

import javax.inject.Inject
import javax.inject.Singleton

import com.typesafe.config.ConfigException
import play.api.Configuration
import play.api.Environment
import play.api.Logger
import play.api.mvc.EssentialFilter

/**
 * Provides filters to the [[play.api.http.HttpRequestHandler]].
 */
trait HttpFilters:

  /**
   * Return the filters that should filter every request
   */
  def filters: Seq[EssentialFilter]

/**
 * A filters provider that provides no filters.
 */
class NoHttpFilters extends HttpFilters:
  val filters: Seq[EssentialFilter] = Nil

object NoHttpFilters extends NoHttpFilters
