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

import com.google.firebase.appdistribution.gradle.VersionUtils.Stability.STABLE
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

object VersionUtils {
  private const val AGP_METADATA_URL =
    "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
  private const val GRADLE_RELEASES_URL = "https://github.com/gradle/gradle/releases"
  private const val GOOGLE_SERVICES_METADATA_URL =
    "https://dl.google.com/dl/android/maven2/com/google/gms/google-services/maven-metadata.xml"

  enum class Stability(val filteredKeywords: Set<String>) {
    STABLE(setOf("alpha", "beta", "milestone", "canary", "m", "rc")),
    RC(setOf("alpha", "milestone", "canary", "m")),
    BLEEDING_EDGE(setOf());

    fun applies(version: String) = !filteredKeywords.any { version.contains(it, ignoreCase = true) }
  }

  fun fetchLatestAgpVersion(stability: Stability = STABLE): String {
    return fetchLatestDependencyVersion(AGP_METADATA_URL, "AGP", stability)
  }

  fun fetchLatestGoogleServicesVersion(stability: Stability = STABLE): String {
    return fetchLatestDependencyVersion(GOOGLE_SERVICES_METADATA_URL, "Google Services", stability)
  }

  private fun fetchLatestDependencyVersion(
    url: String,
    name: String,
    stability: Stability,
  ): String {
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
      if (stability.applies(version)) {
        return version
      }
    }
    throw RuntimeException("Failed to find any $stability version in $name metadata")
  }

  fun fetchLatestGradleVersion(stability: Stability = STABLE): String {
    val content =
      try {
        fetchUrl(GRADLE_RELEASES_URL) { connection ->
          connection.inputStream.bufferedReader().use { it.readText() }
        }
      } catch (e: Exception) {
        throw RuntimeException("Failed to fetch latest Gradle version", e)
      }

    val matchResults = Regex("/gradle/gradle/releases/tag/v?([^\"/\\s>]+)").findAll(content)

    val applicableVersions =
      matchResults
        .map { it.groupValues[1] }
        .map { version ->
          // Convert RC versions like 9.5.0-RC1 to 9.5.0-rc-1 for the isUnstable check
          version.replace(Regex("-RC(\\d+)", RegexOption.IGNORE_CASE)) {
            "-rc-${it.groupValues[1]}"
          }
        }
        .filter { stability.applies(it) }
        .toList()

    if (applicableVersions.isEmpty()) {
      throw RuntimeException("Failed to find any $stability Gradle version in HTML")
    }

    return applicableVersions.maxWithOrNull(VersionComparator) ?: applicableVersions[0]
  }

  private object VersionComparator : Comparator<String> {
    override fun compare(v1: String, v2: String): Int {
      val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
      val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
      val maxLen = maxOf(parts1.size, parts2.size)
      for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) {
          return p1.compareTo(p2)
        }
      }
      return 0
    }
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
}
