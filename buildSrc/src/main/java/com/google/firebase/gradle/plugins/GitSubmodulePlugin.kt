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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Exposes configuration for [GitSubmodulePlugin].
 *
 * @param submodules the parent directory of the SDK's Git Submodules. Defaults to `src/third_party`
 */
interface GitSubmodulePluginExtension {
  val submodules: Property<File>
}

/**
 * Helper plugin for common actions regarding Git Submodules
 *
 * At the time of writing this, we only have one SDK with submodules. Although, that could grow in
 * the future. More importantly though, this provides a way for us to make sure the submodules are
 * initilized whenever we are building said SDKs- while keeping our system clean and modular.
 *
 * This plugin is automatically applied to all SDKs that utilize [FirebaseLibraryPlugin], and is
 * subsequently bound to the `preBuild` task that is apart of all gradle modules.
 *
 * The following tasks are registered when this plugin is applied:
 * - [initializeGitSubmodules][registerInitializeGitSubmodulesTask]
 * - [updateGitSubmodules][registerUpdateGitSubmodulesTask]
 * - [removeGitSubmodules][registerRemoveGitSubmodulesTask]
 *
 * __Documentation explaining each task is provided in the annotation for each task__
 *
 * @see [GitSubmodulePluginExtension]
 */
abstract class GitSubmodulePlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(configureExtension(project)) {
      registerInitializeGitSubmodulesTask(project, submodules.get())
      registerUpdateGitSubmodulesTask(project, submodules.get())
      registerRemoveGitSubmodulesTask(project, submodules.get())
    }
  }

  private fun configureExtension(project: Project) =
    project.extensions.create<GitSubmodulePluginExtension>("GitSubmodule").apply {
      submodules.convention(project.file("src/third_party"))
    }

  /**
   * Registers the initializeGitSubmodules Task for the provided [Project].
   *
   * Creates a local configuration for the predefined submodules. It does this by running the
   * command `git submodule init` from the [project]'s root directory.
   *
   * If there aren't any submodules to initialize, this task is skipped- saving resources.
   *
   * @param project the [Project] to register this task to
   * @param submodules the root directory of where the submodules live
   */
  private fun registerInitializeGitSubmodulesTask(project: Project, submodules: File) =
    project.tasks.register<Exec>("initializeGitSubmodules") {
      onlyIf { hasEmptySubmodules(submodules) }

      workingDir = project.projectDir
      commandLine = "git submodule init".split(" ")
    }

  /**
   * Registers the updateGitSubmodules Task for the provided [Project].
   *
   * Pulls the latest data for each submodule, similiar to `git pull`. It does this by running the
   * command `git submodule update` from the [project]'s root directory.
   *
   * If there aren't any submodules, this task is skipped- saving resources.
   *
   * @param project the [Project] to register this task to
   * @param submodules the root directory of where the submodules live
   */
  private fun registerUpdateGitSubmodulesTask(project: Project, submodules: File) =
    project.tasks.register<Exec>("updateGitSubmodules") {
      onlyIf { hasEmptySubmodules(submodules) }
      dependsOn("initializeGitSubmodules")

      workingDir = project.projectDir
      commandLine = "git submodule update".split(" ")
    }

  /**
   * Registers the removeGitSubmodules Task for the provided [Project].
   *
   * Removes and de initilizes all submodules for the given [project]. It does this by running the
   * command `git submodule deinit --all` from the [project]'s root directory.
   *
   * If there aren't any submodules to remove, this task is skipped- saving resources.
   *
   * @param project the [Project] to register this task to
   * @param submodules the root directory of where the submodules live
   */
  private fun registerRemoveGitSubmodulesTask(project: Project, submodules: File) =
    project.tasks.register<Exec>("removeGitSubmodules") {
      onlyIf { submodules.exists() }

      workingDir = project.projectDir
      commandLine = "git submodule deinit --all".split(" ")
    }

  private fun hasEmptySubmodules(parentFolder: File) =
    parentFolder.listFilesOrEmpty().any { it.isDirectory && it.listFilesOrEmpty().isEmpty() }
}
