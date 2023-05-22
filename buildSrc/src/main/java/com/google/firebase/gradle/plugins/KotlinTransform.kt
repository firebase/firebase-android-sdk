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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

fun getMethodSignature(methodNode: MethodNode): String {
  val parameterTypes = Type.getArgumentTypes(methodNode.desc)
  var parameterClasses = mutableListOf<String>()
  for (type in parameterTypes) {
    parameterClasses.add(type.getClassName())
  }
  return String.format(
    "%s %s %s(%s)",
    AccessDescriptor(methodNode.access).getVerboseDescription(),
    Type.getReturnType(methodNode.desc).className,
    methodNode.name,
    parameterClasses.joinToString(", ")
  )
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

fun MethodNode.transformToKotlin(clsName: String): String {
  val descriptor = AccessDescriptor(this.access)
  val parameterTypes = Type.getArgumentTypes(this.desc)
  var counter = 1
  var parameterClasses = mutableListOf<String>()
  var callStringList = mutableListOf<String>()

  parameterTypes.forEach { type ->
    for (type in parameterTypes) {
      parameterClasses.add("param${counter}: ${type.getClassName()}")
      callStringList.add("param${counter}")
      counter += 1
    }
  }
  var def =
    "fun ${this.name} (${parameterClasses.joinToString(", ")}) : ${Type.getReturnType(this.desc).className}"
  if (descriptor.isStatic()) {
    return """
          ${def} {
            return ${clsName}Object.${this.name}(${callStringList.joinToString(", ")})
          }
      """
      .trimIndent()
  } else {
    return """
          ${def} {
            return instance${clsName}.${this.name}(${callStringList.joinToString(", ")})
          }
      """
      .trimIndent()
  }
}

fun FieldNode.transformToKotlin(clsName: String): String {
  val descriptor = AccessDescriptor(this.access)
  val type = Type.getType(this.desc).className
  if (descriptor.isStatic()) {
    return "val ${this.name}: ${type} = ${clsName}Object.${this.name}"
  } else {
    return "val ${this.name}: ${type} = instance${clsName}.${this.name}"
  }
}

fun ClassInfo.transformToKotlin(): String {
  val classDescriptor = AccessDescriptor(this.node.access)
  val classType = if (classDescriptor.isInterface()) "interface" else "class"
  val className = this.name.substringAfterLast("/").replace("$", ".")
  val varClassName = className.replace("$", "")
  val staticCode =
    this.fields
      .filter { (key, value) ->
        val accessDescriptor = AccessDescriptor(value.access)
        !hasNonPublicFieldSignature(value) &&
          !accessDescriptor.isPrivate() &&
          accessDescriptor.isStatic()
      }
      .map { (key, field) -> field.transformToKotlin(varClassName) }
      .plus(
        this.methods
          .filter { (key, value) ->
            val accessDescriptor = AccessDescriptor(value.access)
            !hasNonPublicMethodSignature(value) &&
              !accessDescriptor.isPrivate() &&
              accessDescriptor.isStatic()
          }
          .map { (key, field) -> field.transformToKotlin(varClassName) }
      )
  val nonStaticCode =
    this.fields
      .filter { (key, value) ->
        val accessDescriptor = AccessDescriptor(value.access)
        !hasNonPublicFieldSignature(value) &&
          !accessDescriptor.isPrivate() &&
          !accessDescriptor.isStatic()
      }
      .map { (key, field) -> field.transformToKotlin(varClassName) }
      .plus(
        this.methods
          .filter { (key, value) ->
            val accessDescriptor = AccessDescriptor(value.access)
            !hasNonPublicMethodSignature(value) &&
              !accessDescriptor.isPrivate() &&
              !accessDescriptor.isStatic()
          }
          .map { (key, field) -> field.transformToKotlin(varClassName) }
      )
  return """
    ${classType} ${className} (val instance${varClassName}: Java${varClassName} ) {
      ${if(staticCode.isNotEmpty()) "companion object {\n ${staticCode.joinToString("\n")}}" else ""}
      ${nonStaticCode.joinToString("\n")}
    }
  """
    .trimIndent()
}

abstract class KotlinTransform : DefaultTask() {
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>
  @get:Input abstract val currentJar: Property<String>
  @get:Input abstract val projectPath: Property<String>

  @TaskAction
  fun run() {
    val classesJar = UtilityClass.readApi(currentJar.get())
    val allValidClasses =
      classesJar.keys
        .filter { isValidClass(it) }
        .map { arrayOf(it.split("$").get(0), it) }
        .groupBy({ it[0] }, { it[1] })
        .filter { AccessDescriptor(classesJar.get(it.key)!!.node.access).isPublic() }

    allValidClasses.forEach { (key, value) ->
      val packageName = key.substringBeforeLast("/").replace("/", ".")
      val importList =
        value.map { x ->
          val pkgName = x.substringBeforeLast("/").replace("/", ".")
          val clsName = x.substringAfterLast("/").replace("$", ".")
          "import ${pkgName}.java.${clsName} as Java${clsName.replace(".","")}"
        }
      val typedefList =
        value.map { x ->
          val pkgName = x.substringBeforeLast("/").replace("/", ".")
          val clsName = x.substringAfterLast("/").replace("$", "")
          "typealias ${clsName}Object = ${pkgName}.java.${clsName}"
        }
      val classContent =
        """
          package ${packageName}
          ${importList.joinToString("\n")}
          ${typedefList.joinToString("\n")}
          ${classesJar.get(key)!!.transformToKotlin()}
      """
          .trimIndent()
      println(classContent)
      return

      //      val classInfo: ClassInfo = classesJar.get(it)!!

    }

    //   transformJavaFiles()
  }

  private fun isValidClass(className: String): Boolean {
    val modifiedClass = className.split("$").get(0)
    val javaPath = "${projectPath.get()}/src/main/java/${modifiedClass}.java"
    return File(javaPath).exists()
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
