// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.project
import org.w3c.dom.Element

val PROJECT_LEVEL_REQUIRED =
  mutableListOf<String>(
    "firebase-appdistribution-api",
    "firebase-common",
    "firebase-config",
    "firebase-crashlytics",
    "firebase-components",
    "firebase-database",
    "firebase-dynamic-links",
    "firebase-firestore",
    "firebase-functions",
    "firebase-messaging",
    "firebase-inappmessaging",
    "firebase-inappmessaging-display",
    "firebase-installations",
    "firebase-ml-modeldownloader",
    "firebase-perf",
    "firebase-storage",
  )
val KTX_CONTENT =
  """
  // Copyright 2023 Google LLC
  //
  // Licensed under the Apache License, Version 2.0 (the "License");
  // you may not use this file except in compliance with the License.
  // You may obtain a copy of the License at
  //
  //      http://www.apache.org/licenses/LICENSE-2.0
  //
  // Unless required by applicable law or agreed to in writing, software
  // distributed under the License is distributed on an "AS IS" BASIS,
  // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  // See the License for the specific language governing permissions and
  // limitations under the License.
  package #{PACKAGE_NAME}.ktx

  import androidx.annotation.Keep
  import com.google.firebase.components.Component
  import com.google.firebase.components.ComponentRegistrar
  import #{PACKAGE_NAME}.BuildConfig
  import com.google.firebase.platforminfo.LibraryVersionComponent

  internal const val LIBRARY_NAME: String = #{LIBRARY_NAME}

  /** @suppress */
  @Keep
  class #{PROJECT_NAME}LoggingRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> {
      return listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
    }
  }
"""
    .trimIndent()

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

  fun copyDir(src: Path, dest: Path, cont: Boolean) {
    for (file in Files.walk(src)) {
      if (!Files.isDirectory(file)) {
        val destination = dest.resolve(src.relativize(file))
        Files.createDirectories(destination.parent)
        Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    if (!cont) return
    val dir: File = File(src.parent.toString())
    if (dir.exists() && dir.isDirectory) {
      for (file in Files.walk(dir.toPath())) {
        if (!Files.isDirectory(file) && !file.toAbsolutePath().contains(src.toAbsolutePath())) {
          val destination = dest.resolve(dir.toPath().relativize(file))
          Files.createDirectories(destination.parent)
          Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
  }

  fun deprecateKTX(src: String, pkgName: String) {
    for (file in File(src).walk()) {
      if (file.absolutePath.endsWith(".kt")) {
        val lines = File(file.absolutePath).readLines()
        val output = mutableListOf<String>()
        for (i in 0 until lines.size) {
          output.add(lines[i])
          if (lines[i].contains("*/")) {
            var symbol = ""
            var ctr = i + 1
            while (symbol.isEmpty() && ctr < lines.size) {
              symbol = getSymbol(lines[ctr++]).trim()
            }
            output.add(
              """@Deprecated("${pkgName}ktx.${symbol} has been deprecated. Use `${pkgName}${symbol}`", ReplaceWith(expression="${pkgName}${symbol}", imports=[]))"""
            )
          }
        }
        File(file.absolutePath).writeText(output.joinToString("\n"))
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
          File(it.absolutePath)
            .readLines()
            .map { x ->
              val p =
                x.replace(
                  "LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)",
                  ""
                )
              if (p.trim().isEmpty()) {
                return@map "#REMOVE#REMOVE#REMOVE"
              } else {
                return@map p
              }
            }
            .filter {
              !it.contains("#REMOVE#REMOVE#REMOVE") && !it.contains("contains(LIBRARY_NAME)")
            }
        File(it.absolutePath).delete()
        val newFile = File(it.absolutePath)
        newFile.writeText(lines.joinToString("\n"))
      }
    }
  }

  fun readDependencies(path: String): List<String> {
    val lines = File(path).readLines()
    var ctr = 0
    var output = mutableListOf<String>()
    var tmpString = ""
    for (line in lines) {
      if (ctr == 0) {
        if (line.contains("dependencies {")) {
          ctr++
          continue
        }
      } else if (ctr == 1) {
        if (line.contains("{") && !line.contains("}")) {
          tmpString = line
          ctr += 1
        } else if (line.contains("}") && !line.contains("{")) {
          break
        } else {
          output.add(line.trim())
        }
      } else if (ctr == 2) {
        val add = " "
        if (line.contains("}")) {
          ctr = 1
          tmpString += "\n" + add + line
          output.add(tmpString.trim())
        } else {
          tmpString += "\n" + add + line
        }
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
    if (artifactId.get().equals("firebase-config")) {
      packageNamePath = "com/google/firebase/remoteconfig"
    } else if (artifactId.get().equals("firebase-dynamic-links")) {
      packageNamePath = "com/google/firebase/dynamiclinks"
    } else if (artifactId.get().equals("firebase-ml-modeldownloader")) {
      packageNamePath = "com/google/firebase/ml/modeldownloader"
    } else if (artifactId.get().equals("firebase-inappmessaging-display")) {
      packageNamePath = "com/google/firebase/inappmessaging/display"
    } else if (artifactId.get().contains("appcheck")) {
      packageNamePath = "com/google/firebase/appcheck"
    }
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
    copyDir(File(ktxArtifactPath).toPath(), File(ktxPackagePath).toPath(), false)
    copyDir(File(ktxArtifactPath).toPath(), File(mainPackagePath).toPath(), true)
    if (File(ktxArtifactTestPath).exists()) {
      copyDir(File(ktxArtifactTestPath).toPath(), File(mainPackageTestPath).toPath(), true)
      copyDir(File(ktxArtifactTestPath).toPath(), File(ktxPackageTestPath).toPath(), false)
      updateKTXReferences(mainPackageTestPath)
    }
    if (File(ktxArtifactAndroidTestPath).exists()) {
      copyDir(
        File(ktxArtifactAndroidTestPath).toPath(),
        File(mainPackageAndroidTestPath).toPath(),
        true
      )
      copyDir(
        File(ktxArtifactAndroidTestPath).toPath(),
        File(ktxPackageAndroidTestPath).toPath(),
        false
      )
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
    var ktxDependencies =
      readDependencies(ktxGradlePath)
        .filter {
          !it.contains("project(\"${project.path}\")") && !it.contains("project('${project.path}')")
        }
        .toMutableList()
    val filtered_project_deps =
      PROJECT_LEVEL_REQUIRED.map { x -> ":${x}" }.filter { it != project.path }
    val deps =
      (dependencies.toSet() + ktxDependencies.toSet())
        .toList()
        .map { x ->
          val matches =
            filtered_project_deps.filter { y ->
              x.contains(y) && !x.contains("interop") && !x.contains("collection")
            }
          if (matches.isEmpty()) return@map x
          return@map "implementation(project(\"${matches.get(0)}\"))"
        }
        .toSet()
        .toList()

    updateGradleFile(gradlePath, deps)
    // KTX changes
    updateCode(
      ktxArtifactPath,
      packageNamePath.replace("/", "."),
      "${projectPath.get()}/ktx/src/main/AndroidManifest.xml"
    )
    ktxDependencies =
      ktxDependencies
        .filter {
          !((it.contains("implementation") || it.contains("api")) &&
            (!it.contains("firebase") || it.contains(project.path) || it.contains("-ktx")))
        }
        .map { x ->
          val filtered = PROJECT_LEVEL_REQUIRED.filter { x.contains("${it}:") }
          if (filtered.isEmpty()) return@map x
          else return@map "api(project(\":${filtered.get(0)}\"))"
        }
        .toMutableList()
    // KTX gradle changes
    ktxDependencies.add("api(project(\"${project.path}\"))")
    updateGradleFile(ktxGradlePath, ktxDependencies)
  }
  private fun updateGradleFile(gradlePath: String, depsArg: List<String>) {
    val deps = depsArg.sorted().map { x -> "    " + x }
    val lines = File(gradlePath).readLines()
    val output = mutableListOf<String>()
    for (line in lines) {
      if (line.contains("id(\"kotlin-android\")") || line.contains("id 'kotlin-android'")) {
        continue
      }
      if (
        line.contains("plugins { id(\"firebase-library\") }") ||
          line.contains("plugins { id 'firebase-library' }")
      ) {
        output.add("plugins {")
        output.add("    id(\"firebase-library\")")
        output.add("    id(\"kotlin-android\")")
        output.add("}")
        continue
      }
      output.add(line)
      if (line.contains("id(\"firebase-library\")") || line.contains("id 'firebase-library'")) {
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
  private fun extractLibraryName(path: String): String? =
    File(path)
      .readLines()
      .filter { x -> x.contains("LIBRARY_NAME:") }
      .map { x -> x.split(" ").last() }
      .getOrNull(0)
  private fun updateCode(path: String, pkgName: String, manifestPath: String) {
    File(path).walk().forEach {
      if (
        it.absolutePath.endsWith(".kt") &&
          !it.absolutePath.endsWith("ChildEvent.kt") &&
          !it.absolutePath.endsWith("Logging.kt")
      ) {
        val filePath = it.absolutePath
        val projectName = artifactId.get().split("-").map { x -> x.capitalized() }.joinToString("")
        val replaceClass: String? =
          File(filePath)
            .readLines()
            .filter { it.contains("class") && it.contains("KtxRegistrar") }
            .map { x -> x.split(" ").get(1).replace(":", "") }
            .getOrNull(0)
        val loggingPath: String = "${File(filePath).parent}/Logging.kt"
        val libraryName = extractLibraryName(filePath)
        if (!libraryName.isNullOrEmpty()) {
          File(loggingPath)
            .writeText(
              KTX_CONTENT.replace("#{LIBRARY_NAME}", libraryName)
                .replace("#{PROJECT_NAME}", projectName)
                .replace("#{PACKAGE_NAME}", pkgName.trim('.'))
            )
        }
        if (!replaceClass.isNullOrEmpty()) {
          val lines =
            File(manifestPath).readLines().map { x ->
              x.replace(replaceClass, "${projectName}LoggingRegistrar")
            }
          File(manifestPath).writeText(lines.joinToString("\n"))
        }
        File(filePath).delete()
      }
    }
  }
}
