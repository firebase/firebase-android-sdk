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
 *   changes to release
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
   * @see ReleaseNotesConfigurationExtension
   */
  @TaskAction
  fun make() {
    val changelog = Changelog.fromFile(changelogFile.asFile.get())
    val config = project.firebaseLibrary.releaseNotes
    val unreleased = changelog.releases.first()
    val version = project.version.toString()
    val skipMissing = skipMissingEntries.getOrElse(false)

    if (!config.enabled.get())
      throw StopActionException("No release notes required for ${project.name}")

    if (!unreleased.hasContent()) {
      if (skipMissing)
        throw StopActionException(
          "Missing releasing notes for  \"${project.name}\", but skip missing enabled."
        )

      throw GradleException("Missing release notes for \"${project.name}\"")
    }

    val versionClassifier = version.replace(".", "-")

    val releaseNotes =
      """
        |### ${config.name.get()} version $version {: #${config.versionName.get()}_v$versionClassifier}
        |
        |${unreleased.content.toReleaseNotes()}
      """
        .trimMargin()
        .trim()

    releaseNotesFile.asFile.get().writeText(releaseNotes + "\n")
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
   * It applies the [CONTENT_FORMATTERS] functions to the content in order to transform it.
   *
   * @see [MakeReleaseNotesTask]
   * @see [LINK_REGEX]
   */
  private fun Change.toReleaseNote(): String {
    if (message.isBlank()) throw RuntimeException("A changelog entry message can not be blank.")
    val fixedMessage = CONTENT_FORMATTERS.fold(message) { acc, formatter -> formatter(acc) }
    return "* {{${type.name.lowercase()}}} $fixedMessage"
  }

  private companion object {

    /**
     * Formats github issues link.
     *
     * Using the regex [LINK_REGEX], this function formats references to github issues or PRs into
     * actual links.
     */
    private fun githubIssueLinkFormatter(message: String): String =
      LINK_REGEX.replace(message) {
        val id = it.firstCapturedValue
        "GitHub [#$id](//github.com/firebase/firebase-android-sdk/issues/$id){: .external}"
      }

    /**
     * Formats product name references.
     *
     * Using the regex [PRODUCT_REF_REGEX], this function formats product names as variables, i.e. a
     * string between 2 set of brackets.
     *
     * See the regex to know more about the assumptions made about the content.
     */
    private fun productNameFormatter(message: String): String =
      PRODUCT_REF_REGEX.replace(message) { "{{${it.firstCapturedValue}}}${it.groupValues[2]}" }

    /**
     * List of functions to apply to the content.
     *
     * The functions should take the content as modified by the previous function, apply it's own
     * customization, and then return the value that will be passed to the next formatter, or, if
     * there are no more, used as the actual content.
     */
    private val CONTENT_FORMATTERS: List<(String) -> String> =
      listOf(::githubIssueLinkFormatter, ::productNameFormatter)

    /**
     * Regex for GitHub issue links in change messages.
     *
     * The regex can be described as such:
     * - Look for numbers that will be surrounded by either brackets or parentheses
     * - These numbers might be preceded by `GitHub `
     * - These numbers might also be followed by parentheses with `//` followed by some text (a
     *   link)
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
        "(?:GitHub )?(?:\\[|\\()#(\\d+)(?:\\]|\\))(?:\\(.+?\\))?(?:\\{:\\s*\\.external\\})?",
        RegexOption.MULTILINE,
      )

    /**
     * Regex for product references in change messages.
     *
     * Matches single bracketed product names, for example: `[app-check]`
     *
     * The assumption here is that any string between brackets, and not followed by an open
     * parenthesis, is a product name.
     *
     * Groups:
     * 1. The product name (e.g., `app-check`)
     * 2. The character following the closing bracket (or an empty string if at the end of the line)
     */
    private val PRODUCT_REF_REGEX = Regex("\\[([\\w-]+)\\]([^(]|$)", RegexOption.MULTILINE)
  }
}
