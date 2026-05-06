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

import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

object VersionUtils {
  private const val AGP_METADATA_URL =
    "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
  private const val GRADLE_RELEASES_URL = "https://github.com/gradle/gradle/releases"
  private const val GOOGLE_SERVICES_METADATA_URL =
    "https://dl.google.com/dl/android/maven2/com/google/gms/google-services/maven-metadata.xml"

  fun fetchLatestAgpVersion(): String {
    return fetchLatestDependencyVersion(AGP_METADATA_URL, "AGP")
  }

  fun fetchLatestGoogleServicesVersion(): String {
    return fetchLatestDependencyVersion(GOOGLE_SERVICES_METADATA_URL, "Google Services")
  }

  private fun fetchLatestDependencyVersion(url: String, name: String): String {
    val doc =
      try {
        fetchUrl(url) { connection ->
          val dbFactory = DocumentBuilderFactory.newInstance()
          val dBuilder = dbFactory.newDocumentBuilder()
          dBuilder.parse(connection.inputStream)
        }
      } catch (e: Exception) {
        throw RuntimeException("Failed to fetch latest $name version", e)
      }

    doc.documentElement.normalize()
    val versionsNodeList = doc.getElementsByTagName("version")
    // Iterate backwards through all versions to find the first stable one
    for (i in versionsNodeList.length - 1 downTo 0) {
      val version = versionsNodeList.item(i).textContent
      if (!isUnstable(version)) {
        return version
      }
    }
    throw RuntimeException("Failed to find any stable version in $name metadata")
  }

  fun fetchLatestGradleVersion(): String {
    val content =
      try {
        fetchUrl(GRADLE_RELEASES_URL) { connection ->
          connection.inputStream.bufferedReader().use { it.readText() }
        }
      } catch (e: Exception) {
        throw RuntimeException("Failed to fetch latest Gradle version", e)
      }

    val matchResults = Regex("/gradle/gradle/releases/tag/v?([^\"/\\s>]+)").findAll(content)

    // Find the first stable version in the releases list
    for (match in matchResults) {
      val version = match.groupValues[1]
      // Convert RC versions like 9.5.0-RC1 to 9.5.0-rc-1 for the isUnstable check
      val normalizedVersion =
        version.replace(Regex("-RC(\\d+)", RegexOption.IGNORE_CASE)) { "-rc-${it.groupValues[1]}" }

      if (!isUnstable(normalizedVersion)) {
        // Return the version in the rc-1 format if it was an RC
        return normalizedVersion
      }
    }
    throw RuntimeException("Failed to find any stable Gradle version in HTML")
  }

  private fun <T> fetchUrl(url: String, parser: (HttpURLConnection) -> T): T {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "GET"
      connection.connect()
      if (connection.responseCode == 200) {
        return parser(connection)
      }
      throw RuntimeException("Received response code ${connection.responseCode} from $url")
    } finally {
      connection.disconnect()
    }
  }

  private fun isUnstable(version: String): Boolean {
    val unstableKeywords = listOf("alpha", "beta", "milestone", "canary", "m", "rc")
    return unstableKeywords.any { version.contains(it, ignoreCase = true) }
  }
}
