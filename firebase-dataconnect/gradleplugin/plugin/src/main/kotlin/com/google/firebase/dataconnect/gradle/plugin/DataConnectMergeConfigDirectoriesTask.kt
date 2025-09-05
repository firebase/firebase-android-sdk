/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.gradle.plugin

import java.io.File
import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class DataConnectMergeConfigDirectoriesTask : DefaultTask() {

  @get:InputFiles abstract val defaultConfigDirectories: ListProperty<Directory>

  @get:InputFiles @get:Optional abstract val customConfigDirectory: DirectoryProperty

  @get:Internal abstract val buildDirectory: DirectoryProperty

  @get:OutputDirectory @get:Optional abstract val mergedDirectory: DirectoryProperty

  @get:Inject abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun run() {
    val defaultConfigDirectories: List<File> =
      defaultConfigDirectories
        .get()
        .map { it.asFile }
        .sortedBy { it.absolutePath.lowercase(Locale.US) }
    val customConfigDirectory: File? = customConfigDirectory.orNull?.asFile
    val buildDirectory: File = buildDirectory.get().asFile
    val mergedDirectory: File? = mergedDirectory.orNull?.asFile

    logger.info(
      "defaultConfigDirectories ({}): {}",
      defaultConfigDirectories.size,
      defaultConfigDirectories.joinToString(", ") { it.absolutePath }
    )
    logger.info("customConfigDirectory: {}", customConfigDirectory?.absolutePath)
    logger.info("buildDirectory: {}", buildDirectory.absolutePath)
    logger.info("mergedDirectory: {}", mergedDirectory?.absolutePath)

    logger.info("Deleting build directory: {}", buildDirectory)
    buildDirectory.deleteRecursively()

    val configDirectories =
      buildList {
          addAll(defaultConfigDirectories)
          if (customConfigDirectory !== null) {
            add(customConfigDirectory)
            if (!customConfigDirectory.exists()) {
              throw DataConnectGradleException(
                "chhzf62bwt",
                "custom data connect config directory not found: " +
                  customConfigDirectory.absolutePath
              )
            }
          }
        }
        .sortedBy { it.absolutePath.lowercase(Locale.US) }

    val existingConfigDirectories = configDirectories.filter { it.exists() }

    if (mergedDirectory === null) {
      if (existingConfigDirectories.size > 1) {
        throw DataConnectGradleException(
          "rft8texx22",
          "'mergedDirectory' is null but existingConfigDirectories has more than one directory:" +
            " (${existingConfigDirectories.size} directories) " +
            existingConfigDirectories.joinToString(", ")
        )
      }
      // nothing to do, since the one-and-only existing config directory will be used directly.
      return
    } else if (existingConfigDirectories.isEmpty()) {
      // nothing to do, since there are no existing config directories.
      return
    } else if (mergedDirectory != buildDirectory) {
      throw DataConnectGradleException(
        "qay4ngz5fr",
        "mergedDirectory must equal buildDirectory" +
          " when there are more than one existing config directories;" +
          " however, they were unequal and there were ${existingConfigDirectories.size}" +
          " existing config directories: " +
          existingConfigDirectories.joinToString(", ") { it.absolutePath } +
          " (mergedDirectory=$mergedDirectory buildDirectory=$buildDirectory)"
      )
    }

    logger.info(
      "Merging config directories {} to {}",
      existingConfigDirectories.joinToString(", ") { it.absolutePath },
      mergedDirectory.absolutePath
    )
    fileSystemOperations.copy {
      it.from(existingConfigDirectories)
      it.into(mergedDirectory)
      it.duplicatesStrategy = DuplicatesStrategy.FAIL
    }
  }
}
