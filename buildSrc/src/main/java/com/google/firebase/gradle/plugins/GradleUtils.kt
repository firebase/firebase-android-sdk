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

import com.google.firebase.gradle.plugins.ci.Coverage
import java.io.File
import java.nio.file.Paths
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

fun Copy.fromDirectory(directory: Provider<File>) =
  from(directory) { into(directory.map { it.name }) }

/**
 * Creates a file at the buildDir for the given [Project].
 *
 * Syntax sugar for:
 * ```
 * project.file("${project.buildDir}/$path)
 * ```
 */
fun Project.fileFromBuildDir(path: String) = file("$buildDir/$path")

/**
 * Maps a file provider to another file provider as a sub directory.
 *
 * Syntax sugar for:
 * ```
 * fileProvider.map { project.file("${it.path}/$path") }
 * ```
 */
fun Project.childFile(provider: Provider<File>, childPath: String) =
  provider.map { file("${it.path}/$childPath") }

/**
 * Returns a list of children files, or an empty list if this [File] doesn't exist or doesn't have
 * any children.
 *
 * Syntax sugar for:
 *
 * ```
 * listFiles().orEmpty()
 * ```
 */
fun File.listFilesOrEmpty() = listFiles().orEmpty()

fun kotlinModuleName(project: Project): String {
  val fullyQualifiedProjectPath = project.path.replace(":".toRegex(), "-")
  return project.rootProject.name + fullyQualifiedProjectPath
}

fun setupStaticAnalysis(project: Project, library: FirebaseLibraryExtension) {
  project.afterEvaluate {
    configurations.all {
      if ("lintChecks" == name) {
        for (checkProject in library.staticAnalysis.androidLintCheckProjects) {
          project.dependencies.add("lintChecks", project.project(checkProject!!))
        }
      }
    }
  }
  project.tasks.register("firebaseLint") { dependsOn("lint") }
  Coverage.apply(library)
}

fun getApiInfo(project: Project, srcDirs: Set<File>): TaskProvider<ApiInformationTask> {
  val outputFile =
    project.rootProject.file(
      Paths.get(
        project.rootProject.buildDir.path,
        "apiinfo",
        project.path.substring(1).replace(":", "_")
      )
    )
  val outputApiFile = File(outputFile.absolutePath + "_api.txt")
  val apiTxt =
    if (project.file("api.txt").exists()) project.file("api.txt")
    else project.file(project.rootDir.toString() + "/empty-api.txt")
  val apiInfo =
    project.tasks.register<ApiInformationTask>("apiInformation") {
      sources.value(project.provider { srcDirs })
      apiTxtFile.set(apiTxt)
      baselineFile.set(project.file("baseline.txt"))
      this.outputFile.set(outputFile)
      this.outputApiFile.set(outputApiFile)
      updateBaseline.set(project.hasProperty("updateBaseline"))
    }
  return apiInfo
}

fun getGenerateApiTxt(project: Project, srcDirs: Set<File>) =
  project.tasks.register<GenerateApiTxtTask>("generateApiTxtFile") {
    sources.value(project.provider { srcDirs })
    apiTxtFile.set(project.file("api.txt"))
    baselineFile.set(project.file("baseline.txt"))
    updateBaseline.set(project.hasProperty("updateBaseline"))
  }

fun getDocStubs(project: Project, srcDirs: Set<File>) =
  project.tasks.register<GenerateStubsTask>("docStubs") {
    sources.value(project.provider { srcDirs })
  }
