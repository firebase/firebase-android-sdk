/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.appdistribution.gradle

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

object VersionUtils {
  private val LOGGER = Logger.getLogger(VersionUtils::class.java.name)
  private const val AGP_METADATA_URL =
    "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
  private const val GRADLE_RELEASES_URL =
    "https://api.github.com/repos/gradle/gradle/releases/latest"
  private const val GOOGLE_SERVICES_METADATA_URL =
    "https://dl.google.com/dl/android/maven2/com/google/gms/google-services/maven-metadata.xml"

  // Latest verified versions. Update these as new versions are released and verified.
  // These serve as fallbacks if fetching fails.
  const val VERIFIED_AGP_VERSION = "9.1.0-alpha07"
  const val VERIFIED_GRADLE_VERSION = "9.3.1"
  const val VERIFIED_GOOGLE_SERVICES_VERSION = "4.4.4"

  fun fetchLatestAgpVersion(): String {
    try {
      val url = URL(AGP_METADATA_URL)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connect()

      if (connection.responseCode == 200) {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc: Document = dBuilder.parse(connection.inputStream)
        doc.documentElement.normalize()
        val versioning = doc.getElementsByTagName("versioning").item(0)
        val latest = versioning.childNodes
        for (i in 0 until latest.length) {
            if (latest.item(i).nodeName == "latest") {
                return latest.item(i).textContent
            }
        }
      }
    } catch (e: Exception) {
      LOGGER.warning("Failed to fetch latest AGP version: ${e.message}")
    }
    return VERIFIED_AGP_VERSION
  }

  fun fetchLatestGradleVersion(): String {
      try {
          val url = URL(GRADLE_RELEASES_URL)
          val connection = url.openConnection() as HttpURLConnection
          connection.requestMethod = "GET"
          connection.setRequestProperty("User-Agent", "Mozilla/5.0")
          connection.connect()

          if (connection.responseCode == 200) {
              val content = connection.inputStream.bufferedReader().use { it.readText() }
              val jsonObject = JsonParser.parseString(content).asJsonObject
              return jsonObject.get("tag_name").asString.removePrefix("v")
          }
      } catch (e: Exception) {
          LOGGER.warning("Failed to fetch latest Gradle version: ${e.message}")
      }
      return VERIFIED_GRADLE_VERSION
  }

  fun fetchLatestGoogleServicesVersion(): String {
    try {
      val url = URL(GOOGLE_SERVICES_METADATA_URL)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connect()

      if (connection.responseCode == 200) {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc: Document = dBuilder.parse(connection.inputStream)
        doc.documentElement.normalize()
        val versioning = doc.getElementsByTagName("versioning").item(0)
        val latest = versioning.childNodes
        for (i in 0 until latest.length) {
          if (latest.item(i).nodeName == "latest") {
            return latest.item(i).textContent
          }
        }
      }
    } catch (e: Exception) {
      LOGGER.warning("Failed to fetch latest Google Services version: ${e.message}")
    }
    return VERIFIED_GOOGLE_SERVICES_VERSION
  }
}
