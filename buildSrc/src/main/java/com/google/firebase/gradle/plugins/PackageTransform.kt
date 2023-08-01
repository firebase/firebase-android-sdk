package com.google.firebase.gradle.plugins

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class PackageTransform : DefaultTask() {
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>
  @get:Input abstract val projectPath: Property<String>

  fun copyDir(src: Path, dest: Path) {
    Files.walk(src).forEach {
      if (!Files.isDirectory(it)) {
        Files.copy(it, dest.resolve(src.relativize(it)), StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }

  fun deprecateKTX(src: String) {
    File(src).walk().forEach {
      if (it.absolutePath.endsWith(".kt")) {
        val lines = File(it.absolutePath).readLines()
        val output = mutableListOf<String>()
        for (line in lines) {
          output.add(line)
          if (line.contains("*/")) {
            output.add("@Deprecated")
          }
        }
        File(it.absolutePath).writeText(output.joinToString("\n"))
      }
    }
  }
  fun updateKTXReferences(src: String) {
    // Remove all .ktx suffixes essentially.
    File(src).walk().forEach {
      if (it.absolutePath.endsWith(".kt")) {
        val lines = File(it.absolutePath).readLines().map { x -> x.replace(".ktx", "") }
        File(it.absolutePath).delete()
        val newFile = File(it.absolutePath)
        newFile.writeText(lines.joinToString("\n"))
      }
    }
  }

  @TaskAction
  fun run() {
    var packageNamePath = "${groupId.get()}.${artifactId.get().split("-")[1]}".replace(".", "/")
    packageNamePath = packageNamePath.replace("common", "")
    val ktxArtifactPath = "${projectPath.get()}/ktx/src/main/kotlin/${packageNamePath}/ktx"
    val ktxPackagePath = "${projectPath.get()}/src/main/java/${packageNamePath}/ktx"
    val mainPackagePath = "${projectPath.get()}/src/main/java/${packageNamePath}"
    copyDir(File(ktxArtifactPath).toPath(), File(ktxPackagePath).toPath())
    copyDir(File(ktxArtifactPath).toPath(), File(mainPackagePath).toPath())
    updateKTXReferences(mainPackagePath)
    deprecateKTX(ktxPackagePath)
  }
}
