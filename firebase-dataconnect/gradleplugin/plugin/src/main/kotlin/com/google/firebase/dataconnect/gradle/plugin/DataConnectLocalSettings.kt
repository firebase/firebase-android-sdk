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

  val dataConnectExecutableFile: Provider<DataConnectExecutable> =
    project
      .providerForDataConnectLocalSettings(
        KEY_DATA_CONNECT_EXECUTABLE_FILE,
        KEY_DATA_CONNECT_EXECUTABLE_VERSION
      ) { settingName, settingValue, project ->
        if (settingName == KEY_DATA_CONNECT_EXECUTABLE_FILE) {
          val regularFile = project.layout.projectDirectory.file(settingValue)
          DataConnectExecutable.RegularFile(regularFile)
        } else if (settingName == KEY_DATA_CONNECT_EXECUTABLE_VERSION) {
          DataConnectExecutable.Version(settingValue)
        } else {
          throw IllegalStateException(
            "fileValue==null && versionValue==null (error code rbhmsd524t)"
          )
        }
      }
      .map { settingValueByName ->
        val executableFile = settingValueByName[KEY_DATA_CONNECT_EXECUTABLE_FILE]
        val executableVersion = settingValueByName[KEY_DATA_CONNECT_EXECUTABLE_VERSION]
        executableFile
          ?: executableVersion
            ?: throw IllegalStateException(
            "executableFile==null && executableVersion==null (error code cn9ygjt55e)"
          )
      }

  val postgresConnectionUrl: Provider<String> =
    project.providerForDataConnectLocalSetting(KEY_POSTGRES_CONNECTION_URL)

  val schemaExtensionsOutputEnabled: Provider<Boolean> =
    project.providerForDataConnectLocalSetting(KEY_SCHEMA_EXTENSIONS_OUTPUT_ENABLED).map {
      when (it) {
        "1" -> true
        "true" -> true
        "0" -> false
        "false" -> false
        // TODO: Find a way to include the file name in th exception's message.
        else ->
          throw DataConnectGradleException(
            "whrtqh5wvy",
            "invalid value for $KEY_SCHEMA_EXTENSIONS_OUTPUT_ENABLED: $it" +
              " (valid values are: 0, 1, true, false"
          )
      }
    }

  val ktfmtJarFile: Provider<RegularFile> =
    project.providerForDataConnectLocalSetting(KEY_KTFMT_JAR_FILE) { settingValue, project ->
      project.layout.projectDirectory.file(settingValue)
    }

  companion object {
    const val FILE_NAME = "dataconnect.local.properties"
    const val KEY_DATA_CONNECT_EXECUTABLE_FILE = "dataConnectExecutable.file"
    const val KEY_DATA_CONNECT_EXECUTABLE_VERSION = "dataConnectExecutable.version"
    const val KEY_POSTGRES_CONNECTION_URL = "emulator.postgresConnectionUrl"
    const val KEY_SCHEMA_EXTENSIONS_OUTPUT_ENABLED = "emulator.schemaExtensionsOutputEnabled"
    const val KEY_KTFMT_JAR_FILE = "ktfmt.jar.file"

    fun Project.providerForDataConnectLocalSetting(settingName: String): Provider<String> =
      providerForDataConnectLocalSetting(settingName) { value, _ -> value }

    fun <T : Any> Project.providerForDataConnectLocalSetting(
      settingName: String,
      transformer: (String, Project) -> T
    ): Provider<T> =
      providerForDataConnectLocalSettings(settingName) { _, settingValue, project ->
          transformer(settingValue, project)
        }
        .map { it[settingName]!! }

    fun <T> Project.providerForDataConnectLocalSettings(
      firstSettingName: String,
      vararg otherSettingNames: String,
      transformer: (String, String, Project) -> T,
    ): Provider<Map<String, T>> =
      project.provider {
        var curProject: Project? = project
        while (curProject !== null) {
          val settingValues =
            curProject.settingValuesFromDataConnectLocalSettings(
              firstSettingName,
              *otherSettingNames
            )
          if (settingValues.isNotEmpty()) {
            return@provider settingValues.mapValues { entry ->
              transformer(entry.key, entry.value, curProject!!)
            }
          }
          curProject = curProject.parent
        }
        return@provider null
      }

    private fun Project.settingValuesFromDataConnectLocalSettings(
      firstSettingName: String,
      vararg otherSettingNames: String
    ): Map<String, String> {
      val localPropertiesFile = project.file(FILE_NAME)
      logger.info(
        "Looking for Data Connect local properties file: {}",
        localPropertiesFile.absolutePath,
      )

      if (!localPropertiesFile.exists()) {
        return emptyMap()
      }

      logger.info("Loading Data Connect local settings file: {}", localPropertiesFile.absolutePath)
      val properties = Properties()
      localPropertiesFile.inputStream().use { properties.load(it) }

      val settingNames = buildList {
        add(firstSettingName)
        addAll(otherSettingNames)
      }
      val settingValueByName = mutableMapOf<String, String>()
      for (settingName in settingNames) {
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
          settingValueByName.put(settingName, settingValue)
        }
      }

      return settingValueByName
    }
  }
}
