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
import java.util.regex.Pattern
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class DataConnectExecutableDownloadTask : DefaultTask() {

  @get:InputFile @get:Optional abstract val inputFile: RegularFileProperty

  @get:Input @get:Optional abstract val version: Property<String>

  @get:Input
  @get:Optional
  abstract val verificationInfo: Property<DataConnectExecutable.VerificationInfo>

  @get:Internal abstract val buildDirectory: DirectoryProperty

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun run() {
    val inputFile: File? = inputFile.orNull?.asFile
    val version: String? = version.orNull
    val verificationInfo: DataConnectExecutable.VerificationInfo? = verificationInfo.orNull
    val buildDirectory: File = buildDirectory.get().asFile
    val outputFile: File = outputFile.get().asFile

    logger.info("inputFile: {}", inputFile)
    logger.info("version: {}", version)
    logger.info("verificationInfo: {}", verificationInfo)
    logger.info("buildDirectory: {}", buildDirectory)
    logger.info("outputFile: {}", outputFile)

    logger.info("Deleting build directory: {}", buildDirectory)
    project.delete(buildDirectory)

    if (inputFile !== null && version !== null) {
      throw DataConnectGradleException(
        "5t7wvatbr7",
        "Both 'inputFile' and 'version' were specified," +
          " but exactly _one_ of them is required to be specified" +
          " (inputFile=$inputFile version=$version)"
      )
    } else if (inputFile !== null) {
      runWithFile(inputFile = inputFile, outputFile = outputFile)
    } else if (version !== null) {
      runWithVersion(version = version, outputFile = outputFile)
    } else {
      throw DataConnectGradleException(
        "chc94cq7vx",
        "Neither 'inputFile' nor 'version' were specified," +
          " but exactly _one_ of them is required to be specified"
      )
    }
  }

  private fun runWithFile(inputFile: File, outputFile: File) {
    if (inputFile == outputFile) {
      logger.info("inputFile == outputFile; nothing to copy ({})", inputFile)
      return
    }

    logger.info("Copying {} to {}", inputFile, outputFile)
    project.copy {
      it.from(inputFile)
      it.into(outputFile.parentFile)
      it.rename(Pattern.quote(inputFile.name), Pattern.quote(outputFile.name))
    }
  }

  private fun runWithVersion(version: String, outputFile: File) {
    TODO("not implemented yet (version=$version, outputFile=$outputFile)")
  }
}
