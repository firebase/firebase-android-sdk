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
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.register

@Suppress("unused")
class DataConnectGradlePlugin : Plugin<Project> {

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

      val generateCodeTask =
        project.tasks.register<DataConnectGenerateCodeTask>(
          "generate${variantNameTitleCase}DataConnectSources"
        ) {
          workDirectory.set(
            project.layout.buildDirectory.dir("intermediates/dataconnect/${variant.name}")
          )

          val android = project.extensions.getByType(LibraryExtension::class.java) as ExtensionAware
          android.extensions.getByType(DataConnectDslExtension::class.java).let { dataConnectDslExtension ->
            dataConnectDslExtension.configDir?.let {
              customConfigDirectory.convention(project.layout.projectDirectory.dir(it.path))
            }
            dataConnectDslExtension.connectors?.let {
              connectors.convention(it)
            }
            dataConnectDslExtension.dataConnectCliExecutable?.let {
              dataConnectCliExecutable.convention(project.layout.projectDirectory.file(it.path))
            }
          }

          variant.getExtension(DataConnectVariantDslExtension::class.java)!!.also {
            defaultConfigDirectories.set(variant.sources.getByName("dataconnect").all)
            customConfigDirectory.set(it.configDir)
            connectors.set(it.connectors)
            dataConnectCliExecutable.set(it.dataConnectCliExecutable)
          }
        }

      variant.sources.kotlin!!.addGeneratedSourceDirectory(
        generateCodeTask,
        DataConnectGenerateCodeTask::outputDirectory
      )
    }
  }
}
