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

import com.google.firebase.dataconnect.gradle.plugin.DataConnectGenerateCodeTask.CallingConvention
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.slf4j.Logger

abstract class DataConnectGenerateCodeTask : DefaultTask() {

  @get:InputFile abstract val dataConnectExecutable: RegularFileProperty

  @get:Input abstract val dataConnectExecutableCallingConvention: Property<CallingConvention>

  @get:Optional @get:InputFiles abstract val configDirectory: DirectoryProperty

  @get:Input abstract val connectors: Property<Collection<String>>

  @get:Internal abstract val buildDirectory: DirectoryProperty

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:Optional @get:InputFile abstract val ktfmtJarFile: RegularFileProperty

  @get:Inject abstract val execOperations: ExecOperations

  /**
   * The subcommand of the Data Connect executable to use to perform code generation.
   *
   * In August 2025 the subcommand was changed by cl/795582011 from "gradle generate" to "sdk
   * generate -platform=kotlin". The "gradle generate" command was last supported in version 2.11.0
   * of the Data Connect executable.
   */
  enum class CallingConvention {
    GRADLE,
    SDK_GENERATE,
  }

  @TaskAction
  fun run() {
    val dataConnectExecutable: File = dataConnectExecutable.get().asFile
    val dataConnectExecutableCallingConvention = dataConnectExecutableCallingConvention.get()
    val configDirectory: File? = configDirectory.orNull?.asFile
    val connectors: Collection<String> = connectors.get().distinct().sorted()
    val buildDirectory: File = buildDirectory.get().asFile
    val outputDirectory: File = outputDirectory.get().asFile
    val ktfmtJarFile: File? = ktfmtJarFile.orNull?.asFile

    logger.info("dataConnectExecutable={}", dataConnectExecutable.absolutePath)
    logger.info("dataConnectExecutableCallingConvention={}", dataConnectExecutableCallingConvention)
    logger.info("configDirectory={}", configDirectory?.absolutePath)
    logger.info("connectors={}", connectors.joinToString(", "))
    logger.info("buildDirectory={}", buildDirectory.absolutePath)
    logger.info("outputDirectory={}", outputDirectory.absolutePath)
    logger.info("ktfmtJarFile={}", ktfmtJarFile?.absolutePath)

    if (outputDirectory.exists()) {
      logger.info("Deleting directory: $outputDirectory")
      outputDirectory.deleteRecursively()
    }

    if (configDirectory === null) {
      logger.info("No Data Connect config directories found; nothing to do")
      return
    }

    val subCommand =
      when (dataConnectExecutableCallingConvention) {
        CallingConvention.GRADLE -> listOf("gradle", "generate")
        CallingConvention.SDK_GENERATE -> listOf("sdk", "generate")
      }

    runDataConnectExecutable(
      dataConnectExecutable = dataConnectExecutable,
      subCommand = subCommand,
      configDirectory = configDirectory,
      execOperations=execOperations,
    ) {
      when (dataConnectExecutableCallingConvention) {
        CallingConvention.GRADLE -> this.connectors = connectors
        CallingConvention.SDK_GENERATE -> this.connectorId = connectors
      }
      this.outputDirectory = outputDirectory
      this.logFile = File(buildDirectory, "codegen.log.txt")
      if (dataConnectExecutableCallingConvention == CallingConvention.SDK_GENERATE) {
        this.platform = "kotlin"
      }
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

private fun DataConnectGenerateCodeTask.runKtfmt(
  ktfmtJarFile: File,
  directory: File,
  logFile: File,
) {
  logFile.parentFile.mkdirs()
  val logFileStream = logFile.outputStream()

  try {
    execOperations.javaexec { execSpec ->
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

fun DataConnectGenerateCodeTask.detectedCallingConvention(
  dataConnectExecutable: RegularFileProperty = this.dataConnectExecutable,
  buildDirectory: DirectoryProperty = this.buildDirectory,
  execOperations: ExecOperations = this.execOperations,
  logger: Logger = this.logger
): Provider<CallingConvention> =
  dataConnectExecutable.map {
    determineCallingConvention(
      dataConnectExecutable = it.asFile,
      workDirectory = File(buildDirectory.get().asFile, "determineCallingConvention"),
      execOperations = execOperations,
      logger = logger,
    )
  }

private fun determineCallingConvention(
  dataConnectExecutable: File,
  workDirectory: File,
  execOperations: ExecOperations,
  logger: Logger,
): CallingConvention {
  logger.info(
    "Determining calling convention of Data Connect executable: {}",
    dataConnectExecutable.absolutePath
  )

  val callingConventionResults =
    CallingConvention.entries.map { callingConvention ->
      val logFile =
        File(workDirectory, "$callingConvention.log.txt").also { it.parentFile.mkdirs() }
      logger.info(
        "Testing {} for support of calling convention {} (log file: {})",
        dataConnectExecutable.absolutePath,
        callingConvention,
        logFile.absolutePath
      )

      val exitCode: Int =
        logFile.outputStream().use { logFileStream ->
          execOperations
            .exec { execSpec ->
              execSpec.run {
                executable(dataConnectExecutable)
                isIgnoreExitValue = true
                standardOutput = logFileStream
                errorOutput = logFileStream
                when (callingConvention) {
                  CallingConvention.GRADLE -> args("gradle", "help", "generate")
                  CallingConvention.SDK_GENERATE -> args("sdk", "help", "generate")
                }
              }
            }
            .exitValue
        }

      val callingConventionSupported = exitCode == 0
      logger.info(
        "Testing {} for support of calling convention {} completed: {} (exitCode={})",
        dataConnectExecutable.absolutePath,
        callingConvention,
        callingConventionSupported,
        exitCode
      )
      Pair(callingConvention, callingConventionSupported)
    }

  val supportedCallingConventions: List<CallingConvention> =
    callingConventionResults.filter { it.second }.map { it.first }
  return supportedCallingConventions.singleOrNull()
    ?: throw DataConnectGradleException(
      "d24j9dm3r6",
      "could not detect calling convention of Data Connect executable ${dataConnectExecutable.absolutePath}: " +
        "found ${supportedCallingConventions.size} supported calling conventions, but expected exactly 1: " +
        supportedCallingConventions.joinToString(", ")
    )
}
