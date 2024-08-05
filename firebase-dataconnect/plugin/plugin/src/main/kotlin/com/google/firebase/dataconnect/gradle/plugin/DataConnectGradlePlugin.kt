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
import java.io.FileNotFoundException
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register

@Suppress("unused")
abstract class DataConnectGradlePlugin : Plugin<Project> {

  @get:Inject abstract val projectLayout: ProjectLayout

  @get:Inject abstract val providerFactory: ProviderFactory

  @get:Inject abstract val objectFactory: ObjectFactory

  private val logger = Logging.getLogger(javaClass)

  override fun apply(project: Project) {
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

      val emulatorTask =
        project.tasks.register<DataConnectEmulatorTask>(
          "run${variantNameTitleCase}DataConnectEmulator"
        )

      val generateCodeTask =
        project.tasks.register<DataConnectGenerateCodeTask>(
          "generate${variantNameTitleCase}DataConnectSources"
        ) {
          workDirectory.set(
            project.layout.buildDirectory.dir("intermediates/dataconnect/${variant.name}")
          )
          defaultConfigDirectories.set(variant.sources.getByName("dataconnect").all)
          connectors.set(emptyList())

          val android = project.extensions.getByType(LibraryExtension::class.java) as ExtensionAware
          android.extensions.getByType(DataConnectDslExtension::class.java).let { parentExt ->
            for (ext in listOf(parentExt, parentExt.codegen)) {
              ext.configDir?.let { customConfigDirectory.set(it) }
              ext.connectors?.let { connectors.set(it) }
              ext.dataConnectExecutable?.let { dataConnectExecutable.set(it) }
            }
          }

          variant.getExtension(DataConnectVariantDslExtension::class.java)!!.also { parentExt ->
            for (ext in listOf(parentExt, parentExt.codegen)) {
              if (ext.configDir.isPresent) {
                customConfigDirectory.set(ext.configDir)
              }
              if (ext.connectors.isPresent) {
                connectors.set(ext.connectors)
              }
              if (ext.dataConnectExecutable.isPresent) {
                dataConnectExecutable.set(ext.dataConnectExecutable)
              }
            }
          }

          fun withGradlePropertyIfSet(propertyName: String, block: (Provider<String>) -> Unit) {
            val provider = project.providers.gradleProperty(propertyName)
            logger.info("Loaded gradle property {}: {}", propertyName, provider.orNull)
            if (provider.isPresent) {
              block(provider)
            }
          }

          withGradlePropertyIfSet(DATA_CONNECT_EXECUTABLE_PROPERTY_NAME) { provider ->
            dataConnectExecutable.set(provider.map { project.layout.projectDirectory.file(it) })
          }
          withGradlePropertyIfSet(DATA_CONNECT_CONFIG_DIR_PROPERTY_NAME) { provider ->
            customConfigDirectory.set(provider.map { project.layout.projectDirectory.dir(it) })
          }
          withGradlePropertyIfSet(DATA_CONNECT_CONNECTORS_PROPERTY_NAME) { provider ->
            connectors.set(provider.map { it.split(",") })
          }
        }

      variant.sources.java!!.addGeneratedSourceDirectory(
        generateCodeTask,
        DataConnectGenerateCodeTask::outputDirectory
      )
    }
  }

  private fun loadLocalProperties(project: Project, file: File): DataConnectDslExtension? {
    val properties = Properties()

    try {
      logger.info("Loading properties from file: {}", file)
      file.inputStream().use { properties.load(it) }
    } catch (e: FileNotFoundException) {
      logger.info("Properties file not found; ignoring ({})", file)
      return null
    }

    val dataConnectDslExtension = objectFactory.newInstance<DataConnectDslExtension>()
    fun Map.Entry<*, Any>.asConfigDir(): File = project.file(value)
    fun Map.Entry<*, Any>.asConnectors(): List<String> = value.toString().split(",")
    fun Map.Entry<*, Any>.asDataConnectExecutable(): File = project.file(value)

    for (property in properties) {
      when (property.key) {
        "configDir" -> dataConnectDslExtension.configDir = property.asConfigDir()
        "connectors" -> dataConnectDslExtension.connectors = property.asConnectors()
        "dataConnectExecutable" ->
          dataConnectDslExtension.dataConnectExecutable = property.asDataConnectExecutable()
        "codegen.configDir" -> dataConnectDslExtension.codegen.configDir = property.asConfigDir()
        "codegen.connectors" -> dataConnectDslExtension.codegen.connectors = property.asConnectors()
        "codegen.dataConnectExecutable" ->
          dataConnectDslExtension.codegen.dataConnectExecutable = property.asDataConnectExecutable()
        "emulator.configDir" -> dataConnectDslExtension.emulator.configDir = property.asConfigDir()
        "emulator.connectors" ->
          dataConnectDslExtension.emulator.connectors = property.asConnectors()
        "emulator.dataConnectExecutable" ->
          dataConnectDslExtension.emulator.dataConnectExecutable =
            property.asDataConnectExecutable()
      }
    }

    return dataConnectDslExtension
  }
}

private const val DATA_CONNECT_EXECUTABLE_PROPERTY_NAME = "DATA_CONNECT_EXECUTABLE"
private const val DATA_CONNECT_CONFIG_DIR_PROPERTY_NAME = "DATA_CONNECT_CONFIG_DIR"
private const val DATA_CONNECT_CONNECTORS_PROPERTY_NAME = "DATA_CONNECT_CONNECTORS"
