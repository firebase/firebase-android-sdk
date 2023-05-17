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

import com.google.firebase.gradle.plugins.semver.AccessDescriptor
import com.google.firebase.gradle.plugins.semver.ClassInfo
import com.google.firebase.gradle.plugins.semver.UtilityClass
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

/**
 * Ensures that pom dependencies are not accidently downgraded.
 *
 * Compares the latest pom at gmaven for the given artifact with the one generate for the current
 * release.
 *
 * @property pomFile The pom file for the current release
 * @property artifactId The artifactId for the pom parent
 * @property groupId The groupId for the pom parent
 * @throws GradleException if a dependency is found with a degraded version
 */
abstract class KotlinTransform : DefaultTask() {
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>
  @get:Input abstract val currentJar: Property<String>
  @get:Input abstract val projectPath: Property<String>

  @TaskAction
  fun run() {
    val classesJar = UtilityClass.readApi(currentJar.get())
    //   transformJavaFiles()
    for (key in classesJar.keys) {
      val classInfo: ClassInfo = classesJar.get(key)!!
      // Public fields
      classInfo.fields
        .filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) && !accessDescriptor.isPrivate()
        }
        .forEach { println(it.key) }
      classInfo.methods
        .filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) && accessDescriptor.isPublic()
        }
        .forEach {
          println(key)
          println(getMethodSignature(it.value))
        }
    }
  }

  private fun getMethodSignature(methodNode: MethodNode): String {
    val parameterTypes = Type.getArgumentTypes(methodNode.desc)
    var parameterClasses = mutableListOf<String>()
    for (type in parameterTypes) {
      parameterClasses.add(type.getClassName())
    }
    if (methodNode.signature == null) {
      return ""
    }
    return methodNode.signature
    //
    //    return String.format(
    //      "%s %s %s(%s)",
    //      AccessDescriptor(methodNode.access).getVerboseDescription(),
    //      Type.getReturnType(methodNode.desc).className,
    //      methodNode.name,
    //      parameterClasses.joinToString(", ")
    //    )
  }

  private fun hasNonPublicMethodSignature(methodNode: MethodNode): Boolean {
    if (
      UtilityClass.isObfuscatedSymbol(
        getUnqualifiedClassname(Type.getReturnType(methodNode.desc).getClassName())
      )
    ) {
      return true
    }

    val parameterTypes = Type.getArgumentTypes(methodNode.desc)
    for (type in parameterTypes) {
      if (UtilityClass.isObfuscatedSymbol(getUnqualifiedClassname(type.getClassName()))) {
        return true
      }
    }
    return false
  }

  private fun hasNonPublicFieldSignature(fieldNode: FieldNode): Boolean {
    val fieldType = Type.getType(fieldNode.desc)
    return UtilityClass.isObfuscatedSymbol(getUnqualifiedClassname(fieldType.getClassName()))
  }

  fun getUnqualifiedClassname(classNodeName: String): String {
    // Class names may be "/" or "." separated depending on context. Normalize to "/"
    val normalizedPath = classNodeName.replace(".", "/")
    val withoutPackage = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1)
    return withoutPackage.substring(withoutPackage.lastIndexOf('$') + 1)
  }

  private fun transformJavaFiles() {
    val javaPath = "${projectPath.get()}/src/main/java"
    val packageName = "${groupId.get()}.${artifactId.get().split("-")[1]}"
    val packageNamePath = packageName.replace(".", "/")
    File("${javaPath}/${packageNamePath}/package-info.java")
      .writeText(
        """
      // Copyright 2022 Google LLC
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

      /** @hide */
      package ${packageName};
    """
          .trimIndent()
      )
    File(javaPath).walk().forEach {
      if (it.absolutePath.endsWith(".java")) {
        var lines = mutableListOf<String>()
        File(it.absolutePath).forEachLine {
          val regex = "package ${packageName}"
          val importRegex = "import ${packageName}"
          lines.add(it.replace(regex, "${regex}.java").replace(importRegex, "${importRegex}.java"))
        }
        File(it.absolutePath).delete()

        val newFile = File(it.absolutePath.replace(packageNamePath, "${packageNamePath}/java"))
        if (!newFile.exists()) {
          newFile.parentFile.mkdirs()
          newFile.writeText(lines.joinToString("\n"))
        }
      }
    }
  }
}
