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

package com.google.firebase.gradle.bomgenerator

import com.google.firebase.gradle.plugins.createIfAbsent
import com.google.firebase.gradle.plugins.multiLine
import com.google.firebase.gradle.plugins.orEmpty
import com.google.firebase.gradle.plugins.services.GMavenService
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates the tutorial bundle recipe for a release.
 *
 * This task uses gmaven as a source of truth, and as such, should be ran _after_ the artifacts are
 * live on gmaven.
 *
 * @see GenerateBomTask
 */
abstract class GenerateTutorialBundleTask : DefaultTask() {
  /**
   * Firebase library artifacts.
   *
   * ```
   * firebaseArtifacts.set(listOf(
   *   "com.google.firebase:firebase-analytics",
   *   "com.google.firebase:firebase-crashlytics"
   * ))
   * ```
   */
  @get:Input abstract val firebaseArtifacts: ListProperty<String>

  /**
   * Common artifacts and dependencies whose versions also need to be tracked during releases.
   *
   * ```
   * commonArtifacts.set(listOf(
   *   "com.google.gms:google-services",
   * ))
   * ```
   */
  @get:Input abstract val commonArtifacts: ListProperty<String>

  /**
   * Firebase gradle plugins.
   *
   * ```
   * gradlePlugins.set(listOf(
   *   "com.google.firebase:firebase-appdistribution-gradle",
   *   "com.google.firebase:firebase-crashlytics-gradle"
   * ))
   * ```
   */
  @get:Input abstract val gradlePlugins: ListProperty<String>

  /**
   * Performance monitoring related artifacts.
   *
   * ```
   * firebaseArtifacts.set(listOf(
   *   "com.google.firebase:perf-plugin"
   * ))
   * ```
   */
  @get:Input abstract val perfArtifacts: ListProperty<String>

  /**
   * All artifacts that are expected to be present.
   *
   * You can use this to verify that the input doesn't exclude any artifacts.
   *
   * ```
   * requiredArtifacts.set(listOf(
   *   "com.google.firebase:firebase-analytics",
   *   "com.google.firebase:perf-plugin"
   * ))
   * ```
   */
  @get:Input abstract val requiredArtifacts: ListProperty<String>

  /**
   * Optional map of versions to use instead of the versions on gmaven.
   *
   * ```
   * versionOverrides.set(mapOf(
   *   "com.google.firebase:firebase-firestore" to "10.0.0"
   * ))
   * ```
   */
  @get:Input abstract val versionOverrides: MapProperty<String, String>

  /** The file to save the generated tutorial to. */
  @get:OutputFile abstract val tutorialFile: RegularFileProperty

  @get:ServiceReference("gmaven") abstract val gmaven: Property<GMavenService>

  @TaskAction
  fun generate() {
    val firebaseArtifacts = firebaseArtifacts.orEmpty()
    val commonArtifacts = commonArtifacts.orEmpty()
    val gradlePlugins = gradlePlugins.orEmpty()
    val perfArtifacts = perfArtifacts.orEmpty()
    val requiredArtifacts = requiredArtifacts.orEmpty()

    val allArtifacts = firebaseArtifacts + commonArtifacts + gradlePlugins + perfArtifacts

    val missingArtifacts = requiredArtifacts - allArtifacts.toSet()
    if (missingArtifacts.isNotEmpty()) {
      throw RuntimeException(
        multiLine(
          "Artifacts required for the tutorial bundle are missing from the provided input:",
          missingArtifacts,
        )
      )
    }

    val sections =
      listOfNotNull(
        generateSection("Common Firebase dependencies", commonArtifacts),
        generateSection("Firebase SDK libraries", firebaseArtifacts),
        generateSection("Firebase Gradle plugins", gradlePlugins),
        generateSection("Performance Monitoring", perfArtifacts),
      )

    tutorialFile
      .get()
      .asFile
      .createIfAbsent()
      .writeText(
        """
         |<!DOCTYPE root [
         |${sections.joinToString("\n\n")}
         |]>
         |
        """
          .trimMargin()
      )
  }

  private fun generateSection(name: String, artifacts: List<String>): String? {
    if (artifacts.isEmpty()) {
      logger.warn("Skipping section, since no data was provided: $name")
      return null
    } else {
      logger.info("Using artifacts for section ($name): ${artifacts.joinToString()}")
    }

    val mappingKeys = mappings.keys

    val (supported, unsupported) = artifacts.partition { mappingKeys.contains(it) }
    if (unsupported.isNotEmpty()) {
      logger.info(
        multiLine(
          "The following artifacts are missing mapping keys.",
          "This is likely intentional, but the artifacts will be listed for debugging purposes:",
          unsupported,
        )
      )
    }

    val sortedArtifacts = supported.sortedBy { mappingKeys.indexOf(it) }
    val artifactSection = sortedArtifacts.map { artifactVariableString(it) }

    return multiLine("<!-- $name -->", artifactSection).prependIndent("  ")
  }

  private fun versionString(fullArtifactName: String): String {
    val overrideVersion = versionOverrides.orEmpty()[fullArtifactName]

    if (overrideVersion != null) {
      logger.info("Using a version override for an artifact ($fullArtifactName): $overrideVersion")

      return overrideVersion
    } else {
      logger.info("Fetching the latest version for an artifact: $fullArtifactName")

      return gmaven.get().latestVersionOrNull(fullArtifactName)
        ?: throw RuntimeException(
          "An artifact required for the tutorial bundle is missing from gmaven: $fullArtifactName"
        )
    }
  }

  private fun artifactVariableString(fullArtifactName: String): String {
    val (name, alias, extra) = mappings[fullArtifactName]!!

    return multiLine(
      "<!-- $name -->",
      "<!ENTITY $alias \"$fullArtifactName:${versionString(fullArtifactName)}\">",
      extra,
    )
  }

  companion object {
    /**
     * A linked mapping for artifact ids to their respective [ArtifactTutorialMapping] metadata.
     *
     * Since this is a _linked_ map, the order is preserved in the tutorial output.
     */
    private val mappings =
      linkedMapOf(
        "com.google.gms:google-services" to
          ArtifactTutorialMapping(
            "Google Services Plugin",
            "google-services-plugin-class",
            listOf(
              "<!ENTITY google-services-plugin \"com.google.gms.google-services\">",
              "<!ENTITY gradle-plugin-class \"com.android.tools.build:gradle:8.1.0\">",
            ),
          ),
        "com.google.firebase:firebase-analytics" to
          ArtifactTutorialMapping("Analytics", "analytics-dependency"),
        "com.google.firebase:firebase-crashlytics" to
          ArtifactTutorialMapping("Crashlytics", "crashlytics-dependency"),
        "com.google.firebase:firebase-perf" to
          ArtifactTutorialMapping("Performance Monitoring", "perf-dependency"),
        "com.google.firebase:firebase-ai" to
          ArtifactTutorialMapping("Firebase AI Logic", "firebase-ai-dependency"),
        "com.google.firebase:firebase-messaging" to
          ArtifactTutorialMapping("Cloud Messaging", "messaging-dependency"),
        "com.google.firebase:firebase-auth" to
          ArtifactTutorialMapping("Authentication", "auth-dependency"),
        "com.google.firebase:firebase-database" to
          ArtifactTutorialMapping("Realtime Database", "database-dependency"),
        "com.google.firebase:firebase-storage" to
          ArtifactTutorialMapping("Cloud Storage", "storage-dependency"),
        "com.google.firebase:firebase-config" to
          ArtifactTutorialMapping("Remote Config", "remote-config-dependency"),
        "com.google.android.gms:play-services-ads" to
          ArtifactTutorialMapping("Admob", "ads-dependency"),
        "com.google.firebase:firebase-firestore" to
          ArtifactTutorialMapping("Cloud Firestore", "firestore-dependency"),
        "com.google.firebase:firebase-functions" to
          ArtifactTutorialMapping("Firebase Functions", "functions-dependency"),
        "com.google.firebase:firebase-inappmessaging-display" to
          ArtifactTutorialMapping("FIAM Display", "fiamd-dependency"),
        "com.google.firebase:firebase-ml-vision" to
          ArtifactTutorialMapping("Firebase MLKit Vision", "ml-vision-dependency"),
        "androidx.credentials:credentials" to
          ArtifactTutorialMapping("Auth Google Sign In", "auth-google-signin-first-dependency"),
        "androidx.credentials:credentials-play-services-auth" to
          ArtifactTutorialMapping("Auth Google Sign In", "auth-google-signin-second-dependency"),
        "com.google.android.libraries.identity.googleid:googleid" to
          ArtifactTutorialMapping("Auth Google Sign In", "auth-google-signin-third-dependency"),
        "com.google.firebase:firebase-appdistribution-gradle" to
          ArtifactTutorialMapping(
            "App Distribution",
            "appdistribution-plugin-class",
            listOf("<!ENTITY appdistribution-plugin \"com.google.firebase.appdistribution\">"),
          ),
        "com.google.firebase:firebase-crashlytics-gradle" to
          ArtifactTutorialMapping(
            "Crashlytics",
            "crashlytics-plugin-class",
            listOf("<!ENTITY crashlytics-plugin \"com.google.firebase.crashlytics\">"),
          ),
        "com.google.firebase:perf-plugin" to
          ArtifactTutorialMapping(
            "Perf Plugin",
            "perf-plugin-class",
            listOf("<!ENTITY perf-plugin \"com.google.firebase.firebase-perf\">"),
          ),
      )
  }
}

/**
 * Metadata for an artifact to use in generation of the tutorial.
 *
 * For example, given the following:
 * ```
 * ArtifactTutorialMapping(
 *   "Perf Plugin",
 *   "perf-plugin-class",
 *   listOf("<!ENTITY perf-plugin \"com.google.firebase.firebase-perf\">")
 * )
 * ```
 *
 * The tutorial will generate the following output:
 * ```html
 * <!-- Perf Plugin -->
 * <!ENTITY perf-plugin-class "1.2.3">
 * <!ENTITY perf-plugin "com.google.firebase.firebase-perf">
 * ```
 *
 * _Assuming the latest version on gmaven is `1.2.3`._
 *
 * @property name The space separated, capitalized, full name of the artifact.
 * @property alias The internal alias of the artifact.
 * @property extra Optional additional data to add after the metadata entry in the tutorial.
 * @see GenerateTutorialBundleTask.mappings
 */
private data class ArtifactTutorialMapping(
  val name: String,
  val alias: String,
  val extra: List<String> = emptyList(),
)
