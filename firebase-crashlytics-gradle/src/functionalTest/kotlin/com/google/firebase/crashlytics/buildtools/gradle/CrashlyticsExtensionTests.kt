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
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPluginTest.Companion.pluginVersion
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Functional tests related to the Crashlytics extension. */
class CrashlyticsExtensionTests {
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
            ${CrashlyticsPluginTest.mavenLocal}
            google()
            mavenCentral()
          }
        }

        rootProject.name = "crashlytics-gradle-plugin-test"
      """
    )

    val propertiesFile = File(projectDir, "gradle.properties")
    propertiesFile.writeText(
      """
        com.google.firebase.crashlytics.buildtools = pretend
      """
    )
  }

  @Test
  fun `set unstrippedNativeLibsDir to single path`() {
    buildFile.writeText(
      """
        import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

        plugins {
          id("com.android.application") version "8.1.4"
          id("com.google.gms.google-services") version "4.4.1"
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }

        android {
          compileSdk = 33
          namespace = "com.google.firebase.testing.crashlytics"

          buildTypes {
            debug {
              configure<CrashlyticsExtension> {
                unstrippedNativeLibsDir = "/some/absolute/string/path"
              }
            }
          }
        }
      """
    )

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments("-d", ":tasks", "--configuration-cache")
        .build()

    assertThat(result.output).contains("/some/absolute/string/path")
  }

  @Test
  fun `set unstrippedNativeLibsDir to array of multiple path types`() {
    buildFile.writeText(
      """
        import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

        plugins {
          id("com.android.application") version "8.1.4"
          id("com.google.gms.google-services") version "4.4.1"
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }

        android {
          compileSdk = 33
          namespace = "com.google.firebase.testing.crashlytics"

          buildTypes {
            debug {
              configure<CrashlyticsExtension> {
                unstrippedNativeLibsDir = arrayOf(
                  "/some/absolute/string/path",
                  "/another/absolute/string/path",
                  File("/a/file/object/path"),
                  project.files("/absolute/project/file/path"),
                  "relative/path",
                  project.files("relative/project/file/path"),
                )
              }
            }
          }
        }
      """
    )

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments("-d", ":tasks", "--configuration-cache")
        .build()

    assertThat(result.output).contains("/some/absolute/string/path")
    assertThat(result.output).contains("/another/absolute/string/path")
    assertThat(result.output).contains("/a/file/object/path")
    assertThat(result.output).contains("/absolute/project/file/path")

    // Verify the relative paths were made absolute by checking the prepended slash
    assertThat(result.output).contains("/relative/path")
    assertThat(result.output).contains("/relative/project/file/path")
  }

  @Test
  fun `set unstrippedNativeLibsDir to invalid type throws`() {
    buildFile.writeText(
      """
        import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

        plugins {
          id("com.android.application") version "8.1.4"
          id("com.google.gms.google-services") version "4.4.1"
          id("com.google.firebase.crashlytics") version "$pluginVersion"
        }

        android {
          compileSdk = 33
          namespace = "com.google.firebase.testing.crashlytics"

          buildTypes {
            debug {
              configure<CrashlyticsExtension> {
                unstrippedNativeLibsDir = 42
              }
            }
          }
        }
      """
    )

    val thrown =
      Assertions.assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("8.1")
          .withProjectDir(projectDir)
          .withArguments(":tasks", "--configuration-cache")
          .build()
      }

    assertThat(thrown)
      .hasMessageThat()
      .contains("Cannot convert the provided notation to a File or URI: 42")
  }
}
