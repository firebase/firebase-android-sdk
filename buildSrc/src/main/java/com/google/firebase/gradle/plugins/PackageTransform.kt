package com.google.firebase.gradle.plugins

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class PackageTransform : DefaultTask() {
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>
  @get:Input abstract val projectPath: Property<String>

  fun copyDir(src: Path, dest: Path) {
    Files.walk(src).forEach {
      if (!Files.isDirectory(it)) {
        println(it)
        println(dest.resolve(src.relativize(it)))
      }
    }
  }

  @TaskAction
  fun run() {
    var packageNamePath = "${groupId.get()}.${artifactId.get().split("-")[1]}".replace(".", "/")
    packageNamePath = packageNamePath.replace("common", "")
    val ktxArtifactPath = "${projectPath.get()}/ktx/src/main/kotlin/${packageNamePath}/ktx"
    val ktxPackagePath = "${projectPath.get()}/src/main/java/${packageNamePath}/ktx"
    val mainPackagePath = "${projectPath.get()}/src/main/java/${packageNamePath}"
    copyDir(File(ktxArtifactPath).toPath(), File(ktxPackagePath).toPath())
    copyDir(File(ktxArtifactPath).toPath(), File(mainPackagePath).toPath())
  }
}
