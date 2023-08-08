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
        val destination = dest.resolve(src.relativize(it))
        Files.createDirectories(destination.parent)
        Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING)
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
          }.filter { x-> !x.contains("contains(LIBRARY_NAME)") }
        File(it.absolutePath).delete()
        val newFile = File(it.absolutePath)
        newFile.writeText(lines.joinToString("\n"))
      }
    }
  }

  fun readDependencies(path: String): List<String> {
    val lines = File(path).readLines()
    var start = false
    var output = mutableListOf<String>()
    for (line in lines) {
      if (start == true) {
        if (line.contains("}")) {
          break
        }
        output.add(line.trim())
      }

      if (line.contains("dependencies {")) {
        start = true
      }
    }
    return output.filter { x -> (x.trim().isNotEmpty() && !x.startsWith("//")) }
  }

  @TaskAction
  fun run() {
    var packageNamePath = "${groupId.get()}.${artifactId.get().split("-")[1]}".replace(".", "/")
    packageNamePath = packageNamePath.replace("common", "")
    val ktxArtifactPath = "${projectPath.get()}/ktx/src/main/kotlin/${packageNamePath}/ktx"
    val ktxPackagePath = "${projectPath.get()}/src/main/java/${packageNamePath}/ktx"
    val mainPackagePath = "${projectPath.get()}/src/main/java/${packageNamePath}"
    val ktxArtifactTestPath = "${projectPath.get()}/ktx/src/test/kotlin/${packageNamePath}/ktx"
    val ktxPackageTestPath = "${projectPath.get()}/src/test/java/${packageNamePath}/ktx"
    val mainPackageTestPath = "${projectPath.get()}/src/test/java/${packageNamePath}"
    val ktxArtifactAndroidTestPath =
      "${projectPath.get()}/ktx/src/androidTest/kotlin/${packageNamePath}/ktx"
    val ktxPackageAndroidTestPath =
      "${projectPath.get()}/src/androidTest/java/${packageNamePath}/ktx"
    val mainPackageAndroidTestPath = "${projectPath.get()}/src/androidTest/java/${packageNamePath}"
    copyDir(File(ktxArtifactPath).toPath(), File(ktxPackagePath).toPath())
    copyDir(File(ktxArtifactPath).toPath(), File(mainPackagePath).toPath())
    if (File(ktxArtifactTestPath).exists()) {
      copyDir(File(ktxArtifactTestPath).toPath(), File(mainPackageTestPath).toPath())
      copyDir(File(ktxArtifactTestPath).toPath(), File(ktxPackageTestPath).toPath())
      updateKTXReferences(mainPackageTestPath)
    }
    if (File(ktxArtifactAndroidTestPath).exists()) {
      copyDir(File(ktxArtifactAndroidTestPath).toPath(), File(mainPackageAndroidTestPath).toPath())
      copyDir(File(ktxArtifactAndroidTestPath).toPath(), File(ktxPackageAndroidTestPath).toPath())
      updateKTXReferences(mainPackageAndroidTestPath)
    }
    updateKTXReferences(mainPackagePath)
    updatePlatformLogging("${projectPath.get()}/src")
    deprecateKTX(ktxPackagePath, packageNamePath.replace("/", "."))
    var gradlePath = "${projectPath.get()}/${artifactId.get()}.gradle.kts"
    var ktxGradlePath = "${projectPath.get()}/ktx/ktx.gradle.kts"
    if (!File(gradlePath).exists()) {
      gradlePath = "${projectPath.get()}/${artifactId.get()}.gradle"
      ktxGradlePath = "${projectPath.get()}/ktx/ktx.gradle"
    }
    val dependencies = readDependencies(gradlePath)
    val ktxDependencies = readDependencies(ktxGradlePath)
    val deps =
      (dependencies.toSet() + ktxDependencies.toSet()).toList().sorted().map { x -> "    " + x }
    val lines = File(gradlePath).readLines()
    val output = mutableListOf<String>()
    println(project.path)
    for (line in lines) {
      output.add(line)
      if (line.contains("id(\"firebase-library\")")) {
        output.add("    id(\"kotlin-android\")")
      }
      if (line.contains("dependencies {")) {
        output += deps
        output.add("}")
        break
      }
    }
    File(gradlePath).writeText(output.joinToString("\n"))
  }
}
