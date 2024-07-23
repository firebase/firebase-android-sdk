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
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class DataConnectGenerateCodeTask : DefaultTask() {

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:OutputDirectory abstract val mergedInputsDirectory: DirectoryProperty

  @get:InputFiles abstract val inputDirectories: ListProperty<Collection<Directory>>

  @get:InputFile abstract val dataConnectCliExecutable: RegularFileProperty

  @get:Input abstract val variantExtension: Property<DataConnectVariantDslExtension>

  @TaskAction
  fun taskAction() {
    val outputDirectory = outputDirectory.get().asFile
    val mergedInputsDirectory = mergedInputsDirectory.get().asFile
    val inputDirectories = inputDirectories.get().flatten().map { it.asFile }.filter { it.exists() }
    val dataConnectCliExecutable: File =
      variantExtension
        .get()
        .dataConnectCliExecutable
        .getOrElse(dataConnectCliExecutable.get())
        .asFile
    val connectors: List<String> = variantExtension.get().connectors.getOrElse(emptyList())

    logger.info("outputDirectory={}", outputDirectory)
    logger.info("mergedInputsDirectory={}", mergedInputsDirectory)
    logger.info("inputDirectories={}", inputDirectories)
    logger.info("dataConnectCliExecutable={}", dataConnectCliExecutable)
    logger.info("connectors={}", if (connectors.isNotEmpty()) connectors.toString() else "<all>")
  }
}
