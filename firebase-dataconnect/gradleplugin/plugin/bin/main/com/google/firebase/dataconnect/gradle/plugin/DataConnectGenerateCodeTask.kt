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
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class DataConnectGenerateCodeTask : DefaultTask() {

  @get:InputFile abstract val dataConnectExecutable: RegularFileProperty

  @get:Optional @get:InputFiles abstract val configDirectory: DirectoryProperty

  @get:Input abstract val connectors: Property<Collection<String>>

  @get:Internal abstract val buildDirectory: DirectoryProperty

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:Optional @get:InputFile abstract val ktfmtJarFile: RegularFileProperty

  @TaskAction
  fun run() {
    val dataConnectExecutable: File = dataConnectExecutable.get().asFile
    val configDirectory: File? = configDirectory.orNull?.asFile
    val connectors: Collection<String> = connectors.get().distinct().sorted()
    val buildDirectory: File = buildDirectory.get().asFile
    val outputDirectory: File = outputDirectory.get().asFile
    val ktfmtJarFile: File? = ktfmtJarFile.orNull?.asFile

    logger.info("dataConnectExecutable={}", dataConnectExecutable.absolutePath)
    logger.info("configDirectory={}", configDirectory?.absolutePath)
    logger.info("connectors={}", connectors.joinToString(", "))
    logger.info("buildDirectory={}", buildDirectory.absolutePath)
    logger.info("outputDirectory={}", outputDirectory.absolutePath)
    logger.info("ktfmtJarFile={}", ktfmtJarFile?.absolutePath)

    if (outputDirectory.exists()) {
      logger.info("Deleting directory: $outputDirectory")
      project.delete(outputDirectory)
    }

    if (configDirectory === null) {
      logger.info("No Data Connect config directories found; nothing to do")
      return
    }

    runDataConnectExecutable(
      dataConnectExecutable = dataConnectExecutable,
      subCommand = listOf("gradle", "generate"),
      configDirectory = configDirectory,
    ) {
      this.connectors = connectors
      this.outputDirectory = outputDirectory
      this.logFile = File(buildDirectory, "codegen.log.txt")
    }

    if (ktfmtJarFile !== null) {
      logger.info("Running ktfmt on generated code")
      runKtfmt(
        ktfmtJarFile = ktfmtJarFile,
        directory = outputDirectory,
        logFile = File(buildDirectory, "ktfmt.log.txt")
      )
    }

    logger.info("Completed successfully")
  }
}

private fun Task.runKtfmt(
  ktfmtJarFile: File,
  directory: File,
  logFile: File,
) {
  project.mkdir(logFile.parentFile)
  val logFileStream = logFile.outputStream()

  try {
    project.javaexec { execSpec ->
      execSpec.run {
        classpath(ktfmtJarFile)
        mainClass.set("com.facebook.ktfmt.cli.Main")
        args("--google-style")
        args(directory.absolutePath)
        isIgnoreExitValue = false
        standardOutput = logFileStream
        errorOutput = logFileStream
      }
    }
  } catch (e: Exception) {
    logFileStream.close()
    logFile.forEachLine { logger.error(it.trimEnd()) }
    throw e
  } finally {
    logFileStream.close()
    if (logger.isInfoEnabled) {
      logFile.forEachLine { logger.error(it.trimEnd()) }
    }
  }
}
