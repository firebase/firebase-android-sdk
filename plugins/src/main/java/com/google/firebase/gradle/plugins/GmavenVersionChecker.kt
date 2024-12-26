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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class GmavenVersionChecker : DefaultTask() {

  @get:Input abstract val groupId: Property<String>

  @get:Input abstract val artifactId: Property<String>

  @get:Input abstract val latestReleasedVersion: Property<String>

  @get:Input abstract val version: Property<String>

  @TaskAction
  fun run() {
    val mavenHelper = GmavenHelper(groupId.get(), artifactId.get())
    val latestMavenVersion = mavenHelper.getLatestReleasedVersion()
    val info =
      "\n  latestReleasedVersion in gradle.properties should match the latest release on GMaven (${latestMavenVersion})" +
        "\n  version in gradle.properties should be a version bump above this, following SemVer, and should not be released on GMaven"
    // Either the Maven metadata does not exist, or the library hasn't been released
    if (latestMavenVersion.isEmpty()) {
      return
    }
    // TODO(b/285892320): Remove condition when bug fixed
    if (artifactId.get() == "protolite-well-known-types") {
      return
    }
    if (version.get() == latestReleasedVersion.get()) {
      throw GradleException(
        "version and latestReleasedVersion from gradle.properties are the same (${version.get()})" +
          info
      )
    }
    if (latestMavenVersion == version.get()) {
      throw GradleException(
        "version from gradle.properties (${version.get()}) is already the latest release on GMaven" +
          info
      )
    } else if (mavenHelper.hasReleasedVersion(version.get())) {
      throw GradleException(
        "version from gradle.properties (${version.get()}) has already been released on GMaven" +
          info
      )
    }
    if (latestMavenVersion != latestReleasedVersion.get()) {
      if (mavenHelper.hasReleasedVersion(latestReleasedVersion.get())) {
        throw GradleException(
          "latestReleasedVersion from gradle.properties (${latestReleasedVersion.get()}) has been released but is not the latest release on GMaven (${latestMavenVersion})"
        )
      } else {
        throw GradleException(
          "latestReleasedVersion from gradle.properties (${latestReleasedVersion.get()}) has not been released on GMaven" +
            info
        )
      }
    }
  }
}
