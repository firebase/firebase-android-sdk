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

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Returns a map of library group names to the list of libraries that belong to that group.
 *
 * Libraries, aka projects with `FirebaseLibraryExtension`, always belong to a library group. See
 * [FirebaseLibraryExtension] to know more about library group names.
 */
fun computeLibraryGroups(project: Project): Map<String, List<FirebaseLibraryExtension>> {
  if (project != project.rootProject) {
    throw GradleException(
      "Error trying to generate library groups from non root project. " +
        "Computing library groups for non root projects can generate incomplete views."
    )
  }
  val libraryGroups =
    project.subprojects.mapNotNull { it.firebaseLibraryOrNull }.groupBy { it.libraryGroupName }

  return libraryGroups
}

fun fixLibraryGroupVersions(libraryGroups: Map<String, List<FirebaseLibraryExtension>>) {
  for ((name, libraryGroup) in libraryGroups) {
    val maxVersion =
      libraryGroup.mapNotNull { it.moduleVersion }.maxOrNull()?.toString() ?: continue
    for (firebaseExtension in libraryGroup) {
      if (ModuleVersion.fromStringOrNull(firebaseExtension.project.version.toString()) == null) {
        firebaseExtension.project.version = maxVersion.toString()
      }
    }
  }
}

/**
 * Returns the list of libraries that should be transitively included in a release but are not.
 *
 * Based on [librariesToRelease], this function will find and return all libraries that belongs to
 * the same library group of a releasing library and are not included.
 */
fun computeMissingLibrariesToRelease(
  librariesToRelease: List<FirebaseLibraryExtension>,
  libraryGroups: Map<String, List<FirebaseLibraryExtension>>
): List<FirebaseLibraryExtension> =
  expandWithLibraryGroup(librariesToRelease, libraryGroups) - librariesToRelease

/** Returns a list that includes [libraries] and all their library group members. */
fun expandWithLibraryGroup(
  libraries: List<FirebaseLibraryExtension>,
  libraryGroups: Map<String, List<FirebaseLibraryExtension>>
) =
  libraries
    .flatMap { libraryGroups.getOrDefault(it.libraryGroupName, emptyList()) }
    .distinctBy { it.artifactId.get() }

val FirebaseLibraryExtension.moduleVersion: ModuleVersion?
  get() = ModuleVersion.fromStringOrNull(version)
