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
import java.util.Locale
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register

@Suppress("unused")
abstract class DataConnectGradlePlugin : Plugin<Project> {

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
      val baseBuildDirectory: Provider<Directory> =
        project.layout.buildDirectory.dir("intermediates/dataconnect/${variant.name}")

      val dataConnectProviders =
        DataConnectProviders(
          project = project,
          localSettings = dataConnectLocalSettings,
          projectExtension = android.extensions.getByType<DataConnectDslExtension>(),
          variantExtension = variant.getExtension<DataConnectVariantDslExtension>(),
        )

      val downloadDataConnectExecutableTask =
        project.tasks.register<DataConnectExecutableDownloadTask>(
          "download${variantNameTitleCase}DataConnectExecutable"
        ) {
          val dataConnectExecutable = dataConnectProviders.dataConnectExecutable
          buildDirectory.set(baseBuildDirectory.map { it.dir("executable") })
          inputFile.set(
            dataConnectExecutable.map(
              TransformerInterop {
                when (it) {
                  is DataConnectExecutable.File ->
                    project.layout.projectDirectory.file(it.file.path)
                  is DataConnectExecutable.RegularFile -> it.file
                  is DataConnectExecutable.Version -> null
                }
              }
            )
          )
          version.set(
            dataConnectExecutable.map(
              TransformerInterop {
                when (it) {
                  is DataConnectExecutable.File -> null
                  is DataConnectExecutable.RegularFile -> null
                  is DataConnectExecutable.Version -> it.version
                }
              }
            )
          )
          operatingSystem.set(dataConnectProviders.operatingSystem)
          cpuArchitecture.set(dataConnectProviders.cpuArchitecture)
          outputFile.set(
            dataConnectExecutable.map {
              when (it) {
                is DataConnectExecutable.File -> inputFile.get()
                is DataConnectExecutable.RegularFile -> inputFile.get()
                is DataConnectExecutable.Version ->
                  buildDirectory
                    .map { directory ->
                      val os = dataConnectProviders.operatingSystem.get()
                      directory.file("dataconnect-v${it.version}${os.executableSuffix}")
                    }
                    .get()
              }
            }
          )
        }

      val defaultConfigDirectories = variant.sources.getByName("dataconnect").all
      val customConfigDirectory = dataConnectProviders.customConfigDir
      val allConfigDirectories = buildList {
        addAll(defaultConfigDirectories.get())
        customConfigDirectory.orNull?.let { add(it) }
      }
      val existingConfigDirectories = allConfigDirectories.filter { it.asFile.exists() }

      val mergeConfigDirectoriesTask =
        project.tasks.register<DataConnectMergeConfigDirectoriesTask>(
          "merge${variantNameTitleCase}DataConnectConfigDirs"
        ) {
          this.defaultConfigDirectories.set(defaultConfigDirectories)
          this.customConfigDirectory.set(customConfigDirectory)
          buildDirectory.set(baseBuildDirectory.map { it.dir("mergedConfigs") })
          if (existingConfigDirectories.size > 1) {
            mergedDirectory.set(buildDirectory)
          }
        }

      project.tasks.register<DataConnectRunEmulatorTask>(
        "run${variantNameTitleCase}DataConnectEmulator"
      ) {
        outputs.upToDateWhen { false }
        buildDirectory.set(baseBuildDirectory.map { it.dir("runEmulator") })
        dataConnectExecutable.set(downloadDataConnectExecutableTask.flatMap { it.outputFile })
        if (existingConfigDirectories.size > 1) {
          configDirectory.set(mergeConfigDirectoriesTask.flatMap { it.mergedDirectory })
        } else if (existingConfigDirectories.size == 1) {
          configDirectory.set(existingConfigDirectories.single())
        } else {
          configDirectory.set(
            project.provider {
              throw DataConnectGradleException(
                "cvvz9b57qp",
                "Cannot run the Data Connect emulator unless one or more config directories exist:" +
                  allConfigDirectories.joinToString(", ")
              )
            }
          )
        }
        postgresConnectionUrl.set(dataConnectProviders.postgresConnectionUrl)
        schemaExtensionsOutputEnabled.set(dataConnectProviders.schemaExtensionsOutputEnabled)
      }

      val generateCodeTask =
        project.tasks.register<DataConnectGenerateCodeTask>(
          "generate${variantNameTitleCase}DataConnectSources"
        ) {
          dataConnectExecutable.set(downloadDataConnectExecutableTask.flatMap { it.outputFile })
          if (existingConfigDirectories.size > 1) {
            configDirectory.set(mergeConfigDirectoriesTask.flatMap { it.mergedDirectory })
          } else if (existingConfigDirectories.size == 1) {
            configDirectory.set(existingConfigDirectories.single())
          }
          connectors.set(dataConnectProviders.connectors)
          buildDirectory.set(baseBuildDirectory.map { it.dir("generateCode") })
          ktfmtJarFile.set(dataConnectProviders.ktfmtJarFile)
          dataConnectExecutableCallingConvention.set(detectedCallingConvention())
        }

      variant.sources.java!!.addGeneratedSourceDirectory(
        generateCodeTask,
        DataConnectGenerateCodeTask::outputDirectory
      )
    }
  }
}
