/*
 * Copyright 2022 Google LLC
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

package com.google.firebase.gradle.plugins.ci

import com.google.firebase.gradle.plugins.firebaseLibraryOrNull
import com.google.gson.Gson
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

abstract class ChangedModulesTask : DefaultTask() {
  @get:Input
  @set:Option(option = "changed-git-paths", description = "The list of changed paths")
  abstract var changedGitPaths: List<String>

  @get:Input
  @set:Option(option = "output-file-path", description = "The file to output json to")
  abstract var outputFilePath: String

  @get:Input
  @set:Option(option = "only-firebase-sdks", description = "Only list Firebase SDKs")
  abstract var onlyFirebaseSDKs: Boolean

  @get:OutputFile val outputFile by lazy { File(outputFilePath) }

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun execute() {
    val changedProjects = findChangedProjects()

    val affectedProjects = findProjectsThatDependOnThese(changedProjects)

    val outputProjects = (changedProjects + affectedProjects).toSet()

    val projectPaths = outputProjects.map { it.path }

    println(projectPaths.joinToString("\n"))

    outputFile.writeText(Gson().toJson(projectPaths))
  }

  private fun findChangedProjects(): List<Project> {
    val projectFinder = AffectedProjectFinder(project, changedGitPaths.toSet(), emptyList())
    val allChangedProjects = projectFinder.find()

    return allChangedProjects.filter { it.matchesOurFilter() }
  }

  private fun findProjectsThatDependOnThese(projects: List<Project>): List<Project> {
    val dependentProjects =
      project.rootProject.subprojects.filter {
        it.configurations.any {
          it.dependencies.filterIsInstance<ProjectDependency>().any {
            projects.contains(it.dependencyProject)
          }
        }
      }

    return dependentProjects.filter { it.matchesOurFilter() }
  }

  private fun Project.matchesOurFilter(): Boolean {
    if (EXCLUDED_PROJECTS.contains(project.parent?.name)) return false
    if (onlyFirebaseSDKs && firebaseLibraryOrNull == null) return false

    return true
  }

  companion object {
    val EXCLUDED_PROJECTS = listOf("protolite-well-known-types")
  }
}
