package com.google.firebase.gradle.plugins

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.project
import org.w3c.dom.Element

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
          File(it.absolutePath).readLines().filter { x ->
            !x.contains("contains(LIBRARY_NAME)") &&
              !x.contains("LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)")
          }
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

  fun copyManifestComponent(source: String, dest: String) {
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val ktxManifest = documentBuilder.parse(File(source))
    ktxManifest.documentElement.normalize()
    val nodeList = ktxManifest.getElementsByTagName("*")
    val metadata =
      (0..nodeList.length - 1)
        .toList()
        .map { x -> nodeList.item(x) }
        .filter { it.nodeType == Element.ELEMENT_NODE }
        .map { x -> (x as Element) }
        .filter { it.nodeName == "meta-data" }
        .map { x ->
          "<meta-data android:name=\"${x.getAttribute("android:name").replace(".ktx.",".")}\" android:value=\"${x.getAttribute("android:value")}\"/>"
        }
        .joinToString("\n")

    val lines = File(dest).readLines()
    var output = mutableListOf<String>()
    var inside = false
    for (line in lines) {
      if (
        line.contains("android:name=\"com.google.firebase.components.ComponentDiscoveryService\"")
      ) {
        inside = true
      }
      if (inside == true) {
        if (line.contains("/>")) {
          output.add(line.replace("/>", "> ${metadata} </service>"))
          inside = false
          continue
        } else if (line.contains(">")) {
          output.add(line.replace(">", "> ${metadata}"))
          inside = false
          continue
        }
      }
      output.add(line)
    }
    File(dest).writeText(output.joinToString("\n"))
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
    copyManifestComponent(
      "${projectPath.get()}/ktx/src/main/AndroidManifest.xml",
      "${projectPath.get()}/src/main/AndroidManifest.xml"
    )
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
      (dependencies.toSet() + ktxDependencies.toSet())
        .toList()
        .sorted()
        .map { x -> "    " + x }
        .filter { !it.contains("project(\"${project.path}\")") }
    val lines = File(gradlePath).readLines()
    val output = mutableListOf<String>()
    for (line in lines) {
      if (line.contains("id(\"kotlin-android\")")) {
        continue
      }
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
