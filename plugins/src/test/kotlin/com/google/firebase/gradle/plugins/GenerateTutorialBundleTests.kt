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

package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.bomgenerator.GenerateTutorialBundleTask
import com.google.firebase.gradle.plugins.services.GMavenService
import com.google.firebase.gradle.shouldBeText
import com.google.firebase.gradle.shouldThrowSubstring
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.file.shouldExist
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenerateTutorialBundleTests : FunSpec() {
  @Rule @JvmField val testProjectDir = TemporaryFolder()

  private val service = mockk<GMavenService>()

  @Before
  fun setup() {
    clearMocks(service)
  }

  @Test
  fun `generates the tutorial bundle`() {
    val tutorialFile =
      makeTutorial(
        commonArtifacts = listOf("com.google.gms:google-services:1.2.3"),
        firebaseArtifacts =
          listOf(
            "com.google.firebase:firebase-analytics:1.2.4",
            "com.google.firebase:firebase-crashlytics:12.0.0",
            "com.google.firebase:firebase-perf:10.2.3",
            "com.google.firebase:firebase-ai:9.2.3",
          ),
        gradlePlugins =
          listOf(
            "com.google.firebase:firebase-appdistribution-gradle:1.21.3",
            "com.google.firebase:firebase-crashlytics-gradle:15.1.0",
            "com.google.firebase:perf-plugin:20.5.6",
          ),
      ) {
        requiredArtifacts.set(listOf("com.google.firebase:firebase-crashlytics"))
      }

    tutorialFile.readText().trim() shouldBeText
      """
        <!DOCTYPE root [
          <!-- Common Firebase dependencies -->
          <!-- Google Services Plugin -->
          <!ENTITY google-services-plugin-class "com.google.gms:google-services:1.2.3">
          <!ENTITY google-services-plugin "com.google.gms.google-services">
          <!ENTITY gradle-plugin-class "com.android.tools.build:gradle:8.1.0">

          <!-- Firebase SDK libraries -->
          <!-- Analytics -->
          <!ENTITY analytics-dependency "com.google.firebase:firebase-analytics:1.2.4">
          <!-- Crashlytics -->
          <!ENTITY crashlytics-dependency "com.google.firebase:firebase-crashlytics:12.0.0">
          <!-- Performance Monitoring -->
          <!ENTITY perf-dependency "com.google.firebase:firebase-perf:10.2.3">
          <!-- Firebase AI Logic -->
          <!ENTITY firebase-ai-dependency "com.google.firebase:firebase-ai:9.2.3">

          <!-- Firebase Gradle plugins -->
          <!-- App Distribution -->
          <!ENTITY appdistribution-plugin-class "com.google.firebase:firebase-appdistribution-gradle:1.21.3">
          <!ENTITY appdistribution-plugin "com.google.firebase.appdistribution">
          <!-- Crashlytics -->
          <!ENTITY crashlytics-plugin-class "com.google.firebase:firebase-crashlytics-gradle:15.1.0">
          <!ENTITY crashlytics-plugin "com.google.firebase.crashlytics">
          <!-- Perf Plugin -->
          <!ENTITY perf-plugin-class "com.google.firebase:perf-plugin:20.5.6">
          <!ENTITY perf-plugin "com.google.firebase.firebase-perf">
        ]>
      """
        .trimIndent()
  }

  @Test
  fun `does not include empty sections`() {
    val tutorialFile = makeTutorial()

    tutorialFile.readText().trim() shouldBeText
      """
      <!DOCTYPE root [

      ]>
    """
        .trimIndent()
  }

  @Test
  fun `allows versions to be overridden`() {
    val tutorialFile =
      makeTutorial(
        commonArtifacts =
          listOf(
            "com.google.gms:google-services:1.2.3",
            "com.google.firebase:firebase-perf:10.2.3",
          ),
        firebaseArtifacts =
          listOf(
            "com.google.firebase:firebase-analytics:1.2.4",
            "com.google.firebase:firebase-crashlytics:12.0.0",
          ),
        gradlePlugins =
          listOf(
            "com.google.firebase:firebase-appdistribution-gradle:1.21.3",
            "com.google.firebase:firebase-crashlytics-gradle:15.1.0",
          ),
      ) {
        versionOverrides.set(
          mapOf(
            "com.google.gms:google-services" to "3.2.1",
            "com.google.firebase:firebase-crashlytics" to "1.2.12",
            "com.google.firebase:firebase-crashlytics-gradle" to "1.15.0",
          )
        )
      }

    tutorialFile.readText().trim() shouldBeText
      """
      <!DOCTYPE root [
        <!-- Common Firebase dependencies -->
        <!-- Google Services Plugin -->
        <!ENTITY google-services-plugin-class "com.google.gms:google-services:3.2.1">
        <!ENTITY google-services-plugin "com.google.gms.google-services">
        <!ENTITY gradle-plugin-class "com.android.tools.build:gradle:8.1.0">
        <!-- Performance Monitoring -->
        <!ENTITY perf-dependency "com.google.firebase:firebase-perf:10.2.3">

        <!-- Firebase SDK libraries -->
        <!-- Analytics -->
        <!ENTITY analytics-dependency "com.google.firebase:firebase-analytics:1.2.4">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-dependency "com.google.firebase:firebase-crashlytics:1.2.12">

        <!-- Firebase Gradle plugins -->
        <!-- App Distribution -->
        <!ENTITY appdistribution-plugin-class "com.google.firebase:firebase-appdistribution-gradle:1.21.3">
        <!ENTITY appdistribution-plugin "com.google.firebase.appdistribution">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-plugin-class "com.google.firebase:firebase-crashlytics-gradle:1.15.0">
        <!ENTITY crashlytics-plugin "com.google.firebase.crashlytics">
      ]>
    """
        .trimIndent()
  }

  @Test
  fun `enforces the predefined order of artifacts`() {
    val tutorialFile =
      makeTutorial(
        commonArtifacts =
          listOf(
            "com.google.firebase:firebase-perf:10.2.3",
            "com.google.gms:google-services:1.2.3",
          ),
        firebaseArtifacts =
          listOf(
            "com.google.firebase:firebase-crashlytics:12.0.0",
            "com.google.firebase:firebase-analytics:1.2.4",
          ),
        gradlePlugins =
          listOf(
            "com.google.firebase:firebase-crashlytics-gradle:15.1.0",
            "com.google.firebase:firebase-appdistribution-gradle:1.21.3",
          ),
      )

    tutorialFile.readText().trim() shouldBeText
      """
      <!DOCTYPE root [
        <!-- Common Firebase dependencies -->
        <!-- Google Services Plugin -->
        <!ENTITY google-services-plugin-class "com.google.gms:google-services:1.2.3">
        <!ENTITY google-services-plugin "com.google.gms.google-services">
        <!ENTITY gradle-plugin-class "com.android.tools.build:gradle:8.1.0">
        <!-- Performance Monitoring -->
        <!ENTITY perf-dependency "com.google.firebase:firebase-perf:10.2.3">

        <!-- Firebase SDK libraries -->
        <!-- Analytics -->
        <!ENTITY analytics-dependency "com.google.firebase:firebase-analytics:1.2.4">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-dependency "com.google.firebase:firebase-crashlytics:12.0.0">

        <!-- Firebase Gradle plugins -->
        <!-- App Distribution -->
        <!ENTITY appdistribution-plugin-class "com.google.firebase:firebase-appdistribution-gradle:1.21.3">
        <!ENTITY appdistribution-plugin "com.google.firebase.appdistribution">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-plugin-class "com.google.firebase:firebase-crashlytics-gradle:15.1.0">
        <!ENTITY crashlytics-plugin "com.google.firebase.crashlytics">
      ]>
    """
        .trimIndent()
  }

  @Test
  fun `throws an error if required artifacts are missing`() {
    shouldThrowSubstring(
      "Artifacts required for the tutorial bundle are missing from the provided input",
      "com.google.firebase:firebase-auth",
    ) {
      makeTutorial(
        firebaseArtifacts =
          listOf(
            "com.google.firebase:firebase-crashlytics:12.0.0",
            "com.google.firebase:firebase-analytics:1.2.4",
          )
      ) {
        requiredArtifacts.add("com.google.firebase:firebase-auth")
      }
    }
  }

  @Test
  fun `throws an error if an unreleased artifact is used`() {
    shouldThrowSubstring("missing from gmaven", "com.google.firebase:firebase-auth") {
      every { service.latestVersionOrNull(any()) } answers { null }

      val task = makeTask { firebaseArtifacts.set(listOf("com.google.firebase:firebase-auth")) }

      task.get().generate()
    }
  }

  @Test
  fun `allows unreleased artifacts to be used if the version is provided`() {
    val task = makeTask {
      firebaseArtifacts.set(listOf("com.google.firebase:firebase-auth"))
      versionOverrides.set(mapOf("com.google.firebase:firebase-auth" to "10.0.0"))
    }

    val file =
      task.get().let {
        it.generate()
        it.tutorialFile.get().asFile
      }

    file.shouldExist()
    file.readText().trim() shouldBeText
      """
        <!DOCTYPE root [
          <!-- Firebase SDK libraries -->
          <!-- Authentication -->
          <!ENTITY auth-dependency "com.google.firebase:firebase-auth:10.0.0">
        ]>
      """
        .trimIndent()
  }

  private fun makeTask(
    configure: GenerateTutorialBundleTask.() -> Unit
  ): TaskProvider<GenerateTutorialBundleTask> {
    val project = ProjectBuilder.builder().withProjectDir(testProjectDir.root).build()
    return project.tasks.register<GenerateTutorialBundleTask>("generateTutorialBundle") {
      tutorialFile.set(project.layout.buildDirectory.file("tutorial.txt"))
      gmaven.set(service)

      configure()
    }
  }

  private fun artifactsToVersionMap(artifacts: List<String>): Map<String, String> {
    return artifacts
      .associate {
        val (groupId, artifactId, version) = it.split(":")
        "$groupId:$artifactId" to version
      }
      .onEach { entry -> every { service.latestVersionOrNull(entry.key) } answers { entry.value } }
  }

  private fun makeTutorial(
    firebaseArtifacts: List<String> = emptyList(),
    commonArtifacts: List<String> = emptyList(),
    gradlePlugins: List<String> = emptyList(),
    perfArtifacts: List<String> = emptyList(),
    configure: GenerateTutorialBundleTask.() -> Unit = {},
  ): File {

    val mappedFirebaseArtifacts = artifactsToVersionMap(firebaseArtifacts)
    val mappedCommonArtifacts = artifactsToVersionMap(commonArtifacts)
    val mappedGradlePlugins = artifactsToVersionMap(gradlePlugins)
    val mappedPerfArtifacts = artifactsToVersionMap(perfArtifacts)

    val task = makeTask {
      this.firebaseArtifacts.set(mappedFirebaseArtifacts.keys)
      this.commonArtifacts.set(mappedCommonArtifacts.keys)
      this.gradlePlugins.set(mappedGradlePlugins.keys)
      this.perfArtifacts.set(mappedPerfArtifacts.keys)
      configure()
    }

    val file =
      task.get().let {
        it.generate()
        it.tutorialFile.get().asFile
      }

    file.shouldExist()

    return file
  }
}
