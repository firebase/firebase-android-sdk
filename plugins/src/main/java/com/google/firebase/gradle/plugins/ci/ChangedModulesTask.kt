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

import com.google.firebase.gradle.plugins.FirebaseLibraryExtension
import com.google.gson.Gson
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.findByType

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
    val projects =
      AffectedProjectFinder(project, changedGitPaths.toSet(), listOf())
        .find()
        .filter {
          val ext = it.extensions.findByType(FirebaseLibraryExtension::class.java)
          !onlyFirebaseSDKs || it.extensions.findByType<FirebaseLibraryExtension>() != null
        }
        .map { it.path }
        .toSet()

    val result = project.rootProject.subprojects.associate { it.path to mutableSetOf<String>() }
    project.rootProject.subprojects.forEach { p ->
      p.configurations.forEach { c ->
        c.dependencies.filterIsInstance<ProjectDependency>().forEach {
          if (
            !onlyFirebaseSDKs ||
              it.dependencyProject.extensions.findByType<FirebaseLibraryExtension>() != null
          ) {
            if (!onlyFirebaseSDKs || p.extensions.findByType<FirebaseLibraryExtension>() != null) {
              result[it.dependencyProject.path]?.add(p.path)
            }
          }
        }
      }
    }
    val affectedProjects =
      result
        .flatMap { (key, value) -> if (projects.contains(key)) setOf(key) + value else setOf() }
        .toSet()

    outputFile.writeText(Gson().toJson(affectedProjects))
  }
}
