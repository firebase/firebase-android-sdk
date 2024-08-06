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
import org.gradle.kotlin.dsl.register

@Suppress("unused")
abstract class DataConnectGradlePlugin : Plugin<Project> {

  @get:Inject abstract val projectLayout: ProjectLayout

  @get:Inject abstract val providerFactory: ProviderFactory

  @get:Inject abstract val objectFactory: ObjectFactory

  private val logger = Logging.getLogger(javaClass)

  override fun apply(project: Project) {
    // TODO: Add support for com.android.build.api.dsl.ApplicationExtension, not just
    // LibraryExtension.
    val android = project.extensions.getByType(LibraryExtension::class.java) as ExtensionAware
    val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

    androidComponents.registerSourceType("dataconnect")

    androidComponents.registerExtension(
      DslExtension.Builder("dataconnect")
        .extendBuildTypeWith(DataConnectDslExtension::class.java)
        .extendProductFlavorWith(DataConnectDslExtension::class.java)
        .extendProjectWith(DataConnectDslExtension::class.java)
        .build()
    ) { config: VariantExtensionConfig<*> ->
      project.objects.newInstance(DataConnectVariantDslExtension::class.java, config)
    }

    androidComponents.onVariants { variant ->
      val variantNameTitleCase = variant.name.replaceFirstChar { it.titlecase(Locale.US) }
      val dataConnectDslProjectExtension =
        android.extensions.getByType(DataConnectDslExtension::class.java)
      val dataConnectDslVariantExtension =
        variant.getExtension(DataConnectVariantDslExtension::class.java)!!

      val resolvedDataConnectExecutable: Provider<RegularFile> = run {
        val valueFromProject: Provider<File> =
          providerFactory.provider { dataConnectDslProjectExtension.dataConnectExecutable }
        val valueFromVariant: Provider<File> = dataConnectDslVariantExtension.dataConnectExecutable
        valueFromVariant.orElse(valueFromProject).map {
          project.layout.projectDirectory.file(it.path)
        }
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

      val mergeConfigDirectoriesTask =
        project.tasks.register<DataConnectMergeDataConnectDirectoriesTask>(
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

      project.tasks.register<DataConnectEmulatorTask>(
        "run${variantNameTitleCase}DataConnectEmulator"
      ) {
        outputs.upToDateWhen { false }
        dataConnectExecutable.set(resolvedDataConnectExecutable)
        configDirectory.set(mergeConfigDirectoriesTask.flatMap { it.mergedDirectory })
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
