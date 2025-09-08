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

import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.Companion.downloadDataConnectExecutable
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.FileInfo
import java.io.File
import javax.inject.Inject
import kotlin.random.Random
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@Suppress("unused")
abstract class UpdateDataConnectExecutableVersionsTask : DefaultTask() {

  @get:InputFile abstract val jsonFile: RegularFileProperty

  @get:Input abstract val versions: ListProperty<String>

  @get:Input @get:Optional abstract val defaultVersion: Property<String>

  @get:Input @get:Optional abstract val updateMode: Property<UpdateMode>

  @get:Internal abstract val workDirectory: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  @TaskAction
  fun run() {
    val jsonFile: File = jsonFile.get().asFile
    val versions: List<String> = versions.get()
    val defaultVersion: String? = defaultVersion.orNull
    val updateMode: UpdateMode? = updateMode.orNull
    val workDirectory: File = workDirectory.get().asFile

    logger.info("jsonFile={}", jsonFile.absolutePath)
    logger.info("versions={}", versions)
    logger.info("defaultVersion={}", defaultVersion)
    logger.info("updateMode={}", updateMode)
    logger.info("workDirectory={}", workDirectory)

    var json: DataConnectExecutableVersionsRegistry.Root =
      if (updateMode == UpdateMode.Overwrite) {
        DataConnectExecutableVersionsRegistry.Root(
          defaultVersion = "<unspecified>",
          versions = emptyList()
        )
      } else {
        logger.info("Loading JSON file {}", jsonFile.absolutePath)
        DataConnectExecutableVersionsRegistry.load(jsonFile)
      }

    if (defaultVersion !== null) {
      json = json.copy(defaultVersion = defaultVersion)
    }

    for (version in versions) {
      val windowsExecutable = download(version, OperatingSystem.Windows, workDirectory)
      val macosExecutable = download(version, OperatingSystem.MacOS, workDirectory)
      val linuxExecutable = download(version, OperatingSystem.Linux, workDirectory)
      json = json.withVersions(version, windowsExecutable, macosExecutable, linuxExecutable)
    }

    logger.info(
      "Writing information about versions {} to file with updateMode={}: {}",
      versions.joinToString(", "),
      updateMode,
      jsonFile.absolutePath
    )
    DataConnectExecutableVersionsRegistry.save(json, jsonFile)
  }

  private fun DataConnectExecutableVersionsRegistry.Root.withVersions(
    version: String,
    windows: DownloadedFile,
    macos: DownloadedFile,
    linux: DownloadedFile
  ): DataConnectExecutableVersionsRegistry.Root {
    data class UpdatedVersion(
      val operatingSystem: OperatingSystem,
      val sizeInBytes: Long,
      val sha512DigestHex: String,
    ) {
      constructor(
        operatingSystem: OperatingSystem,
        downloadedFile: DownloadedFile
      ) : this(operatingSystem, downloadedFile.sizeInBytes, downloadedFile.sha512DigestHex)
    }
    val updatedVersions =
      listOf(
        UpdatedVersion(OperatingSystem.Windows, windows),
        UpdatedVersion(OperatingSystem.MacOS, macos),
        UpdatedVersion(OperatingSystem.Linux, linux),
      )

    val newVersions = versions.toMutableList()
    for (updatedVersion in updatedVersions) {
      val index =
        newVersions.indexOfFirst {
          it.version == version && it.os == updatedVersion.operatingSystem
        }
      if (index >= 0) {
        val newVersion =
          newVersions[index].copy(
            size = updatedVersion.sizeInBytes,
            sha512DigestHex = updatedVersion.sha512DigestHex,
          )
        newVersions[index] = newVersion
      } else {
        val newVersion =
          DataConnectExecutableVersionsRegistry.VersionInfo(
            version = version,
            os = updatedVersion.operatingSystem,
            size = updatedVersion.sizeInBytes,
            sha512DigestHex = updatedVersion.sha512DigestHex,
          )
        newVersions.add(newVersion)
      }
    }

    return this.copy(versions = newVersions.toList())
  }

  private fun download(
    version: String,
    operatingSystem: OperatingSystem,
    outputDirectory: File
  ): DownloadedFile {
    val randomId = Random.nextAlphanumericString(length = 20)
    val outputFile =
      File(outputDirectory, "DataConnectToolkit_${version}_${operatingSystem}_$randomId")

    downloadDataConnectExecutable(version, operatingSystem, outputFile, execOperations)

    logger.info("Calculating SHA512 hash of file: {}", outputFile.absolutePath)
    val fileInfo = FileInfo.forFile(outputFile)

    return DownloadedFile(
      file = outputFile,
      sizeInBytes = fileInfo.sizeInBytes,
      sha512DigestHex = fileInfo.sha512DigestHex,
    )
  }

  private data class DownloadedFile(
    val file: File,
    val sizeInBytes: Long,
    val sha512DigestHex: String,
  )

  enum class UpdateMode {
    Overwrite,
    Update
  }
}
