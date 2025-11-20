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
import org.gradle.api.Task
import org.gradle.process.ExecOperations

interface DataConnectExecutableConfig {
  var outputDirectory: File?
  var connectors: Collection<String>
  var connectorId: Collection<String>
  var listen: String?
  var localConnectionString: String?
  var logFile: File?
  var schemaExtensionsOutputEnabled: Boolean?
  var platform: String?
}

fun Task.runDataConnectExecutable(
  dataConnectExecutable: File,
  subCommand: List<String>,
  configDirectory: File,
  execOperations: ExecOperations,
  configure: DataConnectExecutableConfig.() -> Unit,
) {
  val config =
    object : DataConnectExecutableConfig {
        override var outputDirectory: File? = null
        override var connectors: Collection<String> = emptyList()
        override var connectorId: Collection<String> = emptyList()
        override var listen: String? = null
        override var localConnectionString: String? = null
        override var logFile: File? = null
        override var schemaExtensionsOutputEnabled: Boolean? = null
        override var platform: String? = null
      }
      .apply(configure)

  val logFile = config.logFile?.also { it.parentFile.mkdirs() }
  val logFileStream = logFile?.outputStream()

  try {
    execOperations.exec { execSpec ->
      execSpec.run {
        executable(dataConnectExecutable)
        isIgnoreExitValue = false

        if (logger.isDebugEnabled) {
          args("-v").args("9")
          args("-logtostderr")
        } else if (logger.isInfoEnabled) {
          args("-v").args("2")
          args("-logtostderr")
        } else if (logFileStream !== null) {
          args("-v").args("2")
          args("-logtostderr")
          standardOutput = logFileStream
          errorOutput = logFileStream
        }

        args(subCommand)

        args("-config_dir=$configDirectory")

        config.outputDirectory?.let { args("-output_dir=${it.path}") }
        config.connectors.let {
          if (it.isNotEmpty()) {
            args("-connectors=${it.joinToString(",")}")
          }
        }
        config.connectorId.let {
          if (it.isNotEmpty()) {
            args("-connector_id=${it.joinToString(",")}")
          }
        }
        config.listen?.let { args("-listen=${it}") }
        config.platform?.let { args("-platform=${it}") }
        config.localConnectionString?.let { args("-local_connection_string=${it}") }
        config.schemaExtensionsOutputEnabled?.let { args("-enable_output_schema_extensions=${it}") }
      }
    }
  } catch (e: Exception) {
    logFileStream?.close()
    logFile?.forEachLine { logger.error(it.trimEnd()) }
    throw e
  } finally {
    logFileStream?.close()
  }
}
