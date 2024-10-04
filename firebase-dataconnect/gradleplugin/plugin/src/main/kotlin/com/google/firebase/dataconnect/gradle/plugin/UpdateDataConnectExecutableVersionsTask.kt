package com.google.firebase.dataconnect.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class UpdateDataConnectExecutableVersionsTask : DefaultTask() {

  @get:InputFile
  abstract val jsonFile: RegularFileProperty

  @get:Input
  abstract val version: Property<String>

  @get:Internal
  abstract val workDirectory: DirectoryProperty

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
  }

  private fun download(version: String, operatingSystem: OperatingSystem, outputDirectory: File): File {
    logger.info("Downloading $version $operatingSystem to $outputDirectory")
    return outputDirectory
  }

}