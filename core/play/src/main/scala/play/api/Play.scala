/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api

import javax.xml.parsers.SAXParserFactory
import play.libs.XML.Constants
import javax.xml.XMLConstants

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
