/*
 * Copyright (c) 2012-2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich.common
package loaders

// Java
import java.net.URI
import java.net.URLDecoder

// Scala
import scala.collection.JavaConversions._
// import scala.language.existentials

// Scalaz
import scalaz._
import Scalaz._

// Apache URLEncodedUtils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

// Joda-Time
import org.joda.time.DateTime

object CollectorPayload {

  /**
   * A constructor version to use. Supports legacy
   * tp1 (where no API vendor or version provided
   * as well as Snowplow).
   */
  def apply(
    querystring: List[NameValuePair],
    sourceName: String,
    sourceEncoding: String,
    sourceHostname: Option[String],
    contextTimestamp: DateTime,
    contextIpAddress: Option[String],
    contextUseragent: Option[String],
    contextRefererUri: Option[String],
    contextHeaders: List[String],
    contextUserId: Option[String],
    api: CollectorApi,
    contentType: Option[String],
    body: Option[String]): CollectorPayload = {

    val source  = CollectorSource(sourceName, sourceEncoding, sourceHostname)
    val context = CollectorContext(contextTimestamp, contextIpAddress, contextUseragent, contextRefererUri, contextHeaders, contextUserId)
    
    CollectorPayload(api, querystring, contentType, body, source, context)
  }
}

object CollectorApi {

  // Defaults for the tracker vendor and version
  // before we implemented this into Snowplow.
  // TODO: make private once the ThriftLoader is updated
  val SnowplowTp1 = CollectorApi("com.snowplowanalytics.snowplow", "tp1")

  // To extract the API vendor and version from the
  // the path to the requested object.
  // TODO: move this to somewhere not specific to
  // this collector
  private val ApiPathRegex = """^[\/]?([^\/]+)\/([^\/]+)[\/]?$""".r

  /**
   * Parses the requested URI path to determine the
   * specific API version this payload follows.
   *
   * @param path The request path
   * @return a Validation boxing either a
   *         CollectorApi or a Failure String.
   */
  def parse(path: String): Validation[String, CollectorApi] = path match {
    case ApiPathRegex(vnd, ver)   => CollectorApi(vnd, ver).success
    case _ if isIceRequest(path)  => SnowplowTp1.success
    case _                        => s"Request path ${path} does not match (/)vendor/version(/) pattern nor is a legacy /i(ce.png) request".fail
  }

  /**
   * Checks whether a request to
   * a collector is a tracker
   * hitting the ice pixel.
   *
   * @param path The request path
   * @return true if this is a request
   *         for the ice pixel
   */
  protected[loaders] def isIceRequest(path: String): Boolean =
    path.startsWith("/ice.png") || // Legacy name for /i
    path.equals("/i") ||           // Legacy name for /com.snowplowanalytics.snowplow/tp1
    path.startsWith("/i?")
}

/**
 * Unambiguously identifies the collector
 * source of this input line.
 */
final case class CollectorSource(
  name:     String,
  encoding: String,
  hostname: Option[String]
  )

/**
 * Context derived by the collector.
 */
final case class CollectorContext(
  timestamp:   DateTime,       // Must have a timestamp
  ipAddress:   Option[String],
  useragent:   Option[String],
  refererUri:  Option[String],
  headers:     List[String],   // Could be empty
  userId:      Option[String]  // User ID generated by collector-set third-party cookie
  )

/**
 * Define the vendor and version
 * of the payload.
 */
final case class CollectorApi(
  vendor:      String,
  version:     String
)

/**
 * The canonical input format for the ETL
 * process: it should be possible to
 * convert any collector input format to
 * this format, ready for the main,
 * collector-agnostic stage of the ETL.
 */
final case class CollectorPayload(
  api:         CollectorApi,
  querystring: List[NameValuePair], // Could be empty in future trackers
  contentType: Option[String],      // Not always set
  body:        Option[String],      // Not set for GETs
  source:      CollectorSource,
  context:     CollectorContext
  )
