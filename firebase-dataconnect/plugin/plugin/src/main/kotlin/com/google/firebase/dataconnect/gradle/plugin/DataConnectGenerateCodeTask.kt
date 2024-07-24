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
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class DataConnectGenerateCodeTask : DefaultTask() {

  @get:Inject abstract val execOperations: ExecOperations

  @get:Inject abstract val fileSystemOperations: FileSystemOperations

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:OutputDirectory abstract val mergedInputsDirectory: DirectoryProperty

  @get:InputFiles abstract val defaultConfigDirectories: ListProperty<Directory>

  @get:InputDirectory abstract val customConfigDirectory: DirectoryProperty

  @get:InputFile abstract val dataConnectCliExecutable: RegularFileProperty

  @get:Input abstract val connectors: ListProperty<String>

  @TaskAction
  fun run() {
    val outputDirectory: File = outputDirectory.get().asFile
    val mergedInputsDirectory: File = mergedInputsDirectory.get().asFile
    val defaultConfigDirectories: List<File> = defaultConfigDirectories.get().map { it.asFile }
    val customConfigDirectory: File? = customConfigDirectory.orNull?.asFile
    val dataConnectCliExecutable: File = dataConnectCliExecutable.get().asFile
    val connectors: List<String> = connectors.get()

    logger.info("outputDirectory={}", outputDirectory)
    logger.info("mergedInputsDirectory={}", mergedInputsDirectory)
    logger.info("defaultConfigDirectories={}", defaultConfigDirectories)
    logger.info("customConfigDirectory={}", customConfigDirectory)
    logger.info("dataConnectCliExecutable={}", dataConnectCliExecutable)
    logger.info("connectors={}", connectors)

    deleteDirectories(mergedInputsDirectory, outputDirectory)
    val configDirectory =
      mergeConfigDirectories(defaultConfigDirectories, customConfigDirectory, mergedInputsDirectory)

    if (configDirectory === null) {
      logger.info("No non-empty config directories found; nothing to do")
      return
    }

    generateCode(dataConnectCliExecutable, configDirectory, outputDirectory, connectors)
    logger.info("completed successfully")
  }

  private fun deleteDirectories(vararg directory: File) {
    val directories = directory.toList()
    logger.info("Deleting directories: {}", directories)
    fileSystemOperations.delete { it.delete(directories) }
  }

  private fun mergeConfigDirectories(
    defaultConfigDirectories: List<File>,
    customConfigDirectory: File?,
    outputDirectory: File
  ): File? {
    if (customConfigDirectory !== null) {
      if (!customConfigDirectory.exists()) {
        throw DataConnectInputDirectoryNotFoundException(
          "custom config directory not found: $customConfigDirectory"
        )
      }
      logger.info("Using custom config directory: {}", customConfigDirectory)
      return customConfigDirectory
    }

    defaultConfigDirectories
      .filter { it.exists() }
      .let { existingConfigDirectories ->
        if (existingConfigDirectories.size == 1) {
          val singleConfigDirectory = existingConfigDirectories.single()
          logger.info("Using single config directory: {}", singleConfigDirectory)
          return singleConfigDirectory
        }
      }

    logger.info("Merging config directories {} to {}", defaultConfigDirectories, outputDirectory)
    val workResult =
      fileSystemOperations.copy {
        it.from(defaultConfigDirectories)
        it.into(outputDirectory)
        it.duplicatesStrategy = DuplicatesStrategy.FAIL
      }

    return if (workResult.didWork) outputDirectory else null
  }

  private fun generateCode(
    dataConnectCliExecutable: File,
    configDirectory: File,
    outputDirectory: File,
    connectors: List<String>
  ) {
    execOperations.exec { execSpec ->
      execSpec.run {
        executable(dataConnectCliExecutable)
        setIgnoreExitValue(false)

        if (logger.isInfoEnabled) {
          args("-logtostderr")
        }
        if (logger.isDebugEnabled) {
          args("-v").args("2")
        }

        args("gradle").args("generate")

        args("-config_dir=$configDirectory")
        args("-output_dir=${outputDirectory.path}")
        if (connectors.isNotEmpty()) {
          args("-connectors=${connectors.joinToString(",")}")
        }
      }
    }
  }

  class DataConnectInputDirectoryNotFoundException(message: String) : GradleException(message)
}
