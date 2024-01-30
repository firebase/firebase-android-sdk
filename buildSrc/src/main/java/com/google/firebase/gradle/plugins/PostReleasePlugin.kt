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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Facilitates the creation of Post Release Tasks.
 *
 * At the end of a release, we need to update the state of the master branch according to various
 * "cleanup" tasks. These tasks are defined in this plugin, and attached to releasing SDKs.
 *
 * The primary parent task that this plugin creates is `postReleaseCleanup`- which runs all the
 * clean up tasks in one go.
 *
 * *Note that this task should be ran on the release branch- or more appropriately the merge-back
 * branch*
 *
 * @see registerVersionBumpTask
 * @see registerMoveUnreleasedChangesTask
 * @see registerUpdatePinnedDependenciesTask
 */
class PostReleasePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val versionBump = registerVersionBumpTask(project)
    val moveUnreleasedChanges = registerMoveUnreleasedChangesTask(project)
    val updatePinnedDependencies = registerUpdatePinnedDependenciesTask(project)

    project.tasks.register("postReleaseCleanup") {
      dependsOn(versionBump, moveUnreleasedChanges, updatePinnedDependencies)
    }
  }

  /**
   * Registers the `versionBump` task for the provided [Project].
   *
   * Each SDK has a `gradle.properties` file at its root. This file has a `version` variable, that
   * is set to the current version of said module. After a release, this `version` should be bumped
   * up to differentiate between code at HEAD, and the latest released version.
   *
   * Furthermore, this file may optionally contain a `latestReleasedVersion` variable (if the SDK
   * has released). If this property is present, it should be updated to the related version that
   * went out during the release.
   *
   * @see VersionBumpTask
   *
   * @param project the [Project] to register this task to
   */
  fun registerVersionBumpTask(project: Project) =
    project.tasks.register<VersionBumpTask>("versionBump")

  /**
   * Registers the `moveUnreleasedChanges` task for the provided [Project].
   *
   * Each SDK has `CHANGELOG.md` file at its root. This file has an `Unreleased` section with
   * changes that go out in the next release. After a release, this `Unreleased` section should be
   * moved into a seperate section that specifies the version it went out with, and the `Unreleased`
   * section should be wiped clear for new changes to come; for the next release.
   *
   * @see MoveUnreleasedChangesTask
   *
   * @param project the [Project] to register this task to
   */
  fun registerMoveUnreleasedChangesTask(project: Project) =
    project.tasks.register<MoveUnreleasedChangesTask>("moveUnreleasedChanges") {
      onlyIf("CHANGELOG.md file must be present") { project.file("CHANGELOG.md").exists() }
    }

  /**
   * Registers the `updatePinnedDependencies` for the provided [Project]
   *
   * If a given SDK needs to use unreleased features from a dependent SDK they change their pinned
   * dependency to a project level dependency, until the features are released. After a release, we
   * need to convert these project level dependencies back to pinned dependencies- with the latest
   * released version attached.
   *
   * @see UpdatePinnedDependenciesTask
   *
   * @param project the [Project] to register this task to
   */
  fun registerUpdatePinnedDependenciesTask(project: Project) =
    project.tasks.register<UpdatePinnedDependenciesTask>("updatePinnedDependencies") {
      buildFile.set(project.buildFile)
      outputFile.set(project.buildFile)
    }
}
