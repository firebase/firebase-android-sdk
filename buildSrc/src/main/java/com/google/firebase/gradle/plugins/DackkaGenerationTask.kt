/*
 * Copyright 2022 Google LLC
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
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.json.JSONObject

/**
 * Extension class for [GenerateDocumentationTask].
 *
 * Provides public configurations for the task.
 *
 * @property dackkaJarFile a [File] of the Dackka fat jar
 * @property dependencies a list of all dependent jars (the classpath)
 * @property sources a list of source roots
 * @property suppressedFiles a list of files to exclude from documentation
 * @property packageListFiles a list of files that define external package-lists for links
 * @property clientName the name of the module
 * @property outputDirectory where to store the generated files
 */
@CacheableTask
abstract class GenerateDocumentationTaskExtension : DefaultTask() {
  @get:[InputFile Classpath]
  abstract val dackkaJarFile: Property<File>

  @get:[InputFiles Classpath]
  abstract val dependencies: Property<FileCollection>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ListProperty<File>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val suppressedFiles: ListProperty<File>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val packageListFiles: ListProperty<File>

  @get:Input abstract val clientName: Property<String>

  @get:OutputDirectory abstract val outputDirectory: Property<File>
}

/**
 * Wrapper data class for External package-lists in Dokka
 *
 * This class allows us to map package-lists in a type-safe way, versus inline straight to a map.
 * This extra step could be removed- but it could also catch bugs in the future.
 *
 * @property packageList the prepared package-list file to map against
 * @property externalLink the url to map with when generating the docs
 */
data class ExternalDocumentationLink(val packageList: File, val externalLink: String)

/**
 * Task to run Dackka on a project.
 *
 * Since dackka needs to be run on the command line, we have to organize the arguments for dackka
 * into a json file. We then pass that json file to dackka as an argument.
 *
 * @see GenerateDocumentationTaskExtension
 */
abstract class GenerateDocumentationTask
@Inject
constructor(private val workerExecutor: WorkerExecutor) : GenerateDocumentationTaskExtension() {

  @TaskAction
  fun build() {
    val configFile = saveToJsonFile(constructArguments())
    launchDackka(configFile, workerExecutor)
  }

  private fun constructArguments(): JSONObject {
    val annotationsNotToDisplay =
      listOf(
        "androidx.compose.runtime.Stable",
        "androidx.compose.runtime.Immutable",
        "androidx.compose.runtime.ReadOnlyComposable",
        "androidx.annotation.CheckResult",
        "androidx.annotation.OptIn",
        "androidx.annotation.StringDef",
        "kotlin.OptIn",
        "kotlin.ParameterName",
        "kotlin.js.JsName",
        "java.lang.Override"
      )
    val annotationsNotToDisplayKotlin = listOf("kotlin.ExtensionFunctionType")

    val jsonMap =
      mapOf(
        "moduleName" to "",
        "outputDir" to outputDirectory.get().absolutePath,
        "globalLinks" to "",
        "sourceSets" to
          listOf(
            mutableMapOf(
              "sourceSetID" to mapOf("scopeId" to "androidx", "sourceSetName" to "main"),
              "sourceRoots" to sources.get().map { it.absolutePath },
              "classpath" to dependencies.get().map { it.absolutePath },
              "documentedVisibilities" to listOf("PUBLIC", "PROTECTED"),
              "skipEmptyPackages" to "true",
              "suppressedFiles" to suppressedFiles.get().map { it.absolutePath },
              "externalDocumentationLinks" to
                createExternalLinks(packageListFiles).map {
                  mapOf("url" to it.externalLink, "packageListUrl" to it.packageList.toURI())
                }
            )
          ),
        "offlineMode" to "true",
        "noJdkLink" to "true",
        "pluginsConfiguration" to
          listOf(
            mapOf(
              "fqPluginName" to "com.google.devsite.DevsitePlugin",
              "serializationFormat" to "JSON",
              "values" to
                JSONObject(
                    mapOf(
                      "docRootPath" to "/docs/reference/",
                      "javaDocsPath" to "android",
                      "kotlinDocsPath" to "kotlin",
                      "projectPath" to "client/${clientName.get()}",
                      "includedHeadTagsPathJava" to
                        "docs/reference/android/_reference-head-tags.html",
                      "includedHeadTagsPathKotlin" to
                        "docs/reference/kotlin/_reference-head-tags.html",
                      "annotationsNotToDisplay" to annotationsNotToDisplay,
                      "annotationsNotToDisplayKotlin" to annotationsNotToDisplayKotlin
                    )
                  )
                  .toString()
            )
          )
      )

    return JSONObject(jsonMap)
  }

  private fun createExternalLinks(
    packageLists: ListProperty<File>
  ): List<ExternalDocumentationLink> {
    val linksMap =
      mapOf(
        "android" to "https://developer.android.com/reference/kotlin/",
        "androidx" to "https://developer.android.com/reference/kotlin/",
        "google" to "https://developers.google.com/android/reference/",
        "firebase" to "https://firebase.google.com/docs/reference/kotlin/",
        "coroutines" to "https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/",
        "kotlin" to "https://kotlinlang.org/api/latest/jvm/stdlib/"
      )

    return packageLists
      .get()
      .associateWith {
        linksMap[it.parentFile.nameWithoutExtension]
          ?: throw RuntimeException("Unexpected package-list found: ${it.name}")
      }
      .map { ExternalDocumentationLink(it.key, it.value) }
  }

  private fun saveToJsonFile(jsonObject: JSONObject) =
    File.createTempFile("dackkaArgs", ".json").apply {
      deleteOnExit()
      writeText(jsonObject.toString(2))
    }

  private fun launchDackka(argsFile: File, workerExecutor: WorkerExecutor) {
    val workQueue = workerExecutor.noIsolation()
    workQueue.submit<DackkaWorkAction, DackkaParams>() {
      args.set(listOf(argsFile.path, "-loggingLevel", "WARN"))
      dackkaFile.set(dackkaJarFile.get())
    }
  }
}

/**
 * Parameters needs to launch the Dackka fat jar on the command line.
 *
 * @property args a list of arguments to pass to Dackka- should include the json arguments file
 * @property dackkaFile a [File] of the Dackka fat jar
 */
interface DackkaParams : WorkParameters {
  val args: ListProperty<String>
  val dackkaFile: Property<File>
}

/**
 * Work action to launch dackka with a [DackkaParams].
 *
 * Work actions are organized sections of work, offered by gradle.
 */
abstract class DackkaWorkAction @Inject constructor(private val execOperations: ExecOperations) :
  WorkAction<DackkaParams> {
  override fun execute() {
    execOperations.javaexec {
      mainClass.set("org.jetbrains.dokka.MainKt")
      args = parameters.args.get()
      classpath(parameters.dackkaFile.get())
    }
  }
}
