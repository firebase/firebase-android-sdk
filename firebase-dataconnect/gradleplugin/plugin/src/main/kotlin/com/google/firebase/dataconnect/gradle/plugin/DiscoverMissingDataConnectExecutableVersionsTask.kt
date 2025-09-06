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

import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage.StorageOptions
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.Companion.downloadDataConnectExecutable
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.FileInfo
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import java.io.File
import javax.inject.Inject
import kotlin.random.Random
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class DiscoverMissingDataConnectExecutableVersionsTask : DefaultTask() {

  @get:InputFile abstract val jsonFile: RegularFileProperty

  @get:Internal abstract val workDirectory: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  @TaskAction
  fun run() {
    val jsonFile: File = jsonFile.get().asFile
    val workDirectory: File = workDirectory.get().asFile

    logger.info("jsonFile={}", jsonFile.absolutePath)
    logger.info("workDirectory={}", workDirectory.absolutePath)

    val allVersions = downloadPublishedDataConnectExecutableBinaryInfos().map { it.version }
    if (true) {
      return
    }

    var json = DataConnectExecutableVersionsRegistry.load(jsonFile)
    val knownVersions = json.versions.map { it.version }.toSet()
    logger.info("Found ${knownVersions.size} known versions in ${jsonFile.name}.")

    val missingVersions = allVersions.filter { it.toString() !in knownVersions }
    logger.info("Found ${missingVersions.size} missing versions: $missingVersions")

    if (missingVersions.isEmpty()) {
      logger.info("No missing versions found. Nothing to do.")
      return
    }

    for (version in missingVersions) {
      logger.info("Processing new version: $version")
      val versionStr = version.toString()
      val windowsExecutable = download(versionStr, OperatingSystem.Windows, workDirectory)
      val macosExecutable = download(versionStr, OperatingSystem.MacOS, workDirectory)
      val linuxExecutable = download(versionStr, OperatingSystem.Linux, workDirectory)
      json = json.withVersion(versionStr, windowsExecutable, macosExecutable, linuxExecutable)
    }

    val latestVersion = allVersions.maxOrNull()!!
    json = json.copy(defaultVersion = latestVersion.toString())
    logger.info("Updating default version to $latestVersion")

    logger.info(
      "Writing information about versions {} to file: {}",
      missingVersions.joinToString(", "),
      jsonFile.absolutePath
    )
    DataConnectExecutableVersionsRegistry.save(json, jsonFile)
  }

  data class DataConnectExecutableBinaryInfo(
    val version: Version,
    val operatingSystem: OperatingSystem,
  )

  private fun downloadPublishedDataConnectExecutableBinaryInfos():
    Set<DataConnectExecutableBinaryInfo> {
    val storage = StorageOptions.getDefaultInstance().service
    val bucketName = "firemat-preview-drop"
    logger.info("Finding all Data Connect Emulator Binary versions in GCS bucket: {}", bucketName)
    val bucket =
      storage.get(bucketName)
        ?: throw DataConnectGradleException("bvkxzp2esg", "GCS bucket not found: $bucketName")

    val invalidVersions = setOf("1.15.0".toVersion())
    val minVersion = "1.3.4".toVersion()

    val blobs = bucket.list(BlobListOption.prefix("emulator/"))
    val regex = ".*dataconnect-emulator-([^-]+)-v(.*)".toRegex()
    val dataConnectExecutableBinaries =
      blobs
        .iterateAll()
        .mapNotNull {
          logger.debug("[av7zhespw2] Found Data Connect Emulator binary file: {}", it.name)
          val match =
            regex.matchEntire(it.name)
              ?: run {
                logger.debug(
                  "[p4vjjcp2kq] Ignoring Data Connect Emulator binary file: {} " +
                    "(does not match regex: {})",
                  it.name,
                  regex
                )
                return@mapNotNull null
              }
          DataConnectExecutableBinaryInfo(
            version =
              run {
                val versionString = match.groups[2]?.value
                versionString?.toVersionOrNull(strict = false)
                  ?: run {
                    logger.info(
                      "WARNING: Ignoring Data Connect Emulator binary file: {} " +
                        "(invalid version: {} (in match for regex {}))",
                      it.name,
                      versionString,
                      regex
                    )
                    return@mapNotNull null
                  }
              },
            operatingSystem =
              when (val operatingSystemString = match.groups[1]?.value) {
                "linux" -> OperatingSystem.Linux
                "macos" -> OperatingSystem.MacOS
                "windows" -> OperatingSystem.Windows
                else -> {
                  logger.info(
                    "WARNING: Ignoring Data Connect Emulator binary file: {} " +
                      "(unknown operating system name: {} (in match for regex {}))",
                    it.name,
                    operatingSystemString,
                    regex
                  )
                  return@mapNotNull null
                }
              },
          )
        }
        .filter { it.version >= minVersion }
        .filterNot { invalidVersions.contains(it.version) }
        .toSet()

    val versions = dataConnectExecutableBinaries.map { it.version }.distinct()
    logger.info(
      "Found {} Data Connect Emulator Binary versions in GCS bucket {}: {}",
      versions.size,
      bucketName,
      versions.sorted().joinToString(", ")
    )

    return dataConnectExecutableBinaries
  }

  private fun DataConnectExecutableVersionsRegistry.Root.withVersion(
    version: String,
    windows: DownloadedFile,
    macos: DownloadedFile,
    linux: DownloadedFile
  ): DataConnectExecutableVersionsRegistry.Root {
    val newVersions = versions.toMutableList()

    newVersions.add(
      DataConnectExecutableVersionsRegistry.VersionInfo(
        version = version,
        os = OperatingSystem.Windows,
        size = windows.sizeInBytes,
        sha512DigestHex = windows.sha512DigestHex
      )
    )
    newVersions.add(
      DataConnectExecutableVersionsRegistry.VersionInfo(
        version = version,
        os = OperatingSystem.MacOS,
        size = macos.sizeInBytes,
        sha512DigestHex = macos.sha512DigestHex
      )
    )
    newVersions.add(
      DataConnectExecutableVersionsRegistry.VersionInfo(
        version = version,
        os = OperatingSystem.Linux,
        size = linux.sizeInBytes,
        sha512DigestHex = linux.sha512DigestHex
      )
    )

    return this.copy(
      versions =
        newVersions.sortedWith(
          compareBy<DataConnectExecutableVersionsRegistry.VersionInfo> { it.version.toVersion() }
            .thenBy { it.os }
        )
    )
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

    outputFile.delete()

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
}
