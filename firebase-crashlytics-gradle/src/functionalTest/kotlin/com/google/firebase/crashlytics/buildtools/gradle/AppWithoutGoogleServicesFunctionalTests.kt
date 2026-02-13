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
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPluginTest.Companion.mavenLocal
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPluginTest.Companion.pluginVersion
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional test cases for the Crashlytics Gradle plugin related to the Google-Services plugin and
 * manually configured gmp app ids.
 */
class AppWithoutGoogleServicesFunctionalTests {
  @TempDir private lateinit var projectDir: File
  private lateinit var buildFile: File

  @Test
  fun `plugin requires Google Services plugin`() {
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "8.1.4"
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

    val thrown =
      assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("8.1")
          .withProjectDir(projectDir)
          .withArguments(":uploadCrashlyticsMappingFileRelease", "--configuration-cache")
          .build()
      }

    assertThat(thrown).hasMessageThat().contains("Google-Services plugin not found")
  }

  @Test
  fun `plugin requires Google Services 4-4-1 and above`() {
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "8.1.4"
          id("com.google.gms.google-services") version "4.3.15"
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

    val thrown =
      assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("8.1")
          .withProjectDir(projectDir)
          .withArguments(":uploadCrashlyticsMappingFileRelease", "--configuration-cache")
          .build()
      }

    assertThat(thrown)
      .hasMessageThat()
      .contains("The Crashlytics Gradle plugin 3 requires Google-Services 4.4.1 and above")
  }

  @Test
  fun `plugin requires Google Services to be enabled`() {
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "8.1.4"
          id("com.google.gms.google-services") version "4.4.1" apply false
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

    val thrown =
      assertThrows(UnexpectedBuildFailure::class.java) {
        GradleRunner.create()
          .withGradleVersion("8.1")
          .withProjectDir(projectDir)
          .withArguments(":uploadCrashlyticsMappingFileRelease", "--configuration-cache")
          .build()
      }

    assertThat(thrown).hasMessageThat().contains("Google-Services plugin not configured properly")
  }

  @BeforeEach
  fun setup() {
    buildFile = File(projectDir, "build.gradle.kts")

    val settingsFile = File(projectDir, "settings.gradle.kts")
    settingsFile.writeText(
      """
        pluginManagement {
          repositories {
            $mavenLocal
            google()
            mavenCentral()
          }
        }

        dependencyResolutionManagement {
          repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
          repositories {
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
}
