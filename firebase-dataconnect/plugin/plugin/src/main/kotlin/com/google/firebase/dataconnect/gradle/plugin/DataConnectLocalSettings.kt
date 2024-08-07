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
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class DataConnectLocalSettings(project: Project) {

  val dataConnectExecutable: Provider<File> =
    project.provider {
      var curProject: Project? = project
      while (curProject !== null) {
        val dataConnectExecutable = curProject.loadDataConnectExecutableFromLocalProperties()
        if (dataConnectExecutable !== null) {
          return@provider dataConnectExecutable
        }
        curProject = curProject.parent
      }
      return@provider null
    }

  private companion object {
    const val FILE_NAME = "dataconnect.local.properties"
    const val KEY_DATA_CONNECT_EXECUTABLE = "dataConnectExecutable"

    fun Project.loadDataConnectExecutableFromLocalProperties(): File? {
      val localPropertiesFile = project.file(FILE_NAME)
      logger.info(
        "Looking for Data Connect local properties file: {}",
        localPropertiesFile.absolutePath
      )

      if (!localPropertiesFile.exists()) {
        return null
      }

      logger.info(
        "Loading Data Connect local properties file: {}",
        localPropertiesFile.absolutePath
      )
      val properties = Properties()
      localPropertiesFile.inputStream().use { properties.load(it) }

      val dataConnectExecutableStr = properties.getProperty(KEY_DATA_CONNECT_EXECUTABLE)
      if (dataConnectExecutableStr === null) {
        logger.info(
          "Key \"{}\" not found in Data Connect local properties file: {}",
          KEY_DATA_CONNECT_EXECUTABLE,
          localPropertiesFile.absolutePath
        )
        return null
      }

      val dataConnectExecutableFile = project.file(dataConnectExecutableStr)
      logger.info(
        "Key \"{}\" found in Data Connect local properties file {}: {}",
        KEY_DATA_CONNECT_EXECUTABLE,
        localPropertiesFile.absolutePath,
        dataConnectExecutableFile.absolutePath
      )
      return dataConnectExecutableFile
    }
  }
}
