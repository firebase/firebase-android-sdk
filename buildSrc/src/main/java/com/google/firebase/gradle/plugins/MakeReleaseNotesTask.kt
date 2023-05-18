// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.gradle.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskAction

/**
 * Creates the release notes for the given project.
 *
 * Utilizing the [Changelog] for the target project, this task uses the unreleased section to
 * generate appropriate release notes.
 *
 * @property changelogFile The `CHANGELOG.md` file to use as a [Changelog]
 * @property releaseNotesFile The output file to write the release notes to
 * @throws StopActionException If metadata does not exist for the given project, or there are no
 * changes to release
 * @see make
 */
abstract class MakeReleaseNotesTask : DefaultTask() {
  @get:InputFile abstract val changelogFile: RegularFileProperty

  @get:OutputFile abstract val releaseNotesFile: RegularFileProperty

  /**
   * Converts the [changelogFile] into a [Changelog], and then uses that data to create release
   * notes.
   *
   * Example Input:
   * ```markdown
   * # Unreleased
   *
   * # 18.3.7
   * [feature] Added super cool stuff
   *
   * ## Kotlin
   * The Kotlin extensions library transitively includes the updated
   * `firebase-crashlytics` library. The Kotlin extensions library has no additional
   * updates.
   *
   * # 18.3.6
   * [changed] Changed some bad stuff
   * ```
   *
   * Example Output:
   * ```markdown
   * ### {{crashlytics}} version 18.3.7 {: #crashlytics_v18-3-7}
   *
   * * {{FEATURE}} Added super cool stuff
   *
   * #### {{crashlytics}} Kotlin extension version 18.3.7 {: #crashlytics-ktx_v18-3-7}
   *
   * The Kotlin extensions library transitively includes the updated
   * `firebase-crashlytics` library. The Kotlin extensions library has no additional
   * updates.
   * ```
   *
   * @see convertToMetadata
   */
  @TaskAction
  fun make() {
    val changelog = Changelog.fromFile(changelogFile.asFile.get())
    val metadata = convertToMetadata(project.name)
    val release = changelog.releases.first()
    val version = project.version.toString()

    if (!release.hasContent())
      throw StopActionException("No changes to release for project: ${project.name}")

    val versionClassifier = version.replace(".", "-")

    val baseReleaseNotes =
      """
        |### ${metadata.name} version $version {: #${metadata.versionName}_v$versionClassifier}
        |
        |${release.content.toReleaseNotes()}
      """
        .trimMargin()
        .trim()

    val ktxReleaseNotes =
      """
          |#### ${metadata.name} Kotlin extensions version $version {: #${metadata.versionName}-ktx_v$versionClassifier}
          |
          |${release.ktx?.toReleaseNotes() ?: KotlinTransitiveRelease(project.name)}
        """
        .trimMargin()
        .trim()
        .takeIf { metadata.hasKTX }

    val releaseNotes =
      """
        |$baseReleaseNotes
        |
        |${ktxReleaseNotes.orEmpty()}
      """
        .trimMargin()
        .trim()

    releaseNotesFile.asFile.get().writeText(releaseNotes)
  }

  /**
   * Provides default text for releasing KTX libs that are transitively invoked in a release,
   * because their parent module is releasing. This only applies to `-ktx` libs, not Kotlin SDKs.
   */
  private fun KotlinTransitiveRelease(projectName: String) =
    """
      |The Kotlin extensions library transitively includes the updated
      |`${ProjectNameToKTXPlaceholder(projectName)}` library. The Kotlin extensions library has no additional
      |updates.
    """
      .trimMargin()
      .trim()

  /**
   * Maps a project's name to a KTX suitable placeholder.
   *
   * Some libraries produce artifacts with different coordinates than their project name. This
   * method helps to map that gap for [KotlinTransitiveRelease].
   */
  private fun ProjectNameToKTXPlaceholder(projectName: String) =
    when (projectName) {
      "firebase-perf" -> "firebase-performance"
      "appcheck:firebase-appcheck" -> "firebase-appcheck"
      else -> projectName
    }

  /**
   * Converts a [ReleaseContent] to a [String] to be used in a release note.
   *
   * @see [MakeReleaseNotesTask]
   */
  private fun ReleaseContent.toReleaseNotes() =
    """
      |$subtext
      |
      |${changes.joinToString("\n\n") { "* {{${it.type.name.toLowerCase()}}} ${it.message}" }}
    """
      .trimMargin()
      .trim()

  /**
   * Maps the name of a project to its potential [ReleaseNotesMetadata].
   *
   * @throws StopActionException If a mapping is not found
   */
  // TODO() - Should we expose these as firebaselib configuration points; especially for new SDKS?
  private fun convertToMetadata(string: String) =
    when (string) {
      "firebase-abt" -> ReleaseNotesMetadata("{{ab_testing}}", "ab_testing", false)
      "firebase-appdistribution" -> ReleaseNotesMetadata("{{appdistro}}", "app-distro", false)
      "firebase-appdistribution-api" -> ReleaseNotesMetadata("{{appdistro}} API", "app-distro-api")
      "firebase-config" -> ReleaseNotesMetadata("{{remote_config}}", "remote-config")
      "firebase-crashlytics" -> ReleaseNotesMetadata("{{crashlytics}}", "crashlytics")
      "firebase-crashlytics-ndk" ->
        ReleaseNotesMetadata("{{crashlytics}} NDK", "crashlytics-ndk", false)
      "firebase-database" -> ReleaseNotesMetadata("{{database}}", "realtime-database")
      "firebase-dynamic-links" -> ReleaseNotesMetadata("{{ddls}}", "dynamic-links")
      "firebase-firestore" -> ReleaseNotesMetadata("{{firestore}}", "firestore")
      "firebase-functions" -> ReleaseNotesMetadata("{{functions_client}}", "functions-client")
      "firebase-dynamic-module-support" ->
        ReleaseNotesMetadata(
          "Dynamic feature modules support",
          "dynamic-feature-modules-support",
          false
        )
      "firebase-inappmessaging" -> ReleaseNotesMetadata("{{inappmessaging}}", "inappmessaging")
      "firebase-inappmessaging-display" ->
        ReleaseNotesMetadata("{{inappmessaging}} Display", "inappmessaging-display")
      "firebase-installations" ->
        ReleaseNotesMetadata("{{firebase_installations}}", "installations")
      "firebase-messaging" -> ReleaseNotesMetadata("{{messaging_longer}}", "messaging")
      "firebase-messaging-directboot" ->
        ReleaseNotesMetadata("Cloud Messaging Direct Boot", "messaging-directboot", false)
      "firebase-ml-modeldownloader" ->
        ReleaseNotesMetadata("{{firebase_ml}}", "firebaseml-modeldownloader")
      "firebase-perf" -> ReleaseNotesMetadata("{{perfmon}}", "performance")
      "firebase-storage" -> ReleaseNotesMetadata("{{firebase_storage_full}}", "storage")
      "appcheck:firebase-appcheck" -> ReleaseNotesMetadata("{{app_check}}", "appcheck")
      "appcheck:firebase-appcheck-debug" ->
        ReleaseNotesMetadata("{{app_check}} Debug", "appcheck-debug", false)
      "appcheck:firebase-appcheck-debug-testing" ->
        ReleaseNotesMetadata("{{app_check}} Debug Testing", "appcheck-debug-testing", false)
      "appcheck:firebase-appcheck-playintegrity" ->
        ReleaseNotesMetadata("{{app_check}} Play integrity", "appcheck-playintegrity", false)
      "appcheck:firebase-appcheck-safetynet" ->
        ReleaseNotesMetadata("{{app_check}} SafetyNet", "appcheck-safetynet", false)
      else -> throw StopActionException("No metadata mapping found for project: $string")
    }
}

/**
 * Provides extra metadata needed to create release notes for a given project.
 *
 * This data is needed for g3 internal mappings, and does not really have any implications for
 * public repo actions.
 *
 * @property name The variable name for a project in a release note
 * @property vesionName The variable name given to the versions of a project
 * @property hasKTX The module has a KTX submodule (not to be confused with having KTX files)
 * @see MakeReleaseNotesTask
 */
data class ReleaseNotesMetadata(
  val name: String,
  val versionName: String,
  val hasKTX: Boolean = true
)
