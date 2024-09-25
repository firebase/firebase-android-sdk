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

import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutable.VerificationInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
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

  @get:Input @get:Optional abstract val verificationInfo: Property<VerificationInfo>

  @get:Internal abstract val buildDirectory: DirectoryProperty

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun run() {
    val inputFile: File? = inputFile.orNull?.asFile
    val version: String? = version.orNull
    val verificationInfo: VerificationInfo? = verificationInfo.orNull
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

    if (verificationInfo !== null) {
      verifyOutputFile(outputFile, verificationInfo)
    }
  }

  private fun verifyOutputFile(outputFile: File, verificationInfo: VerificationInfo) {
    logger.info("Verifying file size and SHA512 digest of file: {}", outputFile)
    val fileInfo = FileInfo.forFile(outputFile)
    if (fileInfo.sizeInBytes != verificationInfo.fileSizeInBytes) {
      throw DataConnectGradleException(
        "zjdpbsjv42",
        "File $outputFile has an unexpected size (in bytes): actual=" +
          fileInfo.sizeInBytes.toStringWithThousandsSeparator() +
          " expected=" +
          verificationInfo.fileSizeInBytes.toStringWithThousandsSeparator()
      )
    } else if (fileInfo.sha512DigestHex != verificationInfo.sha512DigestHex) {
      throw DataConnectGradleException(
        "3yyma4dqga",
        "File $outputFile has an unexpected SHA512 digest:" +
          " actual=${fileInfo.sha512DigestHex} expected=${verificationInfo.sha512DigestHex}"
      )
    }
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
    project.copy {
      it.from(inputFile)
      it.into(outputFile.parentFile)
      it.rename(Pattern.quote(inputFile.name), Pattern.quote(outputFile.name))
    }
  }

  private fun runWithVersion(version: String, outputFile: File) {
    val fileName = "dataconnect-emulator-linux-v$version"
    val url = URL("https://storage.googleapis.com/firemat-preview-drop/emulator/$fileName")

    logger.info("Downloading {} to {}", url, outputFile)
    project.mkdir(outputFile.parentFile)

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

    project.exec { execSpec ->
      execSpec.run {
        executable = "chmod"
        args = listOf("a+x", outputFile.absolutePath)
      }
    }
  }
}
