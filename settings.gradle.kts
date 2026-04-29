/*
 * Copyright 2024 Google LLC
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

import java.io.File
import org.gradle.api.initialization.ProjectDescriptor

pluginManagement {
  repositories {
    google()
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
  }

  includeBuild("./plugins")
  includeBuild("firebase-dataconnect/gradleplugin")
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenLocal()
    mavenCentral()
  }
}

fun readSubprojectsFile(
  subprojectsFileNames: Iterable<String>,
  subprojectsDirectory: Directory
): String {
  val subprojectsFileText =
    subprojectsFileNames.firstNotNullOfOrNull {
      val subprojectsRegularFile = subprojectsDirectory.file(it)
      val subprojectsFile = subprojectsRegularFile.asFile
      val fileTextProvider = providers.fileContents(subprojectsRegularFile).asText
      if (!fileTextProvider.isPresent) {
        logger.info("Skipping non-existent subprojects file: {}", subprojectsFile.absolutePath)
        null
      } else {
        logger.info("Loading subprojects file: {}", subprojectsFile.absolutePath)
        fileTextProvider.get()
      }
    }

  class SubprojectsFileNotFoundException(message: String) : Exception(message)
  return subprojectsFileText
    ?: throw SubprojectsFileNotFoundException(
      "No subprojects files found in ${subprojectsDirectory.asFile.absolutePath} " +
        "(looked for: ${subprojectsFileNames.joinToString()})"
    )
}

fun readSubprojectsFile(): String {
  val subprojectsFileNames = listOf("subprojects.local.cfg", "subprojects.cfg")
  val subprojectsDirectory = @Suppress("UnstableApiUsage") layout.settingsDirectory
  return readSubprojectsFile(subprojectsFileNames, subprojectsDirectory)
}

/**
 * Parses the input file and returns a list of subprojects.
 *
 * Expected file format:
 * - Empty lines are ignored.
 * - Text following a '#' character on the same line is treated as a comment.
 * - Other lines are treated as project paths.
 */
fun discoverSubprojects(subprojectsFileContents: String): List<String> {
  return subprojectsFileContents
    .lines()
    .mapNotNull { it.split("#").firstOrNull()?.trim() }
    .filter { it.isNotEmpty() }
}

fun discoverSubprojects(): List<String> {
  val subprojectsFileText = readSubprojectsFile()
  return discoverSubprojects(subprojectsFileText)
}

/**
 * Recursively links the build scripts for each sub-project.
 *
 * Gradle expects build scripts to be named `build`, but we name all of our build scripts after the
 * project (eg; `firebase-common.gradle.kts`). While this makes it easier to quickly access the
 * build scripts of certain projects, it also prevents gradle from being able to identify the build
 * scripts.
 *
 * To fix this, we internally tell gradle the actual name of the build file, so that it can properly
 * find it.
 */
fun setBuildScripts(project: ProjectDescriptor) {
  val names =
    listOf(
      "${project.name}.gradle.kts",
      "${project.name}.gradle",
      "build.gradle.kts",
      "build.gradle",
    )

  val name = names.find { File(project.projectDir, it).exists() }

  if (name !== null) {
    project.buildFileName = name
  } else {
    logger.debug(
      """
      Couldn't find a build script for project: "${project.name}".
      Assuming this is either a container or marker project.
    """
        .trimIndent()
    )
  }

  for (child in project.children) {
    setBuildScripts(child)
  }
}

// Add subprojects to `subprojects.cfg` instead of modifying `settings.gradle.kts` directly.
//
// If `subprojects.local.cfg` exists, it overrides `subprojects.cfg`. This is useful for local
// development to load only a subset of submodules, which reduces build times and improves
// IDE performance by reducing the number of files to index.
discoverSubprojects().forEach { include(":$it") }

setBuildScripts(rootProject)

rootProject.name = "com.google.firebase"
