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
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

abstract class DataConnectGenerateCodeTask : DefaultTask() {

  @get:InputFile abstract val dataConnectExecutable: RegularFileProperty

  @get:Optional @get:InputFiles abstract val configDirectory: DirectoryProperty

  @get:Input abstract val connectors: Property<Collection<String>>

  @get:OutputFiles abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun run() {
    val dataConnectExecutable: File = dataConnectExecutable.get().asFile
    val configDirectory: File? = configDirectory.orNull?.asFile
    val connectors: Collection<String> = connectors.get().distinct().sorted()
    val outputDirectory: File = outputDirectory.get().asFile

    logger.info("dataConnectExecutable={}", dataConnectExecutable.absolutePath)
    logger.info("configDirectory={}", configDirectory?.absolutePath)
    logger.info("connectors={}", connectors.joinToString(", "))
    logger.info("outputDirectory={}", outputDirectory.absolutePath)

    if (outputDirectory.exists()) {
      logger.info("Deleting directory: $outputDirectory")
      project.delete(outputDirectory)
    }

    if (configDirectory === null) {
      logger.info("No Data Connect config directories found; nothing to do")
      return
    }

    project.exec { execSpec ->
      execSpec.run {
        executable(dataConnectExecutable)
        isIgnoreExitValue = false

        if (logger.isDebugEnabled) {
          args("-v").args("9")
          args("-logtostderr")
        } else if (logger.isInfoEnabled) {
          args("-v").args("2")
          args("-logtostderr")
        }

        args("gradle").args("generate")

        args("-config_dir=$configDirectory")
        args("-output_dir=${outputDirectory.path}")
        if (connectors.isNotEmpty()) {
          args("-connectors=${connectors.joinToString(",")}")
        }
      }
    }

    logger.info("Completed successfully")
  }

  class DataConnectInputDirectoryNotFoundException(message: String) : GradleException(message)
}
