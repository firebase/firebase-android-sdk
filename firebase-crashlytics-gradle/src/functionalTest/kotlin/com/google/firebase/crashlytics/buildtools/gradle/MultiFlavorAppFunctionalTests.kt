/*
 * Copyright 2024 Google LLC
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Functional tests related to a app with multiple flavors. */
class MultiFlavorAppFunctionalTests {
  @TempDir private lateinit var projectDir: File

  @Test
  fun `override the google-services json file for a specific variant`() {
    val variantAppId = "1:123:android:321abc"

    val variantGoogleServicesFile = File(projectDir, "src/pro/french/release/google-services.json")
    variantGoogleServicesFile.parentFile.mkdirs()
    variantGoogleServicesFile.writeText(
      """
        {
          "project_info": {
            "project_number": "4815162342",
            "project_id": "crashlytics-gradle-plugin-test"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "$variantAppId",
                "android_client_info": {
                  "package_name": "com.google.firebase.testing.crashlytics"
                }
              },
              "api_key": [ { "current_key": "" } ]
            },
          ]
        }
      """
    )

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(":uploadCrashlyticsMappingFileProFrenchRelease", "--configuration-cache")
        .build()

    assertThat(result.output).contains(variantAppId)
  }

  @Test
  fun `override the app for a specific package name`() {
    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(":uploadCrashlyticsMappingFileProFrenchProd", "--configuration-cache")
        .build()

    // This is the app id specific for package name suffix .prod
    assertThat(result.output).contains("1:2:android:2b")
  }

  @BeforeEach
  fun setup() {
    val settingsFile = File(projectDir, "settings.gradle.kts")
    settingsFile.writeText(
      """
        pluginManagement {
          repositories {
            ${CrashlyticsPluginTest.mavenLocal}
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

    val googleServicesFile = File(projectDir, "google-services.json")
    googleServicesFile.writeText(
      """
        {
          "project_info": {
            "project_number": "4815162342",
            "project_id": "crashlytics-gradle-plugin-test"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "1:1:android:1a",
                "android_client_info": {
                  "package_name": "com.google.firebase.testing.crashlytics"
                }
              },
              "api_key": [ { "current_key": "" } ]
            },
            {
              "client_info": {
                "mobilesdk_app_id": "1:2:android:2b",
                "android_client_info": {
                  "package_name": "com.google.firebase.testing.crashlytics.prod"
                }
              },
              "api_key": [ { "current_key": "" } ]
            },
          ]
        }
      """
    )

    val buildFile = File(projectDir, "build.gradle.kts")
    buildFile.writeText(
      """
        plugins {
          id("com.android.application") version "8.1.4"
          id("com.google.gms.google-services") version "4.4.1"
          id("com.google.firebase.crashlytics") version "${CrashlyticsPluginTest.pluginVersion}"
        }

        android {
          compileSdk = 33
          namespace = "com.google.firebase.testing.crashlytics"

          buildTypes {
            release {
              isMinifyEnabled = true
            }
            create("prod") {
              initWith(getByName("release"))
              applicationIdSuffix = ".prod"
            }
          }

          flavorDimensions += listOf("paid", "lang")
          productFlavors {
            create("free") {
              dimension = "paid"
            }
            create("pro") {
              dimension = "paid"
            }
            create("english") {
              dimension = "lang"
            }
            create("french") {
              dimension = "lang"
            }
          }
        }
      """
    )

    val propertiesFile = File(projectDir, "gradle.properties")
    propertiesFile.writeText(
      """
        com.google.firebase.crashlytics.buildtools = pretend
        com.google.firebase.crashlytics.fakeId = test321
      """
    )
  }
}
