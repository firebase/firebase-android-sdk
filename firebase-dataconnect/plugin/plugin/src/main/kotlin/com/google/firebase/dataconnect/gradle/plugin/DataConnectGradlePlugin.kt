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
@file:Suppress("UnstableApiUsage")

package com.google.firebase.dataconnect.gradle.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.VariantExtensionConfig
import java.io.File
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register

@Suppress("unused")
abstract class DataConnectGradlePlugin : Plugin<Project> {

  @get:Inject abstract val projectLayout: ProjectLayout

  @get:Inject abstract val providerFactory: ProviderFactory

  @get:Inject abstract val objectFactory: ObjectFactory

  private val logger = Logging.getLogger(javaClass)

  override fun apply(project: Project) {
    val android =
      project.extensions.run {
        findByType<ApplicationExtension>()
          ?: findByType<LibraryExtension>()
            ?: throw DataConnectGradleException(
            "b2a848r87f",
            "Unable to find Android ApplicationExtension or LibraryExtension;" +
              " ensure that the Android Gradle application or library plugin has been applied"
          )
      } as ExtensionAware

    val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
    logger.info("Found Android Gradle Plugin version: {}", androidComponents.pluginVersion)

    androidComponents.registerSourceType("dataconnect")

    androidComponents.registerExtension(
      DslExtension.Builder("dataconnect")
        .extendBuildTypeWith(DataConnectDslExtension::class.java)
        .extendProductFlavorWith(DataConnectDslExtension::class.java)
        .extendProjectWith(DataConnectDslExtension::class.java)
        .build()
    ) { config: VariantExtensionConfig<*> ->
      project.objects.newInstance<DataConnectVariantDslExtension>(config)
    }

    val dataConnectLocalSettings = DataConnectLocalSettings(project)

    androidComponents.onVariants { variant ->
      val variantNameTitleCase = variant.name.replaceFirstChar { it.titlecase(Locale.US) }
      val dataConnectDslProjectExtension = android.extensions.getByType<DataConnectDslExtension>()
      val dataConnectDslVariantExtension = variant.getExtension<DataConnectVariantDslExtension>()

      val resolvedDataConnectExecutable: Provider<RegularFile> = run {
        val gradlePropertyName = "dataconnect.dataConnectExecutable"
        val valueFromLocalSettings = dataConnectLocalSettings.dataConnectExecutable
        val valueFromGradleProperty =
          project.providers.gradleProperty(gradlePropertyName).map { project.file(it) }
        val valueFromProject: Provider<File> =
          providerFactory.provider { dataConnectDslProjectExtension.dataConnectExecutable }
        val valueFromVariant: Provider<File> = dataConnectDslVariantExtension.dataConnectExecutable
        valueFromLocalSettings
          .orElse(valueFromGradleProperty)
          .orElse(valueFromVariant)
          .orElse(valueFromProject)
          .map { project.layout.projectDirectory.file(it.path) }
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

      val resolvedCustomConfigDir: Provider<Directory> = run {
        val valueFromProject: Provider<File> =
          providerFactory.provider { dataConnectDslProjectExtension.configDir }
        val valueFromVariant: Provider<File> = dataConnectDslVariantExtension.configDir
        valueFromVariant.orElse(valueFromProject).map {
          project.layout.projectDirectory.dir(it.path)
        }
      }

      val resolvedConnectors: Provider<Collection<String>> = run {
        val valueFromProject: Provider<Collection<String>> =
          providerFactory.provider { dataConnectDslProjectExtension.codegen.connectors }
        val valueFromVariant: Provider<Collection<String>> =
          dataConnectDslVariantExtension.codegen.connectors
        valueFromVariant.orElse(valueFromProject)
      }

      val resolvedPostgresConnectionUrl: Provider<String> = run {
        val gradlePropertyName = "dataconnect.postgresConnectionUrl"
        val valueFromLocalSettings = dataConnectLocalSettings.postgresConnectionUrl
        val valueFromGradleProperty = project.providers.gradleProperty(gradlePropertyName)
        val valueFromProject: Provider<String> =
          providerFactory.provider { dataConnectDslProjectExtension.emulator.postgresConnectionUrl }
        val valueFromVariant: Provider<String> =
          dataConnectDslVariantExtension.emulator.postgresConnectionUrl
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

      val mergeConfigDirectoriesTask =
        project.tasks.register<DataConnectMergeConfigDirectoriesTask>(
          "merge${variantNameTitleCase}DataConnectConfigDirs"
        ) {
          defaultConfigDirectories.set(variant.sources.getByName("dataconnect").all)
          customConfigDirectory.set(resolvedCustomConfigDir)
          buildDirectory.set(
            project.layout.buildDirectory.dir("intermediates/dataconnect/${variant.name}")
          )
          mergedDirectory.set(
            providerFactory
              .provider {
                buildList {
                    addAll(defaultConfigDirectories.get())
                    customConfigDirectory.orNull?.let { add(it) }
                  }
                  .singleOrNull { it.asFile.exists() }
              }
              .orElse(buildDirectory)
          )
        }

      project.tasks.register<DataConnectRunEmulatorTask>(
        "run${variantNameTitleCase}DataConnectEmulator"
      ) {
        outputs.upToDateWhen { false }
        dataConnectExecutable.set(resolvedDataConnectExecutable)
        configDirectory.set(mergeConfigDirectoriesTask.flatMap { it.mergedDirectory })
        postgresConnectionUrl.set(resolvedPostgresConnectionUrl)
      }

      val generateCodeTask =
        project.tasks.register<DataConnectGenerateCodeTask>(
          "generate${variantNameTitleCase}DataConnectSources"
        ) {
          dataConnectExecutable.set(resolvedDataConnectExecutable)
          configDirectory.set(mergeConfigDirectoriesTask.flatMap { it.mergedDirectory })
          connectors.set(resolvedConnectors)
        }

      variant.sources.java!!.addGeneratedSourceDirectory(
        generateCodeTask,
        DataConnectGenerateCodeTask::outputDirectory
      )
    }
  }
}
