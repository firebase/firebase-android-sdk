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
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Functional tests related to a simple native app. */
class NativeAppFunctionalTests {
  @TempDir private lateinit var projectDir: File
  private lateinit var buildFile: File

  @Test
  fun `only variants with nativeSymbolUploadEnabled register symbol tasks`() {
    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(":tasks", "--configuration-cache")
        .build()

    // The release variant has nativeSymbolUploadEnabled set to true.
    assertThat(result.output).contains("uploadCrashlyticsSymbolFileRelease")

    // The debug variant does not set nativeSymbolUploadEnabled.
    assertThat(result.output).doesNotContain("uploadCrashlyticsSymbolFileDebug")
  }

  @Test
  fun `uploadCrashlyticsSymbolFile generates and uploads breakpad symbols`() {
    val generateTask = ":generateCrashlyticsSymbolFileRelease"
    val uploadTask = ":uploadCrashlyticsSymbolFileRelease"

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(uploadTask, "--configuration-cache")
        .build()

    assertThat(result.task(generateTask)?.outcome).isEqualTo(SUCCESS)
    assertThat(result.task(uploadTask)?.outcome).isEqualTo(SUCCESS)

    assertThat(result.output)
      .contains("generateNativeSymbolFiles - out nativeSymbols BreakpadSymbolGenerator")
    assertThat(result.output)
      .contains("uploadNativeSymbolFiles - nativeSymbols 1:1:android:1a BreakpadSymbolFileService")
  }

  @Test
  fun `uploadCrashlyticsSymbolFile with breakpad binary override in extension`() {
    // Create the file for the breakpad override, otherwise task fails with missing input file
    File(projectDir, "dump_syms.o").createNewFile()

    // Write a new build file with the breakpad binary overridden
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
            release {
              isMinifyEnabled = true
              configure<CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
                symbolGeneratorType = "breakpad"
                breakpadBinary = file("dump_syms.o")
              }
            }
          }
        }
      """
    )

    val generateTask = ":generateCrashlyticsSymbolFileRelease"
    val uploadTask = ":uploadCrashlyticsSymbolFileRelease"

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(uploadTask, "--configuration-cache")
        .build()

    assertThat(result.task(generateTask)?.outcome).isEqualTo(SUCCESS)
    assertThat(result.task(uploadTask)?.outcome).isEqualTo(SUCCESS)

    assertThat(result.output)
      .contains("generateNativeSymbolFiles - out nativeSymbols BreakpadSymbolGenerator")
    assertThat(result.output)
      .contains("uploadNativeSymbolFiles - nativeSymbols 1:1:android:1a BreakpadSymbolFileService")
  }

  @Test
  fun `uploadCrashlyticsSymbolFile with breakpad binary override in properties`() {
    // Create the file for the breakpad override, otherwise task fails with missing input file
    File(projectDir, "dump_syms.o").createNewFile()

    val generateTask = ":generateCrashlyticsSymbolFileRelease"
    val uploadTask = ":uploadCrashlyticsSymbolFileRelease"

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(
          uploadTask,
          "-Pcom.google.firebase.crashlytics.breakpadBinary=dump_syms.o",
          "--configuration-cache"
        )
        .build()

    assertThat(result.task(generateTask)?.outcome).isEqualTo(SUCCESS)
    assertThat(result.task(uploadTask)?.outcome).isEqualTo(SUCCESS)

    assertThat(result.output)
      .contains("generateNativeSymbolFiles - out nativeSymbols BreakpadSymbolGenerator")
    assertThat(result.output)
      .contains("uploadNativeSymbolFiles - nativeSymbols 1:1:android:1a BreakpadSymbolFileService")
  }

  @Test
  fun `uploadCrashlyticsSymbolFile with csym override generates and uploads csym symbols`() {
    // Write a new build file with symbol generator type set to csym
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
            release {
              isMinifyEnabled = true
              configure<CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
                symbolGeneratorType = "csym"
              }
            }
          }
        }
      """
    )

    val generateTask = ":generateCrashlyticsSymbolFileRelease"
    val uploadTask = ":uploadCrashlyticsSymbolFileRelease"

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(uploadTask, "--configuration-cache")
        .build()

    assertThat(result.task(generateTask)?.outcome).isEqualTo(SUCCESS)
    assertThat(result.task(uploadTask)?.outcome).isEqualTo(SUCCESS)

    assertThat(result.output)
      .contains("generateNativeSymbolFiles - out nativeSymbols NdkCSymGenerator")
    assertThat(result.output)
      .contains("uploadNativeSymbolFiles - nativeSymbols 1:1:android:1a CsymSymbolFileService")
  }

  @Test
  fun `uploadCrashlyticsSymbolFile with breakpad in extension but csym property uploads csym `() {
    // Write a new build file with symbol generator type set to breakpad
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
            release {
              isMinifyEnabled = true
              configure<CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
                symbolGeneratorType = "breakpad"
              }
            }
          }
        }
      """
    )

    val generateTask = ":generateCrashlyticsSymbolFileRelease"
    val uploadTask = ":uploadCrashlyticsSymbolFileRelease"

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        // Set csym by property, which overrides the extension.
        .withArguments(
          uploadTask,
          "-Pcom.google.firebase.crashlytics.symbolGenerator=csym",
          "--configuration-cache"
        )
        .build()

    assertThat(result.task(generateTask)?.outcome).isEqualTo(SUCCESS)
    assertThat(result.task(uploadTask)?.outcome).isEqualTo(SUCCESS)

    assertThat(result.output)
      .contains("generateNativeSymbolFiles - out nativeSymbols NdkCSymGenerator")
    assertThat(result.output)
      .contains("uploadNativeSymbolFiles - nativeSymbols 1:1:android:1a CsymSymbolFileService")
  }

  @Test
  fun `uploadCrashlyticsSymbolFile with configuration cache caches`() {
    val generateTask = ":generateCrashlyticsSymbolFileRelease"
    val uploadTask = ":uploadCrashlyticsSymbolFileRelease"

    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(uploadTask, "--configuration-cache")
        .build()

    assertThat(result.task(generateTask)?.outcome).isEqualTo(SUCCESS)
    assertThat(result.task(uploadTask)?.outcome).isEqualTo(SUCCESS)

    assertThat(result.output)
      .contains("generateNativeSymbolFiles - out nativeSymbols BreakpadSymbolGenerator")
    assertThat(result.output)
      .contains("uploadNativeSymbolFiles - nativeSymbols 1:1:android:1a BreakpadSymbolFileService")
  }

  @Test
  fun `building a native app generates build ids`() {
    val result =
      GradleRunner.create()
        .withGradleVersion("8.1")
        .withProjectDir(projectDir)
        .withArguments(":assembleRelease", "--configuration-cache")
        .build()

    assertThat(result.task(":injectCrashlyticsBuildIdsRelease")?.outcome).isEqualTo(SUCCESS)
  }

  @BeforeEach
  fun setup() {
    val jniLibs = File(projectDir, "src/main/jniLibs")
    javaClass.getResource("/nativeAppProject/jniLibs")?.file?.let { jniLibsResource ->
      File(jniLibsResource).copyRecursively(jniLibs)
    }

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
          ]
        }
      """
    )

    buildFile = File(projectDir, "build.gradle.kts")
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
            release {
              isMinifyEnabled = true
              configure<CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
              }
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
        com.google.firebase.crashlytics.fakeOutput = generate
      """
    )
  }
}
