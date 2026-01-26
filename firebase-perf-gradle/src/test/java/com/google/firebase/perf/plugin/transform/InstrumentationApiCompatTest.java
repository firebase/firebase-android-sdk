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

package com.google.firebase.perf.plugin.transform;

import com.google.firebase.perf.plugin.GradleBuildProject;
import com.google.firebase.perf.plugin.GradleBuildResult;
import com.google.firebase.perf.plugin.GradleBuildVariant;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Compatibility tests for the Firebase Performance Monitoring Gradle plugin.
 *
 * <p>This class verifies that bytecode instrumentation functions correctly on supported
 * versions of the Android Gradle Plugin.
 *
 * @see <a href="https://developer.android.com/build/releases/gradle-plugin">AGP Versions</a>
 * @see <a href="https://gradle.org/releases/">Gradle releases</a>
 */
public class InstrumentationApiCompatTest {
  private static final Logger LOGGER =
      Logger.getLogger(InstrumentationApiCompatTest.class.getName());
  private static final String AGP_METADATA_URL =
      "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml";
  private static final String GRADLE_RELEASES_URL =
      "https://api.github.com/repos/gradle/gradle/releases/latest";
  // Latest verified versions. Update these as new versions are released and verified.
  private static final String VERIFIED_AGP_VERSION = "9.1.0-alpha03";
  private static final String VERIFIED_GRADLE_VERSION = "9.2.1";
  private final OkHttpClient client = new OkHttpClient();

  @RegisterExtension public GradleBuildProject gradleBuildProject = new GradleBuildProject();

  @ParameterizedTest
  // TODO: Explore using a general list of supported/verified Gradle, AGP versions.
  @CsvSource({
    "7.3.3, 7.0.0", // Earliest supported version.
    "7.6.6, 7.4.1", // Latest 7.x
    "8.14.3, 8.13.0", // Latest 8.x
    "9.2.0, 8.13.0", // Latest general
    VERIFIED_GRADLE_VERSION + ", " + VERIFIED_AGP_VERSION, // Hard coded latest version
  })
  public void gradleBuildRunsInstrumentationForAllVariants(String gradleVersion, String agpVersion)
      throws Exception {
    GradleBuildResult result =
        gradleBuildProject
            .getJavaRunnerBuilder()
            .withGradleVersion(gradleVersion)
            .withAndroidGradlePluginVersion(agpVersion)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void gradleBuildRunsInstrumentationForAllVariants_latestGradleAndAgp() throws Exception {
    String latestGradleVersion = latestGradleVersion();
    String latestAgpVersion = latestAgpVersion();

    LOGGER.log(Level.INFO, "Latest Gradle Version: {0}", latestGradleVersion);
    LOGGER.log(Level.INFO, "Latest AGP Version: {0}", latestAgpVersion);
    LOGGER.log(Level.WARNING, "If this test fails, please file an issue or a bug");

    if (latestAgpVersion.equals(VERIFIED_AGP_VERSION)
        && latestGradleVersion.equals(VERIFIED_GRADLE_VERSION)) {
      LOGGER.log(
          Level.INFO,
          "Latest gradle and AGP versions are identical to verified versions. Skipping test.");
      return;
    }

    GradleBuildResult result =
        gradleBuildProject
            .getJavaRunnerBuilder()
            .withGradleVersion(latestGradleVersion)
            .withAndroidGradlePluginVersion(latestAgpVersion)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedFor(GradleBuildVariant.RELEASE);
  }

  /**
   * Fetches the latest Gradle version using OkHttp.
   * @return The latest Gradle version as a String.
   * @throws RuntimeException if the fetching or parsing fails.
   */
  private String latestGradleVersion() {
    Request request =
        new Request.Builder()
            .url(GRADLE_RELEASES_URL)
            .header("User-Agent", "PerfPlugin-Latest-Fetcher")
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (response.body() == null) {
        throw new IOException("Response body is null");
      }
      JsonObject releaseObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
      return releaseObject.get("name").getAsString();
    } catch (Exception e) {
      // Catch all exceptions (IO, XML parsing, etc.) and wrap them
      throw new RuntimeException(
          "Failed to fetch or parse latest Gradle version: " + e.getMessage(), e);
    }
  }

  /**
   * Fetches the latest AGP version using OkHttp.
   * @return The latest AGP version as a String.
   * @throws RuntimeException if the fetching or parsing fails.
   */
  private String latestAgpVersion() {
    Request request = new Request.Builder().url(AGP_METADATA_URL).build();

    try (Response response = client.newCall(request).execute()) {
      if (response.body() == null) {
        throw new IOException("Response body is null");
      }

      String xmlContent = response.body().string();
      Matcher matcher = Pattern.compile("<latest>(.*?)</latest>").matcher(xmlContent);
      if (matcher.find()) {
        return matcher.group(1);
      } else {
        throw new RuntimeException("Unable to find latest AGP version using regex");
      }
    } catch (Exception e) {
      // Catch all exceptions (IO, XML parsing, etc.) and wrap them
      throw new RuntimeException(
          "Failed to fetch or parse latest AGP version: " + e.getMessage(), e);
    }
  }
}
