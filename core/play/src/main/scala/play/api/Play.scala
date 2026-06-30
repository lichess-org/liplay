/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.stream.Materializer
import play.utils.Threads

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import javax.xml.parsers.SAXParserFactory
import play.libs.XML.Constants
import javax.xml.XMLConstants

import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * High-level API to access Play global features.
 */
private[play] object PlayXML:

  private lazy val xercesSaxParserFactory =
    val saxParserFactory = SAXParserFactory.newInstance()
    saxParserFactory.setFeature(
      Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE,
      false
    )
    saxParserFactory.setFeature(
      Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE,
      false
    )
    saxParserFactory.setFeature(
      Constants.XERCES_FEATURE_PREFIX + Constants.DISALLOW_DOCTYPE_DECL_FEATURE,
      true
    )
    saxParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    saxParserFactory

  /*
   * A parser to be used that is configured to ensure that no schemas are loaded.
   */
  lazy val loader = scala.xml.XML.withSAXParser(xercesSaxParserFactory.newSAXParser())
