package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.bomgenerator.GenerateTutorialBundleTask
import com.google.firebase.gradle.plugins.services.GMavenService
import com.google.firebase.gradle.shouldBeDiff
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
            "com.google.firebase:firebase-vertexai:9.2.3",
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

    tutorialFile.readText().trim() shouldBeDiff
      """
      <!DOCTYPE root [
        <!-- Common Firebase dependencies -->
        <!-- Google Services Plugin -->
        <!ENTITY google-services-plugin-class "1.2.3">
        <!ENTITY google-services-plugin "com.google.gms.google-services">
        <!ENTITY gradle-plugin-class "com.android.tools.build:gradle:8.1.0">

        <!-- Firebase SDK libraries -->
        <!-- Analytics -->
        <!ENTITY analytics-dependency "1.2.4">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-dependency "12.0.0">
        <!-- Performance Monitoring -->
        <!ENTITY perf-dependency "10.2.3">
        <!-- Vertex AI in Firebase -->
        <!ENTITY vertex-dependency "9.2.3">

        <!-- Firebase Gradle plugins -->
        <!-- App Distribution -->
        <!ENTITY appdistribution-plugin-class "1.21.3">
        <!ENTITY appdistribution-plugin "com.google.firebase.appdistribution">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-plugin-class "15.1.0">
        <!ENTITY crashlytics-plugin "com.google.firebase.crashlytics">
        <!-- Perf Plugin -->
        <!ENTITY perf-plugin-class "20.5.6">
        <!ENTITY perf-plugin "com.google.firebase.firebase-perf">
      ]>
    """
        .trimIndent()
  }

  @Test
  fun `does not include empty sections`() {
    val tutorialFile = makeTutorial()

    tutorialFile.readText().trim() shouldBeDiff
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

    tutorialFile.readText().trim() shouldBeDiff
      """
      <!DOCTYPE root [
        <!-- Common Firebase dependencies -->
        <!-- Google Services Plugin -->
        <!ENTITY google-services-plugin-class "3.2.1">
        <!ENTITY google-services-plugin "com.google.gms.google-services">
        <!ENTITY gradle-plugin-class "com.android.tools.build:gradle:8.1.0">
        <!-- Performance Monitoring -->
        <!ENTITY perf-dependency "10.2.3">

        <!-- Firebase SDK libraries -->
        <!-- Analytics -->
        <!ENTITY analytics-dependency "1.2.4">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-dependency "1.2.12">

        <!-- Firebase Gradle plugins -->
        <!-- App Distribution -->
        <!ENTITY appdistribution-plugin-class "1.21.3">
        <!ENTITY appdistribution-plugin "com.google.firebase.appdistribution">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-plugin-class "1.15.0">
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

    tutorialFile.readText().trim() shouldBeDiff
      """
      <!DOCTYPE root [
        <!-- Common Firebase dependencies -->
        <!-- Google Services Plugin -->
        <!ENTITY google-services-plugin-class "1.2.3">
        <!ENTITY google-services-plugin "com.google.gms.google-services">
        <!ENTITY gradle-plugin-class "com.android.tools.build:gradle:8.1.0">
        <!-- Performance Monitoring -->
        <!ENTITY perf-dependency "10.2.3">

        <!-- Firebase SDK libraries -->
        <!-- Analytics -->
        <!ENTITY analytics-dependency "1.2.4">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-dependency "12.0.0">

        <!-- Firebase Gradle plugins -->
        <!-- App Distribution -->
        <!ENTITY appdistribution-plugin-class "1.21.3">
        <!ENTITY appdistribution-plugin "com.google.firebase.appdistribution">
        <!-- Crashlytics -->
        <!ENTITY crashlytics-plugin-class "15.1.0">
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
  fun `throws an error if an artifact hasn't been added to the local map`() {
    shouldThrowSubstring(
      "Artifacts required for the tutorial bundle are missing",
      "Please update the",
      "com.google.firebase:firebase-common",
    ) {
      makeTutorial(firebaseArtifacts = listOf("com.google.firebase:firebase-common:1.2.4"))
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
    file.readText().trim() shouldBeDiff
      """
      <!DOCTYPE root [
        <!-- Firebase SDK libraries -->
        <!-- Authentication -->
        <!ENTITY auth-dependency "10.0.0">
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
