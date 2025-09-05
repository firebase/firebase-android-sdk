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
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class DataConnectExecutableDownloadTask : DefaultTask() {

  @get:InputFile @get:Optional abstract val inputFile: RegularFileProperty

  @get:Input @get:Optional abstract val version: Property<String>

  @get:Input @get:Optional abstract val operatingSystem: Property<OperatingSystem>

  @get:Internal abstract val buildDirectory: DirectoryProperty

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @get:Inject abstract val fileSystemOperations: FileSystemOperations

  @get:Inject abstract val execOperations: ExecOperations

  @TaskAction
  fun run() {
    val inputFile: File? = inputFile.orNull?.asFile
    val version: String? = version.orNull
    val operatingSystem: OperatingSystem = operatingSystem.get()
    val buildDirectory: File = buildDirectory.get().asFile
    val outputFile: File = outputFile.get().asFile

    logger.info("inputFile: {}", inputFile)
    logger.info("version: {}", version)
    logger.info("operatingSystem: {}", operatingSystem)
    logger.info("buildDirectory: {}", buildDirectory)
    logger.info("outputFile: {}", outputFile)

    logger.info("Deleting build directory: {}", buildDirectory)
    buildDirectory.deleteRecursively()

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
      downloadDataConnectExecutable(version, operatingSystem, outputFile, execOperations)
      verifyOutputFile(outputFile, operatingSystem, version)
    } else {
      throw DataConnectGradleException(
        "chc94cq7vx",
        "Neither 'inputFile' nor 'version' were specified," +
          " but exactly _one_ of them is required to be specified"
      )
    }
  }

  private fun verifyOutputFile(
    outputFile: File,
    operatingSystem: OperatingSystem,
    version: String
  ) {
    logger.info("Verifying file size and SHA512 digest of file: {}", outputFile)
    val fileInfo = FileInfo.forFile(outputFile)

    val allVersions = DataConnectExecutableVersionsRegistry.load().versions
    val allVersionNames =
      allVersions
        .asSequence()
        .filter { it.os == operatingSystem }
        .map { it.version }
        .distinct()
        .sorted()
        .joinToString(", ")
    val applicableVersions =
      allVersions.filter { it.version == version && it.os == operatingSystem }

    if (applicableVersions.isEmpty()) {
      val message =
        "verification information for Data Connect toolkit executable" +
          " version $version for $operatingSystem is not known;" +
          " known versions for $operatingSystem are: $allVersionNames" +
          " (loaded from ${DataConnectExecutableVersionsRegistry.PATH})"
      logger.error("ERROR: $message")
      throw DataConnectGradleException("ym8assbfgw", message)
    } else if (applicableVersions.size > 1) {
      val message =
        "INTERNAL ERROR: ${applicableVersions.size} verification information records for" +
          " Data Connect toolkit executable version $version for $operatingSystem were found in" +
          " ${DataConnectExecutableVersionsRegistry.PATH}, but expected exactly 1"
      logger.error("ERROR: $message")
      throw DataConnectGradleException("zyw5xrky6e", message)
    }

    val versionInfo = applicableVersions.single()
    val verificationErrors = mutableListOf<String>()
    if (fileInfo.sizeInBytes != versionInfo.size) {
      logger.error(
        "ERROR: File ${outputFile.absolutePath} has an unexpected size (in bytes): actual is " +
          fileInfo.sizeInBytes.toStringWithThousandsSeparator() +
          " but expected " +
          versionInfo.size.toStringWithThousandsSeparator()
      )
      verificationErrors.add("file size mismatch")
    }
    if (fileInfo.sha512DigestHex != versionInfo.sha512DigestHex) {
      logger.error(
        "ERROR: File ${outputFile.absolutePath} has an unexpected SHA512 digest:" +
          " actual is ${fileInfo.sha512DigestHex}" +
          " but expected ${versionInfo.sha512DigestHex}"
      )
      verificationErrors.add("SHA512 digest mismatch")
    }

    if (verificationErrors.isNotEmpty()) {
      val errorMessage =
        "Verification of ${outputFile.absolutePath}" +
          " (version=${versionInfo.version} os=${versionInfo.os}) failed:" +
          " ${verificationErrors.joinToString(", ")}"
      logger.error(errorMessage)
      throw DataConnectGradleException("x9dfwhjr9c", errorMessage)
    }

    logger.info("Verifying file size and SHA512 digest succeeded")
  }

  data class FileInfo(val sizeInBytes: Long, val sha512DigestHex: String) {
    companion object {
      fun forFile(file: File): FileInfo {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(8192)
        var bytesRead: Long = 0

        file.inputStream().use {
          while (true) {
            val curBytesRead = it.read(buffer)
            if (curBytesRead < 0) {
              break
            }
            bytesRead += curBytesRead
            digest.update(buffer, 0, curBytesRead)
          }
        }

        return FileInfo(bytesRead, toHexString(digest.digest()))
      }
    }
  }

  private fun runWithFile(inputFile: File, outputFile: File) {
    if (inputFile == outputFile) {
      logger.info("inputFile == outputFile; nothing to copy ({})", inputFile)
      return
    }

    logger.info("Copying {} to {}", inputFile, outputFile)
    fileSystemOperations.copy {
      it.from(inputFile)
      it.into(outputFile.parentFile)
      it.rename(Pattern.quote(inputFile.name), Pattern.quote(outputFile.name))
    }
  }

  companion object {
    fun Task.downloadDataConnectExecutable(
      version: String,
      operatingSystem: OperatingSystem,
      outputFile: File,
      execOperations: ExecOperations
    ) {
      val osName =
        when (operatingSystem) {
          OperatingSystem.Windows -> "windows"
          OperatingSystem.MacOS -> "macos"
          OperatingSystem.Linux -> "linux"
        }
      val downloadFileName = "dataconnect-emulator-$osName-v$version"
      val url =
        URL("https://storage.googleapis.com/firemat-preview-drop/emulator/$downloadFileName")

      logger.info("Downloading {} to {}", url, outputFile)
      outputFile.parentFile.mkdirs()

      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"

      val responseCode = connection.responseCode
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw DataConnectGradleException(
          "n3mj6ahxwt",
          "Downloading Data Connect executable from $url failed with HTTP response code" +
            " $responseCode: ${connection.responseMessage}" +
            " (expected HTTP response code ${HttpURLConnection.HTTP_OK})"
        )
      }

      val startTime = System.nanoTime()
      val debouncer = Debouncer(5.seconds)
      outputFile.outputStream().use { oStream ->
        var downloadByteCount: Long = 0
        fun logDownloadedBytes() {
          val elapsedTime = (System.nanoTime() - startTime).toDuration(DurationUnit.NANOSECONDS)
          logger.info(
            "Downloaded {} bytes in {}",
            downloadByteCount.toStringWithThousandsSeparator(),
            elapsedTime
          )
        }
        connection.inputStream.use { iStream ->
          val buffer = ByteArray(8192)
          while (true) {
            val readCount = iStream.read(buffer)
            if (readCount < 0) {
              break
            }
            downloadByteCount += readCount
            debouncer.maybeRun(::logDownloadedBytes)
            oStream.write(buffer, 0, readCount)
          }
        }
        logDownloadedBytes()
      }

      if (operatingSystem != OperatingSystem.Windows) {
        execOperations.exec { execSpec ->
          execSpec.run {
            executable = "chmod"
            args = listOf("a+x", outputFile.absolutePath)
          }
        }
      }
    }
  }
}
