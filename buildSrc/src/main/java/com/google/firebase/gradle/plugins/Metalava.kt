/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.gradle.plugins

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

val Project.metalavaConfig: Configuration
  get() =
    configurations.findByName("metalavaArtifacts")
      ?: configurations.create("metalavaArtifacts") {
        this.dependencies.add(
          this@metalavaConfig.dependencies.create(
            "com.android.tools.metalava:metalava:1.0.0-alpha06"
          )
        )
      }

val Project.docStubs: File?
  get() = project.file("${buildDir.path}/doc-stubs")

fun Project.runMetalavaWithArgs(
  arguments: List<String>,
  ignoreFailure: Boolean = false,
  stdOut: OutputStream? = null
) {
  val allArgs =
    listOf(
      "--no-banner",
      "--hide",
      "HiddenSuperclass", // We allow having a hidden parent class
      "--hide",
      "HiddenAbstractMethod"
    ) + arguments

  project.javaexec {
    mainClass.set("com.android.tools.metalava.Driver")
    classpath = project.metalavaConfig
    args = allArgs
    isIgnoreExitValue = ignoreFailure
    if (stdOut != null) errorOutput = stdOut
  }
}

abstract class GenerateStubsTask : DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: SetProperty<File>

  @get:[InputFiles Classpath]
  lateinit var classPath: FileCollection

  @get:OutputDirectory val outputDir: File = File(project.buildDir, "doc-stubs")

  @TaskAction
  fun run() {
    val sourcePath =
      sources.get().asSequence().filter { it.exists() }.map { it.absolutePath }.joinToString(":")

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    project.runMetalavaWithArgs(
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(":"),
        "--include-annotations",
        "--doc-stubs",
        outputDir.absolutePath
      )
    )
  }
}

abstract class GenerateApiTxtTask : DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: SetProperty<File>

  @get:InputFiles lateinit var classPath: FileCollection

  @get:OutputFile abstract val apiTxtFile: Property<File>

  @get:OutputFile abstract val baselineFile: Property<File>

  @get:Input abstract val updateBaseline: Property<Boolean>

  @TaskAction
  fun run() {
    val sourcePath =
      sources.get().asSequence().filter { it.exists() }.map { it.absolutePath }.joinToString(":")

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    project.runMetalavaWithArgs(
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(":"),
        "--api",
        apiTxtFile.get().absolutePath,
        "--format=v2"
      ) +
        if (updateBaseline.get()) listOf("--update-baseline")
        else if (baselineFile.get().exists()) listOf("--baseline", baselineFile.get().absolutePath)
        else listOf(),
      ignoreFailure = true
    )
  }
}

abstract class ApiInformationTask : DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: SetProperty<File>

  @get:InputFiles lateinit var classPath: FileCollection

  @get:InputFile abstract val apiTxtFile: Property<File>

  @get:OutputFile abstract val outputApiFile: Property<File>

  @get:OutputFile abstract val baselineFile: Property<File>

  @get:OutputFile abstract val outputFile: Property<File>

  @get:Input abstract val updateBaseline: Property<Boolean>

  @TaskAction
  fun run() {
    val sourcePath =
      sources.get().asSequence().filter { it.exists() }.map { it.absolutePath }.joinToString(":")

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    project.runMetalavaWithArgs(
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(":"),
        "--api",
        outputApiFile.get().absolutePath,
        "--format=v2"
      ),
      ignoreFailure = true
    )

    project.runMetalavaWithArgs(
      listOf(
        "--source-files",
        outputApiFile.get().absolutePath,
        "--check-compatibility:api:released",
        apiTxtFile.get().absolutePath,
        "--error",
        "AddedClass",
        "--error",
        "AddedMethod",
        "--error",
        "AddedField",
        "--format=v2",
        "--no-color"
      ) +
        if (updateBaseline.get()) listOf("--update-baseline")
        else if (baselineFile.get().exists()) listOf("--baseline", baselineFile.get().absolutePath)
        else listOf(),
      ignoreFailure = true,
      stdOut = FileOutputStream(outputFile.get())
    )
  }
}
