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
    val gradlePropertyName = "dataconnect.dataConnectExecutable"
    val valueFromLocalSettings: Provider<DataConnectExecutable> =
      localSettings.dataConnectExecutable
    val valueFromGradleProperty: Provider<DataConnectExecutable> =
      project.providers.gradleProperty(gradlePropertyName).map {
        val regularFile = project.layout.projectDirectory.file(it)
        DataConnectExecutable.RegularFile(regularFile, verificationInfo = null)
      }
    val valueFromVariant: Provider<DataConnectExecutable> = variantExtension.dataConnectExecutable
    val valueFromProject: Provider<DataConnectExecutable> =
      project.provider { projectExtension.dataConnectExecutable }

    valueFromLocalSettings
      .orElse(valueFromGradleProperty)
      .orElse(valueFromVariant)
      .orElse(valueFromProject)
      .orElse(
        project.provider {
          throw DataConnectGradleException(
            "cgyqepdcxz",
            "dataConnectExecutable is not set;" +
              " try setting android.dataconnect.dataConnectExecutable=file(\"/foo/bar/cli\")" +
              " in build.gradle or build.gradle.kts," +
              " setting the $gradlePropertyName project property," +
              " such as by specifying -P${gradlePropertyName}=/foo/bar/cli on the Gradle command line," +
              " or setting ${DataConnectLocalSettings.KEY_DATA_CONNECT_EXECUTABLE}=/foo/bar/cli" +
              " in ${project.file(DataConnectLocalSettings.FILE_NAME)}"
          )
        }
      )
  }

  val postgresConnectionUrl: Provider<String> = run {
    val gradlePropertyName = "dataconnect.postgresConnectionUrl"
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
