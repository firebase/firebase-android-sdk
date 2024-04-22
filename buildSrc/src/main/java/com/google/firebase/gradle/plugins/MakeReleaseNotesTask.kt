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

package com.google.firebase.gradle.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Creates the release notes for the given project.
 *
 * Utilizing the [Changelog] for the target project, this task uses the unreleased section to
 * generate appropriate release notes.
 *
 * @property changelogFile The `CHANGELOG.md` file to use as a [Changelog]
 * @property releaseNotesFile The output file to write the release notes to
 * @property skipMissingEntries Continue the build if the release notes are missing entries
 * @throws StopActionException If metadata does not exist for the given project, or there are no
 * changes to release
 * @see make
 */
@DisableCachingByDefault
abstract class MakeReleaseNotesTask : DefaultTask() {
  @get:InputFile abstract val changelogFile: RegularFileProperty

  @get:OutputFile abstract val releaseNotesFile: RegularFileProperty

  @get:Optional @get:Input abstract val skipMissingEntries: Property<Boolean>

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
    val unreleased = changelog.releases.first()
    val version = project.version.toString()
    val skipMissing = skipMissingEntries.getOrElse(false)

    if (!project.firebaseLibrary.publishReleaseNotes)
      throw StopActionException("No release notes required for ${project.name}")

    if (!unreleased.hasContent()) {
      if (skipMissing)
        throw StopActionException(
          "Missing releasing notes for  \"${project.name}\", but skip missing enabled."
        )

      throw GradleException("Missing release notes for \"${project.name}\"")
    }

    val versionClassifier = version.replace(".", "-")

    val baseReleaseNotes =
      """
        |### ${metadata.name} version $version {: #${metadata.versionName}_v$versionClassifier}
        |
        |${unreleased.content.toReleaseNotes()}
      """
        .trimMargin()
        .trim()

    val ktxReleaseNotes =
      """
          |#### ${metadata.name} Kotlin extensions version $version {: #${metadata.versionName}-ktx_v$versionClassifier}
          |
          |${unreleased.ktx?.toReleaseNotes() ?: KTXTransitiveReleaseText(project.name)}
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
   * Converts a [ReleaseContent] to a [String] to be used in a release note.
   *
   * @see [MakeReleaseNotesTask]
   * @see [Change.toReleaseNote]
   */
  private fun ReleaseContent.toReleaseNotes() =
    """
      |$subtext
      |
      |${changes.joinToString("\n\n") { it.toReleaseNote() }}
    """
      .trimMargin()
      .trim()

  /**
   * Converts a [Change] to a [String] to be used in a release note.
   *
   * @see [MakeReleaseNotesTask]
   * @see [LINK_REGEX]
   */
  private fun Change.toReleaseNote(): String {
    if (message.isBlank()) throw RuntimeException("A changelog entry message can not be blank.")

    val fixedMessage =
      LINK_REGEX.replace(message) {
        val id = it.firstCapturedValue
        "GitHub [#$id](//github.com/firebase/firebase-android-sdk/issues/$id){: .external}"
      }

    return "* {{${type.name.toLowerCase()}}} $fixedMessage"
  }

  companion object {
    /**
     * Regex for GitHub issue links in change messages.
     *
     * The regex can be described as such:
     * - Look for numbers that will be surrounded by either brackets or parentheses
     * - These numbers might be preceded by `GitHub `
     * - These numbers might also be followed by parentheses with `//` followed by some text (a
     * link)
     * - At the end there might be `{: .external}`
     *
     * For example:
     * ```markdown
     * We added (#123) and some
     * other cool stuff: [#321]
     * alongside [#5678](//github.com/firebase-firebase-android-sdk/issues/number)
     * ```
     *
     * Will find the following groups:
     * ```kotlin
     * [
     *   123,
     *   321,
     *   5678
     * ]
     * ```
     *
     * But will *match* the following:
     * ```kotlin
     * [
     *  "(#123)",
     *  "[#321]",
     *  "[#5678](//github.com/firebase-firebase-android-sdk/issues/number)"
     * ]
     * ```
     *
     * @see [Change.toReleaseNote]
     */
    private val LINK_REGEX =
      Regex(
        "(?:GitHub )?(?:\\[|\\()#(\\d+)(?:\\]|\\))(?:\\(.+?\\))?(?:\\{: \\.external\\})?",
        RegexOption.MULTILINE
      )
  }
}
