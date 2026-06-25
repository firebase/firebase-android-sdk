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
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

val Project.metalavaConfig: Configuration
  get() =
    configurations.findByName("metalavaArtifacts")
      ?: configurations.create("metalavaArtifacts") {
        this.dependencies.add(
          this@metalavaConfig.dependencies.create(
            "com.android.tools.metalava:metalava:1.0.0-alpha11"
          )
        )
      }

val Project.docStubs: File?
  get() = project.file("${project.layout.buildDirectory.get().asFile.path}/doc-stubs")

fun Project.runMetalavaWithArgs(
  execOperations: ExecOperations,
  arguments: List<String>,
  ignoreFailure: Boolean = false,
  stdOut: OutputStream? = null,
) {
  val allArgs =
    listOf(
      "--hide",
      "HiddenSuperclass", // We allow having a hidden parent class
      "--hide",
      "HiddenAbstractMethod",
    ) + arguments

  execOperations.javaexec {
    mainClass.set("com.android.tools.metalava.Driver")
    classpath = metalavaConfig
    args = allArgs
    isIgnoreExitValue = ignoreFailure
    if (stdOut != null) errorOutput = stdOut
  }
}

abstract class GenerateStubsTask @Inject constructor(private val execOperations: ExecOperations) :
  DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: ConfigurableFileCollection

  @get:[InputFiles Classpath]
  lateinit var classPath: FileCollection

  @get:OutputDirectory
  val outputDir: File = File(project.layout.buildDirectory.get().asFile, "doc-stubs")

  @TaskAction
  fun run() {
    val sourcePath = sources.files.filter { it.exists() }.map { it.absolutePath }.joinToString(":")

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    project.runMetalavaWithArgs(
      execOperations,
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(":"),
        "--include-annotations",
        "--doc-stubs",
        outputDir.absolutePath,
      ),
    )
  }
}

abstract class GenerateApiTxtTask @Inject constructor(private val execOperations: ExecOperations) :
  DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: ConfigurableFileCollection

  @get:InputFiles lateinit var classPath: FileCollection

  @get:OutputFile abstract val apiTxtFile: RegularFileProperty

  @get:OutputFile abstract val baselineFile: RegularFileProperty

  @get:org.gradle.api.tasks.Optional
  @get:org.gradle.api.tasks.OutputDirectory
  val agentDocsDir: File?
    get() =
      if (project.name == "firebase-firestore")
        project.file("${project.layout.buildDirectory.get().asFile}/agent-docs")
      else null

  @get:Input abstract val updateBaseline: Property<Boolean>

  @TaskAction
  fun run() {
    val sourcePath = sources.files.filter { it.exists() }.map { it.absolutePath }.joinToString(":")

    val classPathList = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPathList += listOf(it.absolutePath) }

    if (project.name == "firebase-firestore") {
      val compiledDebug = project.file("build/tmp/kotlin-classes/debug")
      val compiledRelease = project.file("build/tmp/kotlin-classes/release")
      if (compiledDebug.exists()) classPathList += listOf(compiledDebug.absolutePath)
      if (compiledRelease.exists()) classPathList += listOf(compiledRelease.absolutePath)
    }

    project.runMetalavaWithArgs(
      execOperations,
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPathList.joinToString(":"),
        "--api",
        apiTxtFile.get().asFile.absolutePath,
        "--format=v3",
      ) +
        if (updateBaseline.get()) listOf("--update-baseline")
        else if (baselineFile.get().asFile.exists())
          listOf("--baseline", baselineFile.get().asFile.absolutePath)
        else listOf(),
      ignoreFailure = true,
    )

    if (project.name == "firebase-firestore") {
      val classPathString = classPathList.joinToString(":")
      val fsSourcePath = project.file("src/main/java").absolutePath

      generateAgentDocs(
        targetFiles =
          listOf(project.file("src/main/java/com/google/firebase/firestore/Pipeline.kt")),
        stubDirName = "doc-stubs-pipeline",
        outputFileName = "pipeline.docs.txt",
        classPathString = classPathString,
        sourcePath = fsSourcePath,
      )

      generateAgentDocs(
        targetFiles =
          listOf(
            project.file("src/main/java/com/google/firebase/firestore/pipeline/expressions.kt"),
            project.file("src/main/java/com/google/firebase/firestore/pipeline/aggregates.kt"),
          ),
        stubDirName = "doc-stubs-expressions",
        outputFileName = "expressions.docs.txt",
        classPathString = classPathString,
        sourcePath = fsSourcePath,
      )
    }
  }

  private fun generateAgentDocs(
    targetFiles: List<File>,
    stubDirName: String,
    outputFileName: String,
    classPathString: String,
    sourcePath: String,
  ) {
    val docStubsDir = project.file("${project.layout.buildDirectory.get().asFile}/$stubDirName")
    docStubsDir.deleteRecursively()

    project.runMetalavaWithArgs(
      execOperations,
      listOf(
        "--source-path",
        sourcePath,
        "--source-files",
        targetFiles.joinToString(",") { it.absolutePath },
        "--classpath",
        classPathString,
        "--doc-stubs",
        docStubsDir.absolutePath,
        "--format=v3",
      ),
      ignoreFailure = false,
    )

    val agentDocsFile =
      project.file("${project.layout.buildDirectory.get().asFile}/agent-docs/$outputFileName")
    project.file("${project.layout.buildDirectory.get().asFile}/agent-docs").mkdirs()
    val content = buildString {
      docStubsDir
        .walkTopDown()
        .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
        .sortedBy { it.name }
        .forEach { f ->
          append("// File: ${f.name}\n")
          append(f.readText())
          append("\n\n")
        }
    }
    agentDocsFile.writeText(content)
  }
}

abstract class ApiInformationTask @Inject constructor(private val execOperations: ExecOperations) :
  DefaultTask() {
  /** Source files against which API signatures will be validated. */
  @get:InputFiles abstract val sources: ConfigurableFileCollection

  @get:InputFiles lateinit var classPath: FileCollection

  @get:InputFile abstract val apiTxtFile: RegularFileProperty

  @get:OutputFile abstract val outputApiFile: RegularFileProperty

  @get:OutputFile abstract val baselineFile: RegularFileProperty

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @get:Input abstract val updateBaseline: Property<Boolean>

  @TaskAction
  fun run() {
    val sourcePath = sources.files.filter { it.exists() }.map { it.absolutePath }.joinToString(":")

    val classPath = classPath.files.asSequence().map { it.absolutePath }.toMutableList()
    project.androidJar?.let { classPath += listOf(it.absolutePath) }

    project.runMetalavaWithArgs(
      execOperations,
      listOf(
        "--source-path",
        sourcePath,
        "--classpath",
        classPath.joinToString(":"),
        "--api",
        outputApiFile.get().asFile.absolutePath,
        "--format=v3",
      ),
      ignoreFailure = true,
    )

    project.runMetalavaWithArgs(
      execOperations,
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
        "--format=v3",
        "--no-color",
      ) +
        if (updateBaseline.get()) listOf("--update-baseline")
        else if (baselineFile.get().asFile.exists())
          listOf("--baseline", baselineFile.get().asFile.absolutePath)
        else listOf(),
      ignoreFailure = true,
      stdOut = FileOutputStream(outputFile.get().asFile),
    )
  }
}
