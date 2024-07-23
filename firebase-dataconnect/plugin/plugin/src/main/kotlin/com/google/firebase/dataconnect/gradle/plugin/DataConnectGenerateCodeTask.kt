/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.gradle.plugin

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class DataConnectGenerateCodeTask : DefaultTask() {

  @get:Inject abstract val execOperations: ExecOperations

  @get:Inject abstract val fileSystemOperations: FileSystemOperations

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:OutputDirectory abstract val mergedInputsDirectory: DirectoryProperty

  @get:InputFiles abstract val inputDirectories: ListProperty<Collection<Directory>>

  @get:InputFile abstract val dataConnectCliExecutable: RegularFileProperty

  @get:Input abstract val variantExtension: Property<DataConnectVariantDslExtension>

  @TaskAction
  fun run() {
    val outputDirectory: File = outputDirectory.get().asFile
    val mergedInputsDirectory: File = mergedInputsDirectory.get().asFile
    val inputDirectories: List<List<File>> =
      inputDirectories.get().map { aaa -> aaa.map { bbb -> bbb.asFile } }
    val dataConnectCliExecutable: File =
      variantExtension
        .get()
        .dataConnectCliExecutable
        .getOrElse(dataConnectCliExecutable.get())
        .asFile
    val connectors: List<String> = variantExtension.get().connectors.getOrElse(emptyList())

    logger.info("outputDirectory={}", outputDirectory)
    logger.info("mergedInputsDirectory={}", mergedInputsDirectory)
    logger.info("inputDirectories={}", inputDirectories.flatten().filter { it.exists() })
    logger.info("dataConnectCliExecutable={}", dataConnectCliExecutable)
    logger.info("connectors={}", if (connectors.isNotEmpty()) connectors.toString() else "<all>")

    fileSystemOperations.delete { it.delete(mergedInputsDirectory, outputDirectory) }
    mergeInputDirectories(inputDirectories, mergedInputsDirectory)
    runCodgen(mergedInputsDirectory, outputDirectory, dataConnectCliExecutable, connectors)
  }

  private fun mergeInputDirectories(inputDirectories: List<List<File>>, outputDirectory: File) {
    for (curInputDirectories in inputDirectories) {
      fileSystemOperations.copy {
        it.from(inputDirectories)
        it.into(outputDirectory)
        it.duplicatesStrategy = DuplicatesStrategy.FAIL
      }
    }
  }

  private fun runCodgen(
    intermediatesDirectory: File,
    outputDirectory: File,
    dataConnectCliExecutable: File,
    connectors: List<String>
  ) {
    execOperations.exec {
      it.setIgnoreExitValue(false)
      it.executable = dataConnectCliExecutable.path

      if (logger.isInfoEnabled) {
        it.args("-logtostderr")
      }
      if (logger.isDebugEnabled) {
        it.args("-v")
        it.args("2")
      }
      it.args("gradle")
      it.args("generate")
      it.args("-config_dir=$intermediatesDirectory")
      it.args("-output_dir=${outputDirectory.path}")
      if (connectors.isNotEmpty()) {
        it.args("-connectors=${connectors.joinToString(",")}")
      }
    }
  }
}
