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

import com.google.cloud.storage.Blob
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableVersionsRegistry.VersionInfo
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableVersionsRegistry.serializedValue
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.random.Random
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@Suppress("unused")
abstract class UpdateDataConnectExecutableVersionsTask : DefaultTask() {

  @get:Input abstract val jsonFile: Property<File>

  @get:Internal abstract val workDirectory: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  init {
    // Make sure the task ALWAYS runs and is never skipped because Gradle deems it "up to date".
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun run() {
    val jsonFile: File = jsonFile.get()
    val workDirectory: File = workDirectory.get().asFile

    logger.info("jsonFile={}", jsonFile.absolutePath)
    logger.info("workDirectory={}", workDirectory.absolutePath)

    logger.lifecycle("Loading executable versions from registry file: {}", jsonFile.absolutePath)
    val registry = DataConnectExecutableVersionsRegistry.load(jsonFile)
    logger.info(
      "Loaded {} executable versions from registry file {} (default={}): {}",
      registry.versions.size,
      jsonFile.absolutePath,
      registry.defaultVersion,
      registry.versions.sortedWith(versionInfoComparator).toLogString()
    )

    val cloudStorageVersions: Set<CloudStorageVersionInfo> =
      StorageOptions.getDefaultInstance()
        .service
        .getDataConnectExecutablesBucket()
        .list(BlobListOption.prefix("emulator/"))
        .iterateAll()
        .mapNotNull { it.toCloudStorageVersionInfoOrNull() }
        .toSet()

    val cloudStorageVersionsMissingFromRegistry: List<CloudStorageVersionInfo> =
      cloudStorageVersions.filterNotIn(registry).sortedWith(cloudStorageVersionInfoComparator)
    if (cloudStorageVersionsMissingFromRegistry.isEmpty()) {
      logger.lifecycle(
        "Not updating {} since it already contains all versions.",
        jsonFile.absolutePath
      )
      return
    }

    logger.lifecycle(
      "Downloading details for {} versions missing from registry file: {}",
      cloudStorageVersionsMissingFromRegistry.size,
      cloudStorageVersionsMissingFromRegistry.toLogString()
    )

    val updatedRegistry =
      registry.updatedWith(
        cloudStorageVersionsMissingFromRegistry.map { it.toRegistryVersionInfo(workDirectory) }
      )

    logger.lifecycle(
      "Updating {} with {} versions: {}",
      jsonFile.absolutePath,
      cloudStorageVersionsMissingFromRegistry.size,
      cloudStorageVersionsMissingFromRegistry.toLogString()
    )

    if (updatedRegistry.defaultVersion == registry.defaultVersion) {
      logger.lifecycle(
        "Not updating default version in {} because it is already the latest version: {}",
        jsonFile.absolutePath,
        updatedRegistry.defaultVersion
      )
    } else {
      logger.lifecycle(
        "Updating default version in {} to the latest version: {} (was {})",
        jsonFile.absolutePath,
        updatedRegistry.defaultVersion,
        registry.defaultVersion
      )
    }

    DataConnectExecutableVersionsRegistry.save(updatedRegistry, jsonFile)
  }

  private data class CloudStorageVersionInfo(
    val version: Version,
    val operatingSystem: OperatingSystem,
    val blob: Blob,
  )

  private fun Storage.getDataConnectExecutablesBucket(): Bucket {
    val bucketName = "firemat-preview-drop"
    logger.lifecycle("Finding all Data Connect executable versions in GCS bucket: {}", bucketName)

    return runCatching { get(bucketName) }
      .onFailure { e ->
        if (
          e is StorageException &&
            e.cause.let {
              it is com.google.api.client.http.HttpResponseException &&
                (it.statusCode == 401 || it.statusCode == 403)
            }
        ) {
          logger.error(
            "ERROR: 401/403 error returned from Google Cloud Storage; " +
              "try running \"gcloud auth application-default login\" and/or unsetting the " +
              "GOOGLE_APPLICATION_CREDENTIALS environment variable to fix"
          )
        }
      }
      .getOrThrow()
      ?: throw DataConnectGradleException("bvkxzp2esg", "GCS bucket not found: $bucketName")
  }

  private fun Blob.toCloudStorageVersionInfoOrNull(): CloudStorageVersionInfo? {
    logger.debug("[av7zhespw2] Found Data Connect executable file: {}", name)
    val match =
      fileNameRegex.matchEntire(name)
        ?: run {
          logger.debug(
            "[p4vjjcp2kq] Ignoring Data Connect executable file: {} " +
              "(does not match regex: {})",
            name,
            fileNameRegex
          )
          return null
        }

    val versionString = match.groups[2]?.value
    val version = versionString?.toVersionOrNull(strict = false)
    if (version === null) {
      logger.info(
        "Ignoring Data Connect executable file: {} " +
          "(invalid version: {} (in match for regex {}))",
        name,
        versionString,
        fileNameRegex
      )
      return null
    }

    if (version < minVersion) {
      logger.info(
        "Ignoring Data Connect executable file: {} " +
          "(version {} is less than the minimum version: {})",
        name,
        versionString,
        minVersion
      )
      return null
    }

    if (version in invalidVersions) {
      logger.info(
        "Ignoring Data Connect executable file: {} " + "(version {} is a known invalid version)",
        name,
        versionString
      )
      return null
    }

    val operatingSystem =
      when (val operatingSystemString = match.groups[1]?.value) {
        "linux" -> OperatingSystem.Linux
        "macos" -> OperatingSystem.MacOS
        "windows" -> OperatingSystem.Windows
        else -> {
          logger.info(
            "WARNING: Ignoring Data Connect executable file: {} " +
              "(unknown operating system name: {} (in match for regex {}))",
            name,
            operatingSystemString,
            fileNameRegex
          )
          return null
        }
      }

    return CloudStorageVersionInfo(version, operatingSystem, blob = this)
  }

  private fun CloudStorageVersionInfo.toRegistryVersionInfo(workDirectory: File): VersionInfo {
    val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)

    logger.lifecycle(
      "Downloading version {} ({} bytes, created {})",
      "$version-${operatingSystem.serializedValue}",
      blob.size.toStringWithThousandsSeparator(),
      dateFormatter.format(blob.createTimeOffsetDateTime.atZoneSameInstant(ZoneId.systemDefault()))
    )
    workDirectory.mkdirs()
    val outputFile = File(workDirectory, Random.nextAlphanumericString(12))
    outputFile.outputStream().use { dest -> blob.downloadTo(dest) }

    val fileInfo = DataConnectExecutableDownloadTask.FileInfo.forFile(outputFile)
    outputFile.delete()
    check(fileInfo.sizeInBytes == blob.size) {
      "fileInfo.sizeInBytes!=blob.size (${fileInfo.sizeInBytes}!=${blob.size}) and this should " +
        "never happen; if it _does_ happen it _could_ indicate a compromised " +
        "downloaded binary [y5967yd2cf]"
    }
    return VersionInfo(version, operatingSystem, fileInfo.sizeInBytes, fileInfo.sha512DigestHex)
  }

  private companion object {

    val versionInfoComparator =
      compareBy<VersionInfo> { it.version }.thenByDescending { it.os.serializedValue }

    val cloudStorageVersionInfoComparator =
      compareBy<CloudStorageVersionInfo> { it.version }
        .thenByDescending { it.operatingSystem.serializedValue }

    @JvmName("toLogStringCloudStorageVersionInfo")
    fun Iterable<CloudStorageVersionInfo>.toLogString(): String = joinToString {
      "${it.version}-${it.operatingSystem.serializedValue}"
    }

    @JvmName("toLogStringVersionInfo")
    fun Iterable<VersionInfo>.toLogString(): String = joinToString {
      "${it.version}-${it.os.serializedValue}"
    }

    val invalidVersions = setOf("1.15.0".toVersion())
    val minVersion = "1.3.4".toVersion()
    val fileNameRegex = ".*dataconnect-emulator-([^-]+)-v(.*)".toRegex()

    /**
     * Creates a returns a new list that contains all elements of the receiving [Iterable] that are
     * not in the given registry.
     */
    private fun Iterable<CloudStorageVersionInfo>.filterNotIn(
      registry: DataConnectExecutableVersionsRegistry.Root
    ): List<CloudStorageVersionInfo> = filterNot { cloudStorageVersion ->
      registry.versions.any {
        it.version == cloudStorageVersion.version && it.os == cloudStorageVersion.operatingSystem
      }
    }

    private fun DataConnectExecutableVersionsRegistry.Root.updatedWith(
      updatedVersions: Iterable<VersionInfo>
    ): DataConnectExecutableVersionsRegistry.Root {
      val allVersions = buildList {
        addAll(versions)
        addAll(updatedVersions)
        sortWith(versionInfoComparator)
      }
      return copy(defaultVersion = allVersions.maxOf { it.version }, versions = allVersions)
    }
  }
}
