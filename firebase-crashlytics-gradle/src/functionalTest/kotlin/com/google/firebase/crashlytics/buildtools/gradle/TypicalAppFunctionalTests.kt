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
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPluginTest.Companion.buildGradleRunner
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPluginTest.Companion.pluginVersion
import java.io.File
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Functional tests related to a typical app with a debug and release variant as recommended in
 * Android Gradle Crashlytics docs.
 */
class TypicalAppFunctionalTests {
  @TempDir private lateinit var projectDir: File
  private lateinit var settingsFile: File
  private lateinit var buildFile: File

  @Test
  fun `inject mapping file id on app build`() {
    val result = buildGradleRunner(projectDir, ":assembleRelease", "--configuration-cache")

    assertThat(result.output)
      .contains(
        "injectMappingFileIdIntoResource - com_google_firebase_crashlytics_mappingfileid.xml test321"
      )
    assertThat(result.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)
  }

  @Test
  fun `inject version control info on release app build`() {
    val result = buildGradleRunner(projectDir, ":assembleRelease")

    assertThat(result.task(":injectCrashlyticsVersionControlInfoRelease")?.outcome)
      .isEqualTo(SUCCESS)
  }

  @Test
  fun `upload mapping file automatically during minified app build`() {
    val result = buildGradleRunner(projectDir, ":assembleRelease", "--configuration-cache")

    assertThat(result.task(":uploadCrashlyticsMappingFileRelease")?.outcome).isEqualTo(SUCCESS)
    assertThat(result.output)
      .contains("uploadMappingFile - mapping.txt test321  1:1:android:1a release PROGUARD 0.0")
  }

  @ParameterizedTest
  @ValueSource(
    strings = [":injectCrashlyticsMappingFileIdDebug", ":injectCrashlyticsMappingFileIdRelease"]
  )
  fun `inject some mapping file id regardless of obfuscation`(taskName: String) {
    val result = buildGradleRunner(projectDir, taskName)

    assertThat(result.output)
      .contains(
        "injectMappingFileIdIntoResource - com_google_firebase_crashlytics_mappingfileid.xml"
      )
    assertThat(result.task(taskName)?.outcome).isEqualTo(SUCCESS)
  }

  @Test
  fun `upload mapping file with obfuscation`() {
    val taskName = ":uploadCrashlyticsMappingFileRelease"

    val result = buildGradleRunner(projectDir, taskName, "--configuration-cache")

    assertThat(result.output)
      .contains(
        "injectMappingFileIdIntoResource - com_google_firebase_crashlytics_mappingfileid.xml test321"
      )
    assertThat(result.output)
      .contains("uploadMappingFile - mapping.txt test321  1:1:android:1a release PROGUARD 0.0")
    assertThat(result.task(taskName)?.outcome).isEqualTo(SUCCESS)
  }

  @Test
  fun `upload mapping file task without obfuscation not registered`() {
    val result = buildGradleRunner(projectDir, ":tasks", "--configuration-cache")

    assertThat(result.output).doesNotContain("uploadCrashlyticsMappingFileDebug")
  }

  @Test
  fun `skip uploading mapping file when mappingFileUploadEnabled is disabled`() {
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
              configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
              }
              isMinifyEnabled = true
            }
          }
        }
      """
    )

    val result = buildGradleRunner(projectDir, ":assembleRelease", "--configuration-cache")

    assertThat(result.task("uploadCrashlyticsMappingFileRelease")).isNull()
  }

  @ParameterizedTest
  @ValueSource(
    strings =
      [
        ":generateCrashlyticsSymbolFileDebug",
        ":injectBuildIdsTaskDebug",
        ":uploadCrashlyticsSymbolFileDebug",
      ]
  )
  fun `does not register ndk tasks on non-native app`(taskName: String) {
    val thrown =
      assertThrows<UnexpectedBuildFailure> {
        buildGradleRunner(projectDir, taskName, "--configuration-cache")
      }

    assertThat(thrown).hasMessageThat().contains("not found")
  }

  @Test
  fun `building typical app without obfuscation injects blank mapping file id`() {
    val result = buildGradleRunner(projectDir, ":assembleDebug", "--configuration-cache")

    // The debug variant does not have minify enabled, so inject blank mapping file id
    assertThat(result.task(":injectCrashlyticsMappingFileIdDebug")?.outcome).isEqualTo(SUCCESS)
    assertThat(result.output)
      .contains(
        "injectMappingFileIdIntoResource - com_google_firebase_crashlytics_mappingfileid.xml 00000000000000000000000000000000"
      )
  }

  @Test
  fun `building typical app with obfuscation injects mapping file id`() {
    val result = buildGradleRunner(projectDir, ":assembleRelease", "--configuration-cache")

    // The release variant does have minify enabled
    assertThat(result.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)
    assertThat(result.output)
      .contains(
        "injectMappingFileIdIntoResource - com_google_firebase_crashlytics_mappingfileid.xml test321"
      )
  }

  @Test
  fun `inject mapping file id task works with code generators`() {
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

          defaultConfig {
            minSdk = 24
          }
          buildTypes {
            release {
              isMinifyEnabled = true
            }
          }
          buildFeatures {
            viewBinding = true
          }
        }
      """
    )

    val result = buildGradleRunner(projectDir, ":assembleRelease", "--configuration-cache")

    assertThat(result.task(":uploadCrashlyticsMappingFileRelease")?.outcome).isEqualTo(SUCCESS)
    assertThat(result.output)
      .contains(
        "injectMappingFileIdIntoResource - com_google_firebase_crashlytics_mappingfileid.xml test321"
      )
  }

  @Test
  fun `injectCrashlyticsMappingFileIdRelease is UP_TO_DATE on second invocation`() {
    val first =
      buildGradleRunner(
        projectDir,
        ":injectCrashlyticsMappingFileIdRelease",
        "--configuration-cache"
      )

    assertThat(first.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)

    val second =
      buildGradleRunner(
        projectDir,
        ":injectCrashlyticsMappingFileIdRelease",
        "--configuration-cache"
      )

    assertThat(second.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(UP_TO_DATE)
  }

  @Test
  fun `release mapping file id is preserved across rebuilds`() {
    buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease", "--configuration-cache")

    val idFile = File(projectDir, "build/crashlytics/release/mappingFileId.txt")
    val idBefore = idFile.readText()
    assertThat(idBefore).isNotEmpty()

    buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease", "--configuration-cache")

    assertThat(idFile.readText()).isEqualTo(idBefore)
  }

  @Test
  fun `task re-runs after clean`() {
    buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease", "--configuration-cache")

    val idFile = File(projectDir, "build/crashlytics/release/mappingFileId.txt")

    val rerun =
      buildGradleRunner(
        projectDir,
        ":clean",
        ":injectCrashlyticsMappingFileIdRelease",
        "--configuration-cache",
      )

    assertThat(rerun.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)
    assertThat(idFile.exists()).isTrue()
    assertThat(idFile.readText()).isNotEmpty()
  }

  @Test
  fun `toggling mappingFileUploadEnabled invalidates the task`() {
    val first =
      buildGradleRunner(
        projectDir,
        ":injectCrashlyticsMappingFileIdRelease",
        "--configuration-cache"
      )

    assertThat(first.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)
    val idFile = File(projectDir, "build/crashlytics/release/mappingFileId.txt")
    assertThat(idFile.readText()).isEqualTo("test321")

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
              configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
              }
              isMinifyEnabled = true
            }
          }
        }
      """
    )

    val second =
      buildGradleRunner(
        projectDir,
        ":injectCrashlyticsMappingFileIdRelease",
        "--configuration-cache"
      )

    assertThat(second.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)
    assertThat(idFile.readText()).isEqualTo("00000000000000000000000000000000")
  }

  @Test
  fun `release mapping file id task is invalidated when source code changes`() {
    val sourceFile =
      File(projectDir, "src/main/kotlin/com/google/firebase/testing/crashlytics/Greeter.kt")
    sourceFile.parentFile.mkdirs()
    sourceFile.writeText(
      """
        package com.google.firebase.testing.crashlytics
        class Greeter { fun hello() = "hello" }
      """
    )

    val first = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(first.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)

    // Re-running without changing inputs must NOT regenerate the id (would invalidate downstream
    // R8, packaging, etc. — the bug PR #8185 originally fixed).
    val noChange = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(noChange.task(":injectCrashlyticsMappingFileIdRelease")?.outcome)
      .isEqualTo(UP_TO_DATE)

    // Editing the source MUST invalidate the task so a new id is minted: the on-device id is the
    // handle Crashlytics uses to match crashes to the right uploaded mapping.txt, and R8 will
    // produce a different mapping.txt after this edit.
    sourceFile.writeText(
      """
        package com.google.firebase.testing.crashlytics
        class Greeter { fun hello() = "hello world" }
      """
    )
    val afterEdit = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(afterEdit.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)
  }

  @Test
  fun `release mapping file id task is invalidated when the runtime classpath changes`() {
    val first = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(first.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)

    val noChange = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(noChange.task(":injectCrashlyticsMappingFileIdRelease")?.outcome)
      .isEqualTo(UP_TO_DATE)

    // Adding a dependency changes the obfuscated output (new code is bundled and renamed) with no
    // source edit, so R8 produces a different mapping.txt. fileTree("src") would miss this; the
    // @Classpath input must catch it.
    buildFile.appendText(
      """
        dependencies {
          implementation("org.jetbrains:annotations:23.0.0")
        }
      """
    )
    val afterDependency = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(afterDependency.task(":injectCrashlyticsMappingFileIdRelease")?.outcome)
      .isEqualTo(SUCCESS)
  }

  @Test
  fun `release mapping file id task is invalidated when proguard rules change`() {
    val rulesFile = File(projectDir, "proguard-rules.pro")
    rulesFile.writeText("-dontwarn com.google.firebase.testing.**")

    buildFile.writeText(
      """
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
              proguardFiles("proguard-rules.pro")
            }
          }
        }
      """
    )

    val first = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(first.task(":injectCrashlyticsMappingFileIdRelease")?.outcome).isEqualTo(SUCCESS)

    val noChange = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(noChange.task(":injectCrashlyticsMappingFileIdRelease")?.outcome)
      .isEqualTo(UP_TO_DATE)

    // Changing keep rules changes what R8 obfuscates, so the mapping.txt changes with no source
    // edit. The proguard config files are an input so the id must regenerate.
    rulesFile.appendText("\n-keep class com.google.firebase.testing.crashlytics.** { *; }\n")
    val afterRuleChange = buildGradleRunner(projectDir, ":injectCrashlyticsMappingFileIdRelease")
    assertThat(afterRuleChange.task(":injectCrashlyticsMappingFileIdRelease")?.outcome)
      .isEqualTo(SUCCESS)
  }

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

    buildFile.writeText(
      """
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
