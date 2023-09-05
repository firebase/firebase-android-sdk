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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType

fun computeLibraryGroups(project: Project): Map<String, List<FirebaseLibraryExtension>> {
  if (project != project.rootProject) {
    throw GradleException(
      "Error trying to generate library groups from non root project." +
        "Computing library groups for non root projects can generate incomplete views."
    )
  }
  val libraryGroups =
    project.rootProject.allprojects
      .mapNotNull { it.extensions.findByType<FirebaseLibraryExtension>() }
      .groupBy { it.libraryGroupName }
  for (libraryGroup in libraryGroups.values) {
    val maxVersion =
      libraryGroup
        .mapNotNull { ModuleVersion.fromStringOrNull(it.project.version.toString()) }
        .maxOrNull()
        ?: continue
    for (firebaseExtension in libraryGroup) {
      if (ModuleVersion.fromStringOrNull(firebaseExtension.project.version.toString()) == null) {
        firebaseExtension.project.version = maxVersion.toString()
      }
    }
  }
  return libraryGroups
}

fun computeMissingLibrariesToRelease(
  librariesToRelease: List<FirebaseLibraryExtension>,
  libraryGroups: Map<String, List<FirebaseLibraryExtension>>
): List<FirebaseLibraryExtension> =
  expandWithLibraryGroup(librariesToRelease, libraryGroups) - librariesToRelease

fun expandWithLibraryGroup(
  libraries: List<FirebaseLibraryExtension>,
  libraryGroups: Map<String, List<FirebaseLibraryExtension>>
) =
  libraries
    .flatMap { libraryGroups.getOrDefault(it.libraryGroupName, emptyList()) }
    .distinctBy { it.artifactId.get() }
