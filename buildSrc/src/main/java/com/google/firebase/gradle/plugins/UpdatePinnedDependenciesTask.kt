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

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction

/**
 * Changes the project level dependencies in a project's build file to pinned dependencies.
 *
 * Computes project level dependencies outside of the project's library group, looks for instances
 * of them in the `dependencies` block in the build file, and replaces them with pinned versions.
 * GMaven is used to figure out the latest version to use for a given dependency.
 *
 * For example, given the following input:
 * ```
 * dependencies {
 *    implementation(project(":appcheck:firebase-appcheck-interop"))
 * }
 * ```
 *
 * The output would be:
 * ```
 * dependencies {
 *    implementation("com.google.firebase:firebase-appcheck-interop:17.0.1")
 * }
 * ```
 *
 * *Assuming that `17.0.1` is the latest version of `firebase-appcheck-interop`*
 *
 * @property buildFile A [File] that should be used as the source to update from. Typically the
 * `build.gradle` or `build.gradle.kts` file for a given project.
 * @property outputFile A [File] that should be used to write the new text to. Typically the same as
 * the input file ([buildFile]).
 * @see PostReleasePlugin
 */
abstract class UpdatePinnedDependenciesTask : DefaultTask() {
  @get:[InputFile]
  abstract val buildFile: Property<File>

  @get:[OutputFile]
  abstract val outputFile: Property<File>

  @TaskAction
  fun build() {
    val needsChanging = findProjectLevelDependenciesToChange()

    if (needsChanging.isEmpty()) throw StopExecutionException("No libraries to change.")

    val lines = buildFile.get().readLines()
    val newLines = updateDependencyLines(lines, needsChanging)

    validateNewLines(needsChanging, lines, newLines)

    outputFile.get().writeText(newLines.joinToString("\n") + "\n")
  }

  private fun validateNewLines(
    needsChanging: List<FirebaseLibraryExtension>,
    lines: List<String>,
    newLines: List<String>
  ) {
    if (newLines == lines) throw missingProjectLevelDependenciesError(needsChanging)

    val diff = lines.diff(newLines)
    val changedLines = diff.mapNotNull { it.first ?: it.second }

    val (librariesCorrectlyChanged, linesChangedIncorrectly) =
      needsChanging.partition { lib -> changedLines.any { it.contains(lib.path) } }

    val librariesNotChanged = needsChanging - librariesCorrectlyChanged

    if (linesChangedIncorrectly.isNotEmpty())
      throw RuntimeException(
        "The following lines were caught by our REGEX, but should not have been:\n ${linesChangedIncorrectly.joinToString("\n")}"
      )

    if (librariesNotChanged.isNotEmpty())
      throw RuntimeException(
        "The following libraries were not found, but should have been:\n ${librariesNotChanged.joinToString("\n") { it.mavenName }}"
      )

    if (librariesCorrectlyChanged.size > needsChanging.size)
      throw RuntimeException(
        "Too many libraries were caught by our change, possible REGEX false positive:\n ${changedLines.joinToString("\n")}"
      )
  }

  private fun findProjectLevelDependenciesToChange(): List<FirebaseLibraryExtension> {
    val firebaseLibrary = project.firebaseLibrary
    val sisterProjects = firebaseLibrary.getLibrariesToRelease()
    val dependencies = projectLevelDependenciesForLibrary(firebaseLibrary)
    val needsChanging = dependencies - sisterProjects

    return needsChanging
  }

  private fun projectLevelDependenciesForLibrary(library: FirebaseLibraryExtension) =
    library.resolveProjectLevelDependencies().filterNot { it.path in DEPENDENCIES_TO_IGNORE }

  private fun updateDependencyLines(
    lines: List<String>,
    libraries: List<FirebaseLibraryExtension>
  ) =
    lines.replaceMatches(DEPENDENCY_REGEX) {
      val projectName = it.firstCapturedValue
      val projectToChange = libraries.find { it.path == projectName }
      val latestVersion = projectToChange?.latestVersion

      latestVersion?.let { "\"${projectToChange.mavenName}:$latestVersion\"" }
    }

  // This should never occur, but if it does- it's probably a regex error. Better safe than sorry?
  private fun missingProjectLevelDependenciesError(
    expectedLibraries: List<FirebaseLibraryExtension>
  ) =
    RuntimeException(
      "Expected the following project level dependencies, but found none: " +
        "${expectedLibraries.joinToString("\n") { it.mavenName }}"
    )

  companion object {
    /** TODO() */
    val DEPENDENCY_REGEX =
      Regex("(?<=\\s{1,20}\\w{1,20}|(?:\\s|\\())project\\((?:'|\")(:\\S+)(?:'|\")\\)")

    val DEPENDENCIES_TO_IGNORE = listOf(":protolite-well-known-types")
  }
}
