/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.crashlytics.buildtools.gradle

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

/** Functional tests for the Crashlytics Gradle plugin. */
class CrashlyticsPluginTest {
  @TempDir private lateinit var projectDir: File
  private lateinit var settingsFile: File
  private lateinit var buildFile: File

  @BeforeEach
  fun setup() {
    settingsFile = File(projectDir, "settings.gradle.kts")
    buildFile = File(projectDir, "build.gradle.kts")

    settingsFile.writeText(
      """
        pluginManagement {
          repositories {
            $mavenLocal
            google()
            mavenCentral()
          }
        }

        rootProject.name = "crashlytics-gradle-plugin-test"
      """
    )

    val manifestFile = File(projectDir, "src/main/AndroidManifest.xml")
    manifestFile.parentFile.mkdirs()
    manifestFile.writeText("""
        <manifest></manifest>
      """)

    val propertiesFile = File(projectDir, "gradle.properties")
    propertiesFile.writeText(
      """
        com.google.firebase.crashlytics.buildtools = pretend
      """
    )
  }

  @ParameterizedTest
  @CsvFileSource(resources = ["/versions.csv"], numLinesToSkip = 1)
  fun `Run tasks on versions`(
    gradleVersion: String,
    agpVersion: String,
    googleServicesVersion: String,
  ) {
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "$agpVersion"
          id("com.google.gms.google-services") version "$googleServicesVersion"
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }

        android {
          compileSdk = 33
          namespace = "com.google.firebase.testing.crashlytics"

          buildTypes {
            release {
              isMinifyEnabled = true
            }
          }
        }
      """
    )

    // Do not set withPluginClasspath() or there will be conflicts with different dep versions.
    // Instead, the test tasks all depend on :crashlytics-gradle:publishToMavenLocal, and the
    // local plugin version is exposed as a system property.
    val result =
      GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(projectDir)
        .withArguments(":injectCrashlyticsMappingFileIdRelease", "--configuration-cache")
        .build()

    assertThat(result.output)
      .contains(
        "injectMappingFileIdIntoResource - com_google_firebase_crashlytics_mappingfileid.xml test1234"
      )
    assertThat(result.task(":injectCrashlyticsMappingFileIdRelease")?.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `Plugin requires Gradle 8 and above`() {
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "7.2.0"
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }
      """
    )

    val thrown =
      assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("7.6")
          .withProjectDir(projectDir)
          .withArguments(":tasks")
          .build()
      }

    assertThat(thrown)
      .hasMessageThat()
      .contains("The Crashlytics Gradle plugin 3 requires Gradle 8.0 or above")
  }

  @Test
  fun `Plugin requires AGP 8-1 and above`() {
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "8.0.0"
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }
      """
    )

    val thrown =
      assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("8.0")
          .withProjectDir(projectDir)
          .withArguments(":tasks", "--configuration-cache")
          .build()
      }

    assertThat(thrown)
      .hasMessageThat()
      .contains(
        "The Crashlytics Gradle plugin 3 requires `com.android.application` version 8.1 or above"
      )
  }

  @Test
  fun `Plugin requires AGP app plugin to be applied`() {
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "8.1.4" apply false
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }
      """
    )

    val thrown =
      assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("8.1")
          .withProjectDir(projectDir)
          .withArguments(":tasks", "--configuration-cache")
          .build()
      }

    assertThat(thrown)
      .hasMessageThat()
      .contains("Crashlytics requires the `com.android.application` plugin to be applied")
  }

  @Test
  fun `Plugin requires AGP app plugin not library`() {
    buildFile.writeText(
      """
        plugins {
          id("com.android.library") version "8.1.4"
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }
      """
    )

    val thrown =
      assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("8.1")
          .withProjectDir(projectDir)
          .withArguments(":tasks", "--configuration-cache")
          .build()
      }

    assertThat(thrown)
      .hasMessageThat()
      .contains("Applying the Firebase Crashlytics plugin to a library project is unsupported")
  }

  internal companion object {
    /** The local version of the Crashlytics gradle plugin. Propagate by Gradle project version. */
    val pluginVersion: String = System.getProperty("crashlytics.gradle.plugin.version") ?: "latest"

    /** The maven local path. Propagate by Gradle property `maven.artifacts.path`. */
    val mavenLocal: String =
      System.getProperty("crashlytics.maven.artifacts.path")?.let { """maven(url = "$it")""" }
        ?: "mavenLocal()"
  }
}
