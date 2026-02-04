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

import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.google.firebase.appdistribution.gradle.ApiStubs.Companion.WIRE_MOCK_PORT
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.w3c.dom.Document

/**
 * Compatibility tests for the Firebase App Distribution Gradle plugin.
 *
 * This class verifies that the plugin functions correctly on the latest supported versions of the
 * Android Gradle Plugin and Gradle.
 */
class AppDistributionApiCompatTest {
  @get:Rule val wireMockRule = WireMockRule(WIRE_MOCK_PORT)

  @get:Rule val testGradleProject = TestGradleProject()
  private val testGroovyBuild = TestGroovyBuild(testGradleProject)
  private val apiStubs = ApiStubs(testGradleProject)

  @Before
  fun setup() {
    System.setProperty("FIREBASE_APP_DISTRIBUTION_API_URL", "http://localhost:$WIRE_MOCK_PORT")
  }

  @Test
  fun testBuildOnLatestVersions() {
    val latestGradleVersion = fetchLatestGradleVersion()
    val latestAgpVersion = fetchLatestAgpVersion()

    LOGGER.info("Latest Gradle Version: $latestGradleVersion")
    LOGGER.info("Latest AGP Version: $latestAgpVersion")
    LOGGER.warning("If this test fails, please file an issue or a bug")

    if (latestAgpVersion == VERIFIED_AGP_VERSION && latestGradleVersion == VERIFIED_GRADLE_VERSION) {
      LOGGER.info("Latest gradle and AGP versions are identical to verified versions. Skipping test.")
      return
    }

    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = latestAgpVersion,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      // For tests against Gradle 9, we get the error:
      // "In order to compile Java 9+ source, please set compileSdkVersion to 30 or above"
      compileSdkVersion = "30"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(latestGradleVersion)
        .forwardOutput()
        .build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
  }

  private fun fetchLatestAgpVersion(): String {
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

  private fun fetchLatestGradleVersion(): String {
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

  companion object {
    private val LOGGER = Logger.getLogger(AppDistributionApiCompatTest::class.java.name)
    private const val AGP_METADATA_URL =
      "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
    private const val GRADLE_RELEASES_URL =
      "https://api.github.com/repos/gradle/gradle/releases/latest"

    // Latest verified versions. Update these as new versions are released and verified.
    private const val VERIFIED_AGP_VERSION = "9.1.0-alpha06"
    private const val VERIFIED_GRADLE_VERSION = "9.3.1"
    private const val LATEST_GOOGLE_SERVICES_VERSION = "4.4.4"
  }
}
