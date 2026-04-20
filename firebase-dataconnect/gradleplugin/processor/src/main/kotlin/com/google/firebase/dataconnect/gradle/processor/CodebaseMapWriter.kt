package com.google.firebase.dataconnect.gradle.processor

import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets

fun writeCodebaseMap(outputFile: File, classMapInfoList: List<ClassMapInfo>) {
    outputFile.parentFile?.mkdirs() // Ensure directory exists just in case
    outputFile.writer(StandardCharsets.UTF_8).use { writer ->
      writer.writeCodebaseMap(classMapInfoList)
    }
}

fun Writer.writeCodebaseMap(classMapInfoList: List<ClassMapInfo>) {
  write("# Codebase Map\n")

  write("\n")
  write("## Notes\n")
  write(
    "* Files listed without explicit class bullets contain a single declaration " +
        "with the same name as the file (minus the .kt extension)\n")
  write("* Extensions are prefixed with their `Receiver.`\n")

  classMapInfoList.groupBy { it.packageName }.toSortedMap().entries.forEach{ (packageName, classes) ->
    write("\n")
    writePackage(packageName, classes)
  }
}

private fun Writer.writePackage(packageName: String, classes: List<ClassMapInfo>) {
  write("## ${packageName.ifEmpty { "<root>" }}\n")
  classes.groupBy { it.directory }.toSortedMap().entries.forEach { (directory, classesInDir) ->
    writeDirectory(directory, classesInDir)
  }
}

private fun Writer.writeDirectory(directory: String, classes: List<ClassMapInfo>) {
  write("* ${directory.ifEmpty { "./" }}\n")
  classes.groupBy { it.fileName }.toSortedMap().entries.forEach { (fileName, classesInFile) ->
    writeFile(fileName, classesInFile)
  }
}

private fun Writer.writeFile(file: String, classes: List<ClassMapInfo>) {
  val simpleFileName = file.removeSuffix(".kt")

  // Check if the file contains exactly one class AND that class matches the file name.
  // This is the most common case and is highly compressible.
  if (classes.size == 1 && classes[0].className == simpleFileName) {
      write("  * $file\n")
  } else {
      write("  * $file\n")
      val tree = buildClassTree(classes)
      writeClassTree(tree, 2)
  }
}

private fun Writer.writeClassTree(node: TreeNode, indentLevel: Int) {
    node.children.values.sortedBy { it.name }.forEach { child ->
        val indent = "  ".repeat(indentLevel)
        write("$indent* ${child.name}\n")
        writeClassTree(child, indentLevel + 1)
    }
}

    class TreeNode(val name: String) {
        val children = mutableMapOf<String, TreeNode>()
    }
    
    private fun buildClassTree(classes: List<ClassMapInfo>): TreeNode {
        val root = TreeNode("")
        for (info in classes) {
            var current = root
            for (part in info.classParts) {
                current = current.children.getOrPut(part) { TreeNode(part) }
            }
        }
        return root
    }
    
