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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class DataConnectRunEmulatorTask : DefaultTask() {

  @get:InputFile abstract val dataConnectExecutable: RegularFileProperty

  @get:InputDirectory abstract val configDirectory: DirectoryProperty

  @get:Input abstract val postgresConnectionUrl: Property<String>

  @get:Optional @get:Input abstract val schemaExtensionsOutputEnabled: Property<Boolean>

  @get:Internal abstract val buildDirectory: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  @TaskAction
  fun run() {
    val dataConnectExecutable: File = dataConnectExecutable.get().asFile
    val configDirectory: File = configDirectory.get().asFile
    val postgresConnectionUrl: String = postgresConnectionUrl.get()
    val schemaExtensionsOutputEnabled: Boolean = schemaExtensionsOutputEnabled.orNull ?: false
    val buildDirectory: File = buildDirectory.get().asFile

    logger.info("dataConnectExecutable={}", dataConnectExecutable.absolutePath)
    logger.info("configDirectory={}", configDirectory.absolutePath)
    logger.info("postgresConnectionUrl={}", postgresConnectionUrl)
    logger.info("schemaExtensionsOutputEnabled={}", schemaExtensionsOutputEnabled)
    logger.info("buildDirectory={}", buildDirectory)

    runDataConnectExecutable(
      dataConnectExecutable = dataConnectExecutable,
      subCommand = listOf("dev"),
      configDirectory = configDirectory,
      execOperations = execOperations,
    ) {
      this.listen = "127.0.0.1:9399"
      this.localConnectionString = postgresConnectionUrl
      this.logFile = File(buildDirectory, "log.txt")
      this.schemaExtensionsOutputEnabled = schemaExtensionsOutputEnabled
    }
  }
}
