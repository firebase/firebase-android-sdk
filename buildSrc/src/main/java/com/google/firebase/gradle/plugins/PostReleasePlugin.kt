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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Facilitates the creation of Post Release Tasks.
 *
 * TODO() - Add Additional information. Will probably do such whenever we get closer to completion.
 */
class PostReleasePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    registerVersionBumpTask(project)
  }

  /**
   * Registers the `versionBump` task for the provided [Project].
   *
   * Each SDK has a `gradle.properties` file at its root. This file has a `version` variable, that
   * is set to the current version of said module. At the end of a release, we bump the versions at
   * master of SDKs that went out that release to be one patch higher than the currently released
   * version. We do this to differentiate between the current project, and the latest released
   * version. If we didn’t bump the version, gradle could end up using a cached version of the
   * released artifact instead of your local copy during development. As such, the version in every
   * SDK at master is marked as one patch higher than the actual latest released version.
   *
   * @see [VersionBumpTask]
   *
   * @param project the [Project] to register this task to
   */
  fun registerVersionBumpTask(project: Project) =
    project.tasks.register<VersionBumpTask>("versionBump")
}
