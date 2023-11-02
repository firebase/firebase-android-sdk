package com.google.firebase.gradle.plugins.semver

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

object UtilityClass {
  private val CLASS_EXTENSION = ".class"
  fun isObfuscatedSymbol(symbol: String): Boolean {
    val normalizedSymbol = symbol.toLowerCase(Locale.ROOT).replace("\\[\\]", "")
    return normalizedSymbol.startsWith("zz") || normalizedSymbol.startsWith("za")
  }

  fun readApi(jarPath: String): Map<String, ClassInfo> {
    val classes: Map<String, ClassNode> = readClassNodes(Paths.get(jarPath))
    return classes.entries.associate { (key, value) -> key to ClassInfo(value, classes) }
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
        print("Classnode doesn't match expected name ${classNode.name}. This is not a valid jar.")
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
        print("Duplicate class seen: ${classNode.name}")
      }
    }
    return classes
  }
}
