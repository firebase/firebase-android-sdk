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

import com.google.cloud.storage.StorageOptions
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.Companion.downloadDataConnectExecutable
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.FileInfo
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

  private data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    companion object {
      fun fromString(versionString: String): Version {
        val parts = versionString.split(".").map { it.toInt() }
        return Version(parts[0], parts[1], parts[2])
      }
    }

    override fun compareTo(other: Version): Int {
      if (major != other.major) return major.compareTo(other.major)
      if (minor != other.minor) return minor.compareTo(other.minor)
      return patch.compareTo(other.patch)
    }

    override fun toString(): String {
      return "$major.$minor.$patch"
    }
  }

  @TaskAction
  fun run() {
    val jsonFile: File = jsonFile.get().asFile
    val workDirectory: File = workDirectory.get().asFile

    logger.info("jsonFile={}", jsonFile.absolutePath)
    logger.info("workDirectory={}", workDirectory)

    val allVersions = getAllPublishedVersions()
    logger.info("Found ${allVersions.size} versions in GCS bucket.")

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
      "Writing information about versions ${missingVersions.joinToString(", ")} to file: {},
      jsonFile.absolutePath
    )
    DataConnectExecutableVersionsRegistry.save(json, jsonFile)
  }

  private fun getAllPublishedVersions(): List<Version> {
    val storage = StorageOptions.getDefaultInstance().service
    val bucketName = "firemat-preview-drop"
    val bucket = storage.get(bucketName)
        ?: throw DataConnectGradleException("gcs_bucket_not_found", "Bucket $bucketName not found.")

    val blobs = bucket.list(com.google.cloud.storage.Storage.BlobListOption.prefix("emulator/"))
    val regex = ".*dataconnect-emulator-linux-v(\d+\.\d+\.\d+)".toRegex()
    val versionsStrings = blobs.iterateAll().mapNotNull { regex.matchEntire(it.name)?.groups?.get(1)?.value }.toSet()

    val versions = versionsStrings.map { Version.fromString(it) }.toMutableSet()
    // 1.15.0 is an invalid version that should be ignored.
    versions.remove(Version.fromString("1.15.0"))

    val minVersion = Version.fromString("1.3.4")
    return versions.filter { it >= minVersion }.sorted()
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
          compareBy<DataConnectExecutableVersionsRegistry.VersionInfo> {
            Version.fromString(it.version)
          }.thenBy { it.os }
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
