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

  fun getSymbol(line: String): String {
    val parts = line.split(" ")
    if (parts.get(0) == "val" || parts.get(0) == "class") {
      // Field or class
      return parts.get(1).replace(":", "")
    } else if (parts.get(0) == "object") {
      return parts.get(1)
    } else if (parts.get(0) == "fun") {
      // Method
      var output: String = ""
      var ignore = false
      for (c in line) {
        if (c == ':') {
          ignore = true
        }
        if (c == ')' || c == ',') {
          ignore = false
        }
        if (c == '=') {
          break
        }
        if (!ignore) {
          output += c
        }
      }
      return output.replace("fun", "")
    }
    return ""
  }

  fun copyDir(src: Path, dest: Path) {
    Files.walk(src).forEach {
      if (!Files.isDirectory(it)) {
        Files.copy(it, dest.resolve(src.relativize(it)), StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }

  fun deprecateKTX(src: String, pkgName: String) {
    File(src).walk().forEach {
      if (it.absolutePath.endsWith(".kt")) {
        val lines = File(it.absolutePath).readLines()
        val output = mutableListOf<String>()
        for (i in 0 until lines.size) {
          output.add(lines[i])
          if (lines[i].contains("*/")) {
            var symbol = ""
            var ctr = i + 1
            while (symbol.isEmpty()) {
              symbol = getSymbol(lines[ctr++]).trim()
            }
            output.add(
              """@Deprecated("Use `${pkgName}${symbol}`", ReplaceWith("${pkgName}${symbol}"))"""
            )
          }
        }
        File(it.absolutePath).writeText(output.joinToString("\n"))
      }
    }
  }
  fun updateKTXReferences(src: String) {
    // Remove all .ktx suffixes essentially.
    File(src).walk().forEach {
      if (it.absolutePath.endsWith(".kt") && !it.absolutePath.contains("/ktx/")) {
        val lines = File(it.absolutePath).readLines().map { x -> x.replace(".ktx", "") }
        File(it.absolutePath).delete()
        val newFile = File(it.absolutePath)
        newFile.writeText(lines.joinToString("\n"))
      }
    }
  }

  fun updatePlatformLogging(src: String) {
    File(src).walk().forEach {
      if (it.absolutePath.endsWith(".kt")) {
        val lines =
          File(it.absolutePath).readLines().map { x ->
            x.replace(
              """LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME),""",
              ""
            )
          }
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
    updatePlatformLogging(mainPackagePath)
    deprecateKTX(ktxPackagePath, packageNamePath.replace("/", "."))
    project.dependencies.create("com.google.firebase:firebase-functions-ktx:20.3.1")
  }
}
