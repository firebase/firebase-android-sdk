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
import org.objectweb.asm.tree.ClassNode

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
    apiDeltas.forEach {
      print(it.deltaType)
      print(it.description)
    }
  }

  private fun readApi(aarPath: File, destFile: String): Map<String, ClassInfo> {
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
        println("Duplicate class seen: " + classNode.name)
      }
    }
    return classes
  }
}
