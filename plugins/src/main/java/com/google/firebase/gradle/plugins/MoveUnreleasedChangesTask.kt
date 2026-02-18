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

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.provideDelegate

/**
 * Moves the `Unreleased` section in a changelog file into a new section for a release.
 *
 * Primarily utilized as a post-release clean up task in which we update `CHANGELOG.md` files after
 * a release, such that the changes that went out in said release are moved into their own section
 * for auditing purposes.
 *
 * @property changelogFile A [File] to use as the [Changelog]. Defaults to the `CHANGELOG.md` file
 *   at the project's root.
 * @property releaseVersion A [ModuleVersion] of what to set the version to. Defaults to the
 *   project's current version.
 * @see PostReleasePlugin
 */
abstract class MoveUnreleasedChangesTask : DefaultTask() {
  @get:[Optional InputFile]
  abstract val changelogFile: RegularFileProperty

  @get:[Optional Input]
  abstract val releaseVersion: Property<ModuleVersion>

  init {
    configure()
  }

  @TaskAction
  fun build() {
    val file = changelogFile.get().asFile
    val changelog = Changelog.fromFile(file)
    val (unreleased, previousReleases) = changelog.releases.separateAt(1)

    val newEntry = createEntryForRelease(unreleased.single())

    val releases = listOf(ReleaseEntry.Empty, newEntry) + previousReleases

    val newChangelog = Changelog(releases)

    file.writeText(newChangelog.toString())
  }

  /**
   * Creates an actual release entry for the current unreleased section.
   *
   * That is, it attaches the release version and creates the KTX content.
   *
   * @throws StopActionException if [unreleased] does not have any content
   * @see createEntryForKTX
   */
  private fun createEntryForRelease(unreleased: ReleaseEntry): ReleaseEntry {
    if (!unreleased.hasContent())
      throw StopActionException("No changes to move for project: ${project.name}")

    return unreleased.copy(version = releaseVersion.get())
  }

  private fun configure() {
    changelogFile.convention(project.layout.projectDirectory.file("CHANGELOG.md"))
    releaseVersion.convention(computeReleaseVersion())
  }

  private fun computeReleaseVersion(): ModuleVersion? {
    val version: String? by project

    return version?.let { ModuleVersion.fromStringOrNull(it) }
  }
}
