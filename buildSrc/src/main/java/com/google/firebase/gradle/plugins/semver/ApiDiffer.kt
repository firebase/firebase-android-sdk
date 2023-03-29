package com.google.firebase.gradle.plugins.semver

import com.google.firebase.gradle.plugins.GmavenHelper
import com.google.firebase.gradle.plugins.UnzipAar
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

abstract class ApiDiffer : DefaultTask() {
  @get:Input abstract val aarPath: Property<File>

  @get:Input abstract val artifactId: Property<String>

  @get:Input abstract val groupId: Property<String>

  private val CLASS_EXTENSION = ".class"

  @TaskAction
  fun run() {
    val gMavenHelper = GmavenHelper(groupId.get(), artifactId.get())
    val lastAarPath = gMavenHelper.getAarFileForVersion(gMavenHelper.getLatestReleasedVersion())
    val previousAarPath: String = aarPath.get().absolutePath.replace(".aar", "/old.aar")
    try {
      BufferedInputStream(URL(lastAarPath).openStream()).use { `in` ->
        FileOutputStream(previousAarPath).use { fileOutputStream ->
          val dataBuffer = ByteArray(1024)
          var bytesRead: Int
          while (`in`.read(dataBuffer, 0, 1024).also { bytesRead = it } != -1) {
            fileOutputStream.write(dataBuffer, 0, bytesRead)
          }
        }
      }
    } catch (e: IOException) {
      // handle exception
    }
    val currentAarClassJar = aarPath.get().absolutePath.replace(".aar", "")
    val previousAarClassJar = previousAarPath.replace(".aar", "")
    val afterJar = readApi(aarPath.get(), currentAarClassJar)
    val beforeJar = readApi(File(previousAarPath), previousAarClassJar)
    val apiDeltas = mutableListOf<Delta>()
    val classKeys = afterJar.keys union beforeJar.keys
    classKeys.forEach {
      val afterClass = afterJar.get(it)
      val beforeClass = beforeJar.get(it)
      val deltaType = DeltaType.ADDED_FIELD
      apiDeltas.addAll(deltaType.getViolations(beforeClass, afterClass))
    }
    print(apiDeltas)
  }

  fun readApi(aarPath: File, destFile: String): Map<String, ClassInfo> {
    UnzipAar.unzip(aarPath, destFile)
    val classes: Map<String, ClassNode> = readClassNodes(Paths.get("${destFile}/classes.jar"))
    var classInfoDict = mutableMapOf<String, ClassInfo>()
    for (classNodeInfo in classes) {
      classInfoDict[classNodeInfo.key] = ClassInfo(classNodeInfo.value, classes)
    }
    for (classNodeInfo in classes) {
      classInfoDict[classNodeInfo.key] = ClassInfo(classNodeInfo.value, classes)
    }
    return classInfoDict
  }

  /** Returns true if the class is local or anonymous. */
  fun isLocalOrAnonymous(classNode: ClassNode): Boolean {
    // JVMS 4.7.7 says a class has an EnclosingMethod attribute iff it is local or anonymous.
    // ASM sets the "enclosing class" only if an EnclosingMethod attribute is present, so this
    // this test will not include named inner classes even though they have enclosing classes.
    return classNode.outerClass != null
  }

  fun getUnqualifiedClassname(classNodeName: String): String {
    // Class names may be "/" or "." separated depending on context. Normalize to "/"
    val normalizedPath = classNodeName.replace(".", "/")
    val withoutPackage = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1)
    return withoutPackage.substring(withoutPackage.lastIndexOf('$') + 1)
  }

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

  fun hasNonPublicSignature(fieldNode: FieldNode): Boolean {
    val fieldType = Type.getType(fieldNode.desc)
    return UtilityClass.isObfuscatedSymbol(getUnqualifiedClassname(fieldType.getClassName()))
  }

  fun hasNonPublicSignature(methodNode: MethodNode): Boolean {
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

  fun getRemovedApi(before: Map<String, ClassInfo>, after: Map<String, ClassInfo>): List<Delta> {
    var apiDeltas = mutableListOf<Delta>()
    for (entry in before) {
      val className = entry.key
      val classInfo = entry.value
      val classAccess = AccessDescriptor(classInfo.node.access)
      if (!after.keys.contains(className) && !classAccess.isPrivate()) {
        apiDeltas.add(
          Delta(
            className,
            "",
            String.format("Class %s removed.", className),
            DeltaType.REMOVED_CLASS,
            VersionDelta.MAJOR
          )
        )
        continue
      }
      for (method in classInfo.methods) {
        val beforeMethod = method.value
        val methodName = beforeMethod.name
        if (hasNonPublicSignature(beforeMethod)) {
          continue
        }
        val beforeMethodAccess = AccessDescriptor(beforeMethod.access)
        if (
          !beforeMethodAccess.isPrivate() && !after[className]!!.methods.containsKey(method.key)
        ) {
          apiDeltas.add(
            Delta(
              className,
              methodName,
              String.format(
                "Method %s on class %s was removed.",
                getMethodSignature(beforeMethod),
                className
              ),
              DeltaType.REMOVED_METHOD,
              VersionDelta.MAJOR
            )
          )
        }
      }
      for (field in classInfo.fields) {
        val beforeField = field.value
        val fieldName = beforeField.name
        val beforeFieldAccess = AccessDescriptor(beforeField.access)
        if (!beforeFieldAccess.isPrivate() && !after[className]!!.fields.containsKey(field.key)) {
          apiDeltas.add(
            Delta(
              className,
              fieldName,
              String.format("Field %s on class %s was removed.", fieldName, className),
              DeltaType.REMOVED_FIELD,
              VersionDelta.MAJOR,
            )
          )
        }
      }
    }
    return apiDeltas
  }

  fun getFieldDelta(className: String, beforeField: FieldNode?, afterField: FieldNode): Delta? {
    if (hasNonPublicSignature(afterField)) {
      return null
    }
    val afterFieldAccess = AccessDescriptor(afterField.access)
    if (beforeField == null) {
      return if (!afterFieldAccess.isPrivate()) {
        Delta(
          className,
          afterField.name,
          String.format("Field %s on class %s has been added.", afterField.name, className),
          DeltaType.ADDED_FIELD,
          VersionDelta.MINOR
        )
      } else null
    }
    val beforeFieldAccess = AccessDescriptor(beforeField.access)
    if (afterFieldAccess.isPrivate() && !beforeFieldAccess.isPrivate()) {
      return Delta(
        className,
        afterField.name,
        String.format("Field %s on class %s became private.", afterField.name, className),
        DeltaType.REDUCED_VISIBILITY,
        VersionDelta.MAJOR
      )
    }
    if (afterFieldAccess.isProtected() && beforeFieldAccess.isPublic()) {
      return Delta(
        className,
        afterField.name,
        String.format(
          "Field %s on class %s became protected from public.",
          afterField.name,
          className
        ),
        DeltaType.REDUCED_VISIBILITY,
        VersionDelta.MAJOR
      )
    }
    if (afterFieldAccess.isProtected() && beforeFieldAccess.isPrivate()) {
      return Delta(
        className,
        afterField.name,
        String.format(
          "Field %s on class %s became protected from private.",
          afterField.name,
          className
        ),
        DeltaType.INCREASED_VISIBILITY,
        VersionDelta.MINOR
      )
    }
    return if (afterFieldAccess.isPublic() && !beforeFieldAccess.isPublic()) {
      Delta(
        className,
        afterField.name,
        String.format("Field %s on class %s became public.", afterField.name, className),
        DeltaType.INCREASED_VISIBILITY,
        VersionDelta.MINOR
      )
    } else null
  }

  fun getMethodDeltas(
    className: String,
    beforeMethod: MethodNode?,
    afterMethod: MethodNode
  ): List<Delta> {
    val afterMethodAccess = AccessDescriptor(afterMethod.access)
    var apiDeltas = mutableListOf<Delta>()

    if (hasNonPublicSignature(afterMethod)) {
      return apiDeltas
    }

    if (beforeMethod == null) {
      if (!afterMethodAccess.isPrivate()) {
        apiDeltas.add(
          Delta(
            className,
            afterMethod.name,
            String.format(
              "Method %s on class %s has been added.",
              getMethodSignature(afterMethod),
              className
            ),
            DeltaType.ADDED_METHOD,
            VersionDelta.MINOR
          )
        )
      }
      return apiDeltas
    }

    val beforeMethodAccess = AccessDescriptor(beforeMethod.access)

    if (afterMethodAccess.isStatic() && !beforeMethodAccess.isStatic()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s became static.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.CHANGED_TO_STATIC,
          VersionDelta.MAJOR
        )
      )
    } else if (!afterMethodAccess.isStatic() && beforeMethodAccess.isStatic()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s removed static.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.CHANGED_FROM_STATIC,
          VersionDelta.MINOR
        )
      )
    }
    if (afterMethodAccess.isPrivate() && !beforeMethodAccess.isPrivate()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s became private.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.REDUCED_VISIBILITY,
          VersionDelta.MAJOR
        )
      )
    } else if (afterMethodAccess.isProtected() && beforeMethodAccess.isPublic()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s became protected from public.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.REDUCED_VISIBILITY,
          VersionDelta.MAJOR
        )
      )
    } else if (afterMethodAccess.isProtected() && beforeMethodAccess.isPrivate()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s became protected from private.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.INCREASED_VISIBILITY,
          VersionDelta.MINOR
        )
      )
    } else if (afterMethodAccess.isPublic() && !beforeMethodAccess.isPublic()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s became public.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.INCREASED_VISIBILITY,
          VersionDelta.MINOR
        )
      )
    } else if (afterMethodAccess.isAbstract() && !beforeMethodAccess.isAbstract()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s became abstract.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.METHOD_MADE_ABSTRACT,
          VersionDelta.MAJOR
        )
      )
    } else if (afterMethodAccess.isFinal() && !beforeMethodAccess.isFinal()) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s became final.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.METHOD_MADE_FINAL,
          VersionDelta.MAJOR
        )
      )
    }

    // Check to see if the method can throw new types of exceptions.
    if (!beforeMethod.exceptions.containsAll(afterMethod.exceptions)) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s throws new exceptions.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.NEW_EXCEPTIONS,
          VersionDelta.MAJOR
        )
      )
    }
    if (!afterMethod.exceptions.containsAll(beforeMethod.exceptions)) {
      apiDeltas.add(
        Delta(
          className,
          afterMethod.name,
          String.format(
            "Method %s on class %s throws fewer exceptions.",
            getMethodSignature(afterMethod),
            className
          ),
          DeltaType.DELETE_EXCEPTIONS,
          VersionDelta.MAJOR
        )
      )
    }

    return apiDeltas
  }

  fun getClassDelta(beforeClass: ClassInfo?, afterClass: ClassInfo): Delta? {
    val afterClassAccess = AccessDescriptor(afterClass.node.access)
    val className = afterClass.name

    if (beforeClass == null) {
      if (!afterClassAccess.isPrivate()) {
        return Delta(
          className,
          "",
          String.format("Class %s was added.", className),
          DeltaType.ADDED_CLASS,
          VersionDelta.MINOR
        )
      }
      return null
    }

    // Check for visibility changes.
    val beforeClassAccess = AccessDescriptor(beforeClass.node.access)
    if (afterClassAccess.isPrivate() && !beforeClassAccess.isPrivate()) {
      return Delta(
        className,
        "",
        String.format("Class %s became private.", className),
        DeltaType.REDUCED_VISIBILITY,
        VersionDelta.MAJOR
      )
    }
    if (afterClassAccess.isProtected() && beforeClassAccess.isPublic()) {
      return Delta(
        className,
        "",
        String.format("Class %s became protected from public.", className),
        DeltaType.REDUCED_VISIBILITY,
        VersionDelta.MAJOR
      )
    }
    if (afterClassAccess.isPublic() && !beforeClassAccess.isPublic()) {
      return Delta(
        className,
        "",
        String.format("Class %s became public.", className),
        DeltaType.INCREASED_VISIBILITY,
        VersionDelta.MINOR
      )
    }
    if (afterClassAccess.isProtected() && beforeClassAccess.isPrivate()) {
      return Delta(
        className,
        "",
        String.format("Class %s became protected from private.", className),
        DeltaType.INCREASED_VISIBILITY,
        VersionDelta.MINOR
      )
    }
    if (afterClassAccess.isAbstract() && !beforeClassAccess.isAbstract()) {
      return Delta(
        className,
        "",
        String.format("Class %s became abstract.", className),
        DeltaType.CLASS_MADE_ABSTRACT,
        VersionDelta.MAJOR
      )
    }
    if (afterClassAccess.isFinal() && !beforeClassAccess.isFinal()) {
      return Delta(
        className,
        "",
        String.format("Class %s became final.", className),
        DeltaType.CLASS_MADE_FINAL,
        VersionDelta.MAJOR
      )
    }
    return null
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
        println("Duplicate class seen: " + classNode.name)
      }
    }
    return classes
  }
}
