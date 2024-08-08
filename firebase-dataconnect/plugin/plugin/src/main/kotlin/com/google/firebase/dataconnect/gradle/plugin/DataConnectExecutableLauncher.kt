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

interface DataConnectExecutableConfig {
  var outputDirectory: File?
  var connectors: Collection<String>
  var listen: String?
  var localConnectionString: String?
}

fun Task.runDataConnectExecutable(
  dataConnectExecutable: File,
  subCommand: List<String>,
  configDirectory: File,
  configure: DataConnectExecutableConfig.() -> Unit,
) {
  val config =
    object : DataConnectExecutableConfig {
        override var outputDirectory: File? = null
        override var connectors: Collection<String> = emptyList()
        override var listen: String? = null
        override var localConnectionString: String? = null
      }
      .apply(configure)

  project.exec { execSpec ->
    execSpec.run {
      executable(dataConnectExecutable)
      isIgnoreExitValue = false

      if (logger.isDebugEnabled) {
        args("-v").args("9")
        args("-logtostderr")
      } else if (logger.isInfoEnabled) {
        args("-v").args("2")
        args("-logtostderr")
      }

      args(subCommand)

      args("-config_dir=$configDirectory")

      config.outputDirectory?.let { args("-output_dir=${it.path}") }
      config.connectors.let {
        if (it.isNotEmpty()) {
          args("-connectors=${it.joinToString(",")}")
        }
      }
      config.listen?.let { args("-listen=${it}") }
      config.localConnectionString?.let { args("-local_connection_string=${it}") }
    }
  }
}
