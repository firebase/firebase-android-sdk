package com.google.firebase.gradle.plugins.semver

import com.google.firebase.gradle.plugins.GmavenHelper
import com.google.firebase.gradle.plugins.UnzipAar
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ApiDiffer : DefaultTask() {
  @get:Input abstract val aarPath: Property<File>

  @get:Input abstract val artifactId: Property<String>

  @get:Input abstract val groupId: Property<String>

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
    readApi(aarPath.get(), currentAarClassJar)
    readApi(File(previousAarPath), previousAarClassJar)
  }

  fun readApi(aarPath: File, destFile: String) {
    UnzipAar.unzip(aarPath, destFile)
    val classes: Map<String, ClassNode> = readClassNodes(destFile)
    val closure = MemberClosure(classes)
    val builder: ImmutableMap.Builder<String, ClassInfo> = ImmutableMap.builder()
    for (classNode in classes.values) {
      builder.put(
        classNode.name,
        ClassInfo.create(
          classNode.name,
          classNode,
          getAllFields(classNode),
          getAllMethods(closure, classNode)
        )
      )
    }
    return builder.buildOrThrow()
  }
}
