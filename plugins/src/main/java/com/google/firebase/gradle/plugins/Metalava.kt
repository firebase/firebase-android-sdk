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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.dependencies

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

fun Project.runMetalavaWithArgs(
  arguments: List<String>,
  ignoreFailure: Boolean = false,
  stdOut: OutputStream? = null,
) {
  val allArgs =
    listOf(
      "--no-banner",
      "--hide",
      "HiddenSuperclass", // We allow having a hidden parent class
      "--hide",
      "HiddenAbstractMethod",
    ) + arguments

  project.javaexec {
    mainClass.set("com.android.tools.metalava.Driver")
    classpath = project.metalavaConfig
    args = allArgs
    isIgnoreExitValue = ignoreFailure
    if (stdOut != null) errorOutput = stdOut
  }
}

// TODO(): Migrate away from `project` usage within the task scope (same for the tasks below)
abstract class GenerateStubsTask : DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: ConfigurableFileCollection

  @get:[InputFiles Classpath]
  abstract val classPath: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDir: RegularFileProperty

  @TaskAction
  fun run() {
    val sourcePath =
      sources.files.filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath }

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    val args =
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(File.pathSeparator),
        "--include-annotations",
        "--doc-stubs",
        outputDir.get().asFile.absolutePath,
      )

    logger.info("Running metalava with args:\n ${args.joinToString("\n")}")

    project.runMetalavaWithArgs(args)
  }
}

abstract class GenerateApiTxtTask : DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: ConfigurableFileCollection

  @get:[InputFiles Classpath]
  abstract val classPath: ConfigurableFileCollection

  @get:OutputFile abstract val apiTxtFile: RegularFileProperty

  @get:OutputFile abstract val baselineFile: RegularFileProperty

  @get:Input abstract val updateBaseline: Property<Boolean>

  @TaskAction
  fun run() {
    val sourcePath =
      sources.files.filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath }

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()

    println(
      """
      Project: ${project.name}
      
      Classpath: ${classPath.joinToString("\n")}
    """
        .trimIndent()
    )
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    project.runMetalavaWithArgs(
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(File.pathSeparator),
        "--api",
        apiTxtFile.get().asFile.absolutePath,
        "--format=v2",
      ) +
        if (updateBaseline.get()) listOf("--update-baseline")
        else if (baselineFile.get().asFile.exists())
          listOf("--baseline", baselineFile.get().asFile.absolutePath)
        else listOf(),
      ignoreFailure = true,
    )
  }
}

abstract class ApiInformationTask : DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: ConfigurableFileCollection

  @get:[InputFiles Classpath]
  abstract val classPath: ConfigurableFileCollection

  @get:InputFile abstract val apiTxtFile: RegularFileProperty

  @get:OutputFile abstract val outputApiFile: RegularFileProperty

  @get:OutputFile abstract val baselineFile: RegularFileProperty

  @get:Input abstract val updateBaseline: Property<Boolean>

  @TaskAction
  fun run() {
    val sourcePath =
      sources.files.filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath }

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    project.runMetalavaWithArgs(
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(File.pathSeparator),
        "--api",
        outputApiFile.get().asFile.absolutePath,
        "--format=v2",
      ),
      ignoreFailure = true,
    )

    val outputFilePath = outputApiFile.get().asFile.absolutePath.remove("_api.txt")

    project.runMetalavaWithArgs(
      listOf(
        "--source-files",
        outputApiFile.get().asFile.absolutePath,
        "--check-compatibility:api:released",
        apiTxtFile.get().asFile.absolutePath,
        "--error",
        "AddedClass",
        "--error",
        "AddedMethod",
        "--error",
        "AddedField",
        "--format=v2",
        "--no-color",
      ) +
        if (updateBaseline.get()) listOf("--update-baseline")
        else if (baselineFile.get().asFile.exists())
          listOf("--baseline", baselineFile.get().asFile.absolutePath)
        else listOf(),
      ignoreFailure = true,
      stdOut = FileOutputStream(outputFilePath),
    )
  }
}
