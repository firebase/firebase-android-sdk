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

import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.gradle.LibraryPlugin
import java.io.File
import java.util.Locale
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

@Suppress("unused", "UnstableApiUsage")
class DataConnectGradlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.plugins.withType(LibraryPlugin::class.java) { _ ->
      val androidComponents =
        project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)

      androidComponents.registerExtension(
        DslExtension.Builder("dataconnect")
          .extendProjectWith(DataConnectDslExtension::class.java)
          .extendBuildTypeWith(DataConnectDslExtension::class.java)
          .extendProductFlavorWith(DataConnectDslExtension::class.java)
          .build()
      ) { config: VariantExtensionConfig<*> ->
        println(
          "zzyzx androidComponents.registerExtension() callback starting for variant: ${config.variant.name}"
        )
        project.objects.newInstance(DataConnectVariantDslExtension::class.java, config)
      }

      println("zzyzx androidComponents.registerExtension() called")

      androidComponents.onVariants { variant ->
        val variantNameTitleCase = variant.name.replaceFirstChar { it.titlecase(Locale.US) }
        println(
          "zzyzx androidComponents.onVariants() callback starting for variant: ${variant.name}"
        )

        val generateCodeTask =
          project.tasks.register<DataConnectGenerateCodeTask>(
            "generate${variantNameTitleCase}DataConnectSources"
          ) {
            // Use src/main/dataconnect, src/debug/dataconnect, etc. as the "input" directories.
            // These directories will be merged into a single directory using the same scheme as
            // Java sources. Find these "input" directories relative to the "assets" directories.
            inputDirectories.set(
              variant.sources.assets!!.all.map { directoryCollections ->
                directoryCollections.map { directories ->
                  directories.map { directory -> directory.dir("../dataconnect") }
                }
              }
            )

            // Use a directory in the "build" directory for writing the result of merging the
            // "input" directories.
            mergedInputsDirectory.set(
              project.layout.buildDirectory.dir(
                "intermediates/dataconnect/mergedSources/${variant.name}"
              )
            )

            dataConnectCliExecutable.set(
              File(
                "/google/src/cloud/dconeybe/codegen/google3/" +
                  "blaze-bin/third_party/firebase/dataconnect/emulator/cli/cli"
              )
            )

            // Provide a reference to the variant extension, from which the task can retrieve
            // settings set or overridden by the caller.
            variantExtension.set(variant.getExtension(DataConnectVariantDslExtension::class.java))
          }

        variant.sources.kotlin!!.addGeneratedSourceDirectory(
          generateCodeTask,
          DataConnectGenerateCodeTask::outputDirectory
        )
      }

      println("zzyzx androidComponents.onVariants() called")
    }
  }
}
