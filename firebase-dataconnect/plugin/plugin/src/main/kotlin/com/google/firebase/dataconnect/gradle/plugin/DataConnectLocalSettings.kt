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

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

class DataConnectLocalSettings(project: Project) {

  val dataConnectExecutable: Provider<DataConnectExecutable> =
    project.providerForDataConnectLocalSetting(KEY_DATA_CONNECT_EXECUTABLE) { value, project ->
      val regularFile = project.layout.projectDirectory.file(value)
      DataConnectExecutable.RegularFile(regularFile, verificationInfo = null)
    }

  val postgresConnectionUrl: Provider<String> =
    project.providerForDataConnectLocalSetting(KEY_POSTGRES_CONNECTION_URL)

  companion object {
    const val FILE_NAME = "dataconnect.local.properties"
    const val KEY_DATA_CONNECT_EXECUTABLE = "dataConnectExecutable"
    const val KEY_POSTGRES_CONNECTION_URL = "postgresConnectionUrl"

    fun Project.providerForDataConnectLocalSetting(settingName: String): Provider<String> =
      providerForDataConnectLocalSetting(settingName) { value, _ -> value }

    fun <T> Project.providerForDataConnectLocalSetting(
      settingName: String,
      transformer: (String, Project) -> T
    ): Provider<T> =
      project.provider {
        var curProject: Project? = project
        while (curProject !== null) {
          val settingValue = curProject.settingValueFromDataConnectLocalSettings(settingName)
          if (settingValue !== null) {
            return@provider transformer(settingValue, curProject)
          }
          curProject = curProject.parent
        }
        return@provider null
      }

    fun Project.settingValueFromDataConnectLocalSettings(settingName: String): String? {
      val localPropertiesFile = project.file(FILE_NAME)
      logger.info(
        "Looking for Data Connect local properties file: {}",
        localPropertiesFile.absolutePath
      )

      if (!localPropertiesFile.exists()) {
        return null
      }

      logger.info("Loading Data Connect local settings file: {}", localPropertiesFile.absolutePath)
      val properties = Properties()
      localPropertiesFile.inputStream().use { properties.load(it) }

      val settingValue = properties.getProperty(settingName)
      if (settingValue === null) {
        logger.info(
          "Setting \"{}\" not found in Data Connect local properties file: {}",
          settingName,
          localPropertiesFile.absolutePath,
        )
      } else {
        logger.info(
          "Setting \"{}\" found in Data Connect local properties file {}: {}",
          settingName,
          localPropertiesFile.absolutePath,
          settingValue,
        )
      }

      return settingValue
    }
  }
}
