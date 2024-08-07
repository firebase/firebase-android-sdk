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

fun Task.runDataConnectExecutable(
  dataConnectExecutable: File,
  subCommand: List<String>,
  configDirectory: File,
  connectors: Collection<String> = emptyList(),
  outputDirectory: File? = null
) {
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

      if (outputDirectory !== null) {
        args("-output_dir=${outputDirectory.path}")
      }
      if (connectors.isNotEmpty()) {
        args("-connectors=${connectors.joinToString(",")}")
      }
    }
  }
}
