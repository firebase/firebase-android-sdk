package com.google.firebase.dataconnect.gradle.plugin

import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.Companion.downloadDataConnectExecutable
import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableDownloadTask.FileInfo
import java.io.File
import kotlin.random.Random
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class UpdateDataConnectExecutableVersionsTask : DefaultTask() {

  @get:InputFile abstract val jsonFile: RegularFileProperty

  @get:Input abstract val version: Property<String>

  @get:Internal abstract val workDirectory: DirectoryProperty

  @TaskAction
  fun run() {
    val jsonFile: File = jsonFile.get().asFile
    val version: String = version.get()
    val workDirectory: File = workDirectory.get().asFile

    logger.info("jsonFile={}", jsonFile.absolutePath)
    logger.info("version={}", version)
    logger.info("workDirectory={}", workDirectory)

    val windowsExecutable = download(version, OperatingSystem.Windows, workDirectory)
    val macosExecutable = download(version, OperatingSystem.MacOS, workDirectory)
    val linuxExecutable = download(version, OperatingSystem.Linux, workDirectory)

    logger.info("Loading JSON file {}", jsonFile.absolutePath)
    val oldJson = DataConnectExecutableVersionsRegistry.load(jsonFile)
    val newJson = oldJson.withVersions(version, windowsExecutable, macosExecutable, linuxExecutable)
    logger.info(
      "Writing information about version $version to JSON file: {}",
      jsonFile.absolutePath
    )
    DataConnectExecutableVersionsRegistry.save(newJson, jsonFile)
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

    downloadDataConnectExecutable(version, operatingSystem, outputFile)

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
}