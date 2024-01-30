/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.gradle.plugins.semver

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

abstract class ApiDiffer : DefaultTask() {
  @get:Input abstract val currentJar: Property<String>

  @get:Input abstract val previousJar: Property<String>

  @get:Input abstract val version: Property<String>

  @get:Input abstract val previousVersionString: Property<String>

  private val CLASS_EXTENSION = ".class"

  @TaskAction
  fun run() {
    if (version.get().contains("beta") || previousVersionString.get().isNullOrEmpty()) {
      return
    }
    val (pMajor, pMinor, _) = previousVersionString.get().split(".")
    val (major, minor, _) = version.get().split(".")
    val curVersionDelta: VersionDelta =
      if (major > pMajor) VersionDelta.MAJOR
      else if (minor > pMinor) VersionDelta.MINOR else VersionDelta.PATCH
    val afterJar = readApi(currentJar.get())
    val beforeJar = readApi(previousJar.get())
    val classKeys = afterJar.keys union beforeJar.keys
    val apiDeltas =
      classKeys
        .map { className -> Pair(beforeJar.get(className), afterJar.get(className)) }
        .flatMap { (before, after) ->
          DeltaType.values().flatMap { it.getViolations(before, after) }
        }
    val deltaViolations: List<Delta> =
      if (curVersionDelta == VersionDelta.MINOR)
        apiDeltas.filter { it.versionDelta == VersionDelta.MAJOR }
      else if (curVersionDelta == VersionDelta.PATCH) apiDeltas else mutableListOf()
    if (!apiDeltas.isEmpty()) {
      val printString =
        apiDeltas.joinToString(
          prefix =
            "Here is a list of all the minor/major version bump changes which are made since the last release.\n",
          separator = "\n"
        ) {
          "[${it.versionDelta}] ${it.description}"
        }
      println(printString)
    }
    if (!deltaViolations.isEmpty()) {
      val outputString =
        deltaViolations.joinToString(
          prefix =
            "Here is a list of all the violations which needs to be fixed before we could release.\n",
          separator = "\n"
        ) {
          "[${it.versionDelta}] ${it.description}"
        }
      throw GradleException(outputString)
    }
  }

  private fun readApi(jarPath: String): Map<String, ClassInfo> {
    val classes: Map<String, ClassNode> = readClassNodes(Paths.get(jarPath))
    return classes.entries.associate { (key, value) -> key to ClassInfo(value, classes) }
  }

  /** Returns true if the class is local or anonymous. */
  private fun isLocalOrAnonymous(classNode: ClassNode): Boolean {
    // JVMS 4.7.7 says a class has an EnclosingMethod attribute iff it is local or anonymous.
    // ASM sets the "enclosing class" only if an EnclosingMethod attribute is present, so this
    // this test will not include named inner classes even though they have enclosing classes.
    return classNode.outerClass != null
  }

  private fun getUnqualifiedClassname(classNodeName: String): String {
    // Class names may be "/" or "." separated depending on context. Normalize to "/"
    val normalizedPath = classNodeName.replace(".", "/")
    val withoutPackage = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1)
    return withoutPackage.substring(withoutPackage.lastIndexOf('$') + 1)
  }

  fun readClassNodes(jar: Path): Map<String, ClassNode> {
    val classes: MutableMap<String, ClassNode> = LinkedHashMap()
    val inputStream = Files.newInputStream(jar)
    val jis = JarInputStream(inputStream)
    var je: JarEntry? = null
    while (true) {
      je = jis.nextJarEntry
      if (je == null) {
        break
      }
      if (!je.name.endsWith(CLASS_EXTENSION)) {
        continue
      }
      val expectedName = je.name.substring(0, je.name.length - CLASS_EXTENSION.length)
      val classNode: ClassNode = ClassNode()

      ClassReader(jis).accept(classNode, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

      if (!classNode.name.equals(expectedName)) {
        logger.error(
          "Classnode doesn't match expected name ${classNode.name}. This is not a valid jar."
        )
        continue
      }
      // Skip classes that appear to be obfuscated.
      if (UtilityClass.isObfuscatedSymbol(getUnqualifiedClassname(classNode.name))) {
        continue
      }
      // Skip local and anonymous classes.
      if (isLocalOrAnonymous(classNode)) {
        continue
      }
      // Skip private nested classes
      if (!classes.containsKey(classNode.name)) {
        classes.put(classNode.name, classNode)
      } else {
        project.logger.info("Duplicate class seen: ${classNode.name}")
      }
    }
    return classes
  }
}
