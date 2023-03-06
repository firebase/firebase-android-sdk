// Copyright 2022 Google LLC
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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class CheckHeadDependencies : DefaultTask() {
  @get:Input abstract val projectsToPublish: ListProperty<FirebaseLibraryExtension>

  @get:Input abstract val allFirebaseProjects: ListProperty<String>

  @TaskAction
  fun run() {
    val projectsReleasing: Set<String> = projectsToPublish.get().map { it.artifactId.get() }.toSet()
    val projectsToRelease =
      projectsToPublish
        .get()
        .flatMap {
          it.project.configurations
            .getByName(it.runtimeClasspath)
            .allDependencies
            .filter { it is ProjectDependency }
            .map { it.name }
            .toSet()
        }
        .intersect(allFirebaseProjects.get())
        .subtract(projectsReleasing)

    if (projectsToRelease.isNotEmpty()) {
      throw GradleException(
        "Following Sdks have to release as well. Please update the release config.\n${projectsToRelease.joinToString("\n")}"
      )
    }
  }
}
