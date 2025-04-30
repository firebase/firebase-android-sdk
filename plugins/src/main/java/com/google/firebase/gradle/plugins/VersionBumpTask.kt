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
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.provideDelegate

/**
 * Bumps the `version` property of the specified [versionFile], and sets the
 * `latestReleasedVersion`.
 *
 * Primarily utilized as a post-release clean up task in which we bump the versions of released
 * modules to be one patch higher than their currently released counterparts, and update their
 * latest released version.
 *
 * @property versionFile A [File] that contains the `version` property. Defaults to the
 *   `gradle.properties` file at the project's root.
 * @property releasedVersion A [ModuleVersion] of what to bump from. Defaults to the project
 *   version.
 * @property newVersion A [ModuleVersion] of what to set the version to. Defaults to one patch
 *   higher than [releasedVersion].
 * @property bumpVersion If set, then the default provided by [newVersion] will be bumped by one
 *   patch. Defaults to `true`.
 * @see PostReleasePlugin
 */
abstract class VersionBumpTask : DefaultTask() {
  @get:[Optional InputFile]
  abstract val versionFile: RegularFileProperty

  @get:[Optional Input]
  abstract val releasedVersion: Property<ModuleVersion>

  @get:[Optional Input]
  abstract val newVersion: Property<ModuleVersion>

  @get:[Optional Input]
  abstract val bumpVersion: Property<Boolean>

  init {
    configure()
  }

  @TaskAction
  fun build() {
    val latestVersion = releasedVersion.get()
    val version =
      newVersion.orNull ?: if (bumpVersion.get()) latestVersion.bump() else latestVersion

    versionFile.get().asFile.rewriteLines {
      when {
        it.startsWith("version=") -> "version=${version}"
        it.startsWith("latestReleasedVersion") -> "latestReleasedVersion=${latestVersion}"
        else -> it
      }
    }
  }

  fun configure() {
    versionFile.convention(project.layout.projectDirectory.file("gradle.properties"))
    releasedVersion.convention(computeReleasedVersion())
    bumpVersion.convention(true)
  }

  fun computeReleasedVersion(): ModuleVersion? {
    val version: String? by project

    return version?.let { ModuleVersion.fromStringOrNull(it) }
  }
}
