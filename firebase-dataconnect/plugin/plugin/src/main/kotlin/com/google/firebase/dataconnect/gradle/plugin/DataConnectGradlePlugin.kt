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
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
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

      val dataConnectProviders =
        DataConnectProviders(
          project = project,
          localSettings = dataConnectLocalSettings,
          projectExtension = android.extensions.getByType<DataConnectDslExtension>(),
          variantExtension = variant.getExtension<DataConnectVariantDslExtension>(),
        )

      val mergeConfigDirectoriesTask =
        project.tasks.register<DataConnectMergeConfigDirectoriesTask>(
          "merge${variantNameTitleCase}DataConnectConfigDirs"
        ) {
          defaultConfigDirectories.set(variant.sources.getByName("dataconnect").all)
          customConfigDirectory.set(dataConnectProviders.customConfigDir)
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
        dataConnectExecutable.set(dataConnectProviders.dataConnectExecutable)
        configDirectory.set(mergeConfigDirectoriesTask.flatMap { it.mergedDirectory })
        postgresConnectionUrl.set(dataConnectProviders.postgresConnectionUrl)
      }

      val generateCodeTask =
        project.tasks.register<DataConnectGenerateCodeTask>(
          "generate${variantNameTitleCase}DataConnectSources"
        ) {
          dataConnectExecutable.set(dataConnectProviders.dataConnectExecutable)
          configDirectory.set(mergeConfigDirectoriesTask.flatMap { it.mergedDirectory })
          connectors.set(dataConnectProviders.connectors)
        }

      variant.sources.java!!.addGeneratedSourceDirectory(
        generateCodeTask,
        DataConnectGenerateCodeTask::outputDirectory
      )
    }
  }
}
