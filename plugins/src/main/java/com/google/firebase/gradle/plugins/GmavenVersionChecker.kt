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

import com.google.firebase.gradle.plugins.services.GMavenService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Validate that the current `version` and `latestReleasedVersion` set in the library's
 * `gradle.properties` file are properly set.
 *
 * @property artifactId The library's group id (eg; `com.google.firebase`)
 * @property artifactId The library's artifact id (eg; `firebase-common`)
 * @property latestReleasedVersion The `latestReleasedVersion` set in `gradle.properties`
 * @property version The `version` set in `gradle.properties`
 */
abstract class GmavenVersionChecker : DefaultTask() {
  @get:Input abstract val groupId: Property<String>

  @get:Input abstract val artifactId: Property<String>

  @get:Input @get:Optional abstract val latestReleasedVersion: Property<String>

  @get:Input abstract val version: Property<String>

  @get:ServiceReference("gmaven") abstract val gmaven: Property<GMavenService>

  @TaskAction
  fun run() {
    val version = version.get()
    val gmaven = gmaven.get()
    val groupId = groupId.get()
    val artifactId = artifactId.get()
    val latestReleasedVersion = latestReleasedVersion.getOrNull()

    val latestMavenVersion =
      gmaven.latestVersionOrNull(groupId, artifactId)
        ?: skipGradleTask("Library hasn't been published")

    logger.info(
      """
      |Validating GMaven versions for:
      |  version: $version
      |  groupId: $groupId
      |  artifactId: $artifactId
      |  latestReleasedVersion: $latestReleasedVersion
      |  latestMavenVersion: $latestMavenVersion
      """
        .trimMargin()
    )

    fun throwError(reason: String): Nothing {
      throw GradleException(
        """
        |$reason
        |  latestReleasedVersion in gradle.properties should match the latest release on GMaven (${latestMavenVersion})
        |  version in gradle.properties should be a version bump above this, following SemVer, and should not be released on GMaven
        """
          .trimMargin()
      )
    }

    // TODO(b/285892320): Remove condition when bug fixed
    if (artifactId == "protolite-well-known-types") {
      skipGradleTask("Not able to validate this library currently- due to an open bug")
    }

    if (version == latestReleasedVersion) {
      throwError("version and latestReleasedVersion from gradle.properties are the same ($version)")
    }

    if (latestMavenVersion == version) {
      throwError(
        "version from gradle.properties ($version) is already the latest release on GMaven"
      )
    }

    if (gmaven.hasReleasedVersion(groupId, artifactId, version)) {
      throwError("version from gradle.properties ($version) has already been released on GMaven")
    }

    if (latestMavenVersion != latestReleasedVersion) {
      if (latestReleasedVersion === null) {
        throwError("latestReleasedVersion from gradle.properties has not been set yet")
      }

      if (gmaven.hasReleasedVersion(groupId, artifactId, latestReleasedVersion)) {
        throwError(
          "latestReleasedVersion from gradle.properties ($latestReleasedVersion) has been released but is not the latest release on GMaven ($latestMavenVersion)"
        )
      }

      throwError(
        "latestReleasedVersion from gradle.properties ($latestReleasedVersion) has not been released on GMaven"
      )
    }
  }
}
