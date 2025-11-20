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

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

class DataConnectProviders(
  project: Project,
  localSettings: DataConnectLocalSettings,
  projectExtension: DataConnectDslExtension,
  variantExtension: DataConnectVariantDslExtension
) {

  val dataConnectExecutable: Provider<DataConnectExecutable> = run {
    val fileGradlePropertyName = "dataconnect.dataConnectExecutable.file"
    val versionGradlePropertyName = "dataconnect.dataConnectExecutable.version"

    val valueFromLocalSettings: Provider<DataConnectExecutable> =
      localSettings.dataConnectExecutableFile
    val fileValueFromGradleProperty: Provider<DataConnectExecutable> =
      project.providers.gradleProperty(fileGradlePropertyName).map {
        val regularFile = project.layout.projectDirectory.file(it)
        DataConnectExecutable.RegularFile(regularFile)
      }
    val versionValueFromGradleProperty: Provider<DataConnectExecutable> =
      project.providers.gradleProperty(versionGradlePropertyName).map {
        DataConnectExecutable.Version(it)
      }
    val valueFromVariant: Provider<DataConnectExecutable> = variantExtension.dataConnectExecutable
    val valueFromProject: Provider<DataConnectExecutable> =
      project.provider { projectExtension.dataConnectExecutable }

    val defaultVersion: Provider<DataConnectExecutable> =
      project.provider {
        val root = DataConnectExecutableVersionsRegistry.load()
        DataConnectExecutable.Version(root.defaultVersion.toString())
      }

    valueFromLocalSettings
      .orElse(fileValueFromGradleProperty)
      .orElse(versionValueFromGradleProperty)
      .orElse(valueFromVariant)
      .orElse(valueFromProject)
      .orElse(defaultVersion)
  }

  val operatingSystem: Provider<OperatingSystem> = project.provider { OperatingSystem.current() }

  val postgresConnectionUrl: Provider<String> = run {
    val gradlePropertyName = "dataconnect.emulator.postgresConnectionUrl"
    val valueFromLocalSettings: Provider<String> = localSettings.postgresConnectionUrl
    val valueFromGradleProperty: Provider<String> =
      project.providers.gradleProperty(gradlePropertyName)
    val valueFromVariant: Provider<String> = variantExtension.emulator.postgresConnectionUrl
    val valueFromProject: Provider<String> =
      project.provider { projectExtension.emulator.postgresConnectionUrl }

    valueFromLocalSettings
      .orElse(valueFromGradleProperty)
      .orElse(valueFromVariant)
      .orElse(valueFromProject)
      .orElse(
        project.provider {
          throw DataConnectGradleException(
            "m6hbyq6j3b",
            "postgresConnectionUrl is not set;" +
              " try setting android.dataconnect.emulator.postgresConnectionUrl=\"postgresql://...\"" +
              " in build.gradle or build.gradle.kts," +
              " setting the $gradlePropertyName project property," +
              " such as by specifying -P${gradlePropertyName}=postgresql://... on the Gradle command line," +
              " or setting ${DataConnectLocalSettings.KEY_POSTGRES_CONNECTION_URL}=postgresql://..." +
              " in ${project.file(DataConnectLocalSettings.FILE_NAME)};" +
              " an example value is postgresql://postgres:postgres@localhost:5432?sslmode=disable"
          )
        }
      )
  }

  val schemaExtensionsOutputEnabled: Provider<Boolean> = run {
    val gradlePropertyName = "dataconnect.emulator.schemaExtensionsOutputEnabled"
    val valueFromLocalSettings: Provider<Boolean> = localSettings.schemaExtensionsOutputEnabled
    val valueFromGradleProperty: Provider<Boolean> =
      project.providers.gradleProperty(gradlePropertyName).map {
        when (it) {
          "1" -> true
          "true" -> true
          "0" -> false
          "false" -> false
          else ->
            throw DataConnectGradleException(
              "shc2xwypgf",
              "invalid value for gradle property $gradlePropertyName: $it" +
                " (valid values are: 0, 1, true, false"
            )
        }
      }
    val valueFromVariant: Provider<Boolean> =
      variantExtension.emulator.schemaExtensionsOutputEnabled
    val valueFromProject: Provider<Boolean> =
      project.provider { projectExtension.emulator.schemaExtensionsOutputEnabled }

    valueFromLocalSettings
      .orElse(valueFromGradleProperty)
      .orElse(valueFromVariant)
      .orElse(valueFromProject)
  }

  val ktfmtJarFile: Provider<RegularFile> = run {
    val gradlePropertyName = "ktfmt.jar.file"
    val valueFromLocalSettings: Provider<RegularFile> = localSettings.ktfmtJarFile
    val valueFromGradleProperty: Provider<RegularFile> =
      project.providers.gradleProperty(gradlePropertyName).flatMap {
        project.layout.file(project.provider { project.file(it) })
      }
    val valueFromVariant: Provider<RegularFile> =
      project.layout.file(variantExtension.ktfmt.jarFile)
    val valueFromProject: Provider<RegularFile> =
      project.layout.file(project.provider { projectExtension.ktfmt.jarFile })

    valueFromLocalSettings
      .orElse(valueFromGradleProperty)
      .orElse(valueFromVariant)
      .orElse(valueFromProject)
  }

  val customConfigDir: Provider<Directory> = run {
    val valueFromVariant: Provider<Directory> = project.layout.dir(variantExtension.configDir)
    val valueFromProject: Provider<Directory> =
      project.provider {
        projectExtension.configDir?.let { file ->
          project.objects.directoryProperty().apply { set(file) }.get()
        }
      }

    valueFromVariant.orElse(valueFromProject)
  }

  val connectors: Provider<Collection<String>> = run {
    val valueFromVariant: Provider<Collection<String>> = variantExtension.codegen.connectors
    val valueFromProject: Provider<Collection<String>> =
      project.provider { projectExtension.codegen.connectors }

    valueFromVariant.orElse(valueFromProject)
  }
}
