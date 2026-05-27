/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.gradle.tasks

import com.android.build.api.variant.ApplicationVariant
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsBuildtools
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin.Companion.buildFile
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsVariantExtension
import com.google.firebase.crashlytics.buildtools.gradle.extensions.capitalized
import com.google.firebase.crashlytics.buildtools.mappingfiles.MappingFileIdWriter.MAPPING_FILE_ID_RESOURCE_FILENAME
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/** Inject mapping file id task. */
@CacheableTask
abstract class InjectMappingFileIdTask : DefaultTask() {
  @get:Input abstract val useBlankMappingFileId: Property<Boolean>

  @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
  abstract val obfuscatableSources: ConfigurableFileCollection

  @get:[InputFiles PathSensitive(PathSensitivity.NONE)]
  abstract val obfuscatableClasspath: ConfigurableFileCollection

  @get:OutputFile abstract val mappingFileIdFile: RegularFileProperty
  @get:OutputDirectory abstract val resourceDir: DirectoryProperty

  init {
    group = CrashlyticsPlugin.CRASHLYTICS_TASK_GROUP
    description =
      "Generates and injects a mapping file id into the app, used by Crashlytics for deobfuscation."
  }

  @TaskAction
  fun injectMappingFileId() {
    val mappingFileId =
      if (useBlankMappingFileId.get()) {
        CrashlyticsBuildtools.BLANK_MAPPING_FILE_ID
      } else {
        CrashlyticsBuildtools.generateMappingFileId()
      }
    mappingFileIdFile.get().asFile.writeText(mappingFileId)

    CrashlyticsBuildtools.injectMappingFileIdIntoResource(
      resourceFile = File(resourceDir.get().asFile, "values/$MAPPING_FILE_ID_RESOURCE_FILENAME"),
      mappingFileId,
    )
  }

  internal companion object {
    @Suppress("UnstableApiUsage") // isMinifyEnabled, compileClasspath
    fun register(
      project: Project,
      variant: ApplicationVariant,
      crashlyticsExtension: CrashlyticsVariantExtension,
    ): TaskProvider<InjectMappingFileIdTask> {
      val useBlank =
        !crashlyticsExtension.mappingFileUploadEnabled.getOrElse(variant.isMinifyEnabled)

      val injectMappingFileIdTaskProvider =
        project.tasks.register<InjectMappingFileIdTask>(
          "injectCrashlyticsMappingFileId${variant.name.capitalized()}"
        ) {
          this.useBlankMappingFileId.set(useBlank)
          this.mappingFileIdFile.set(buildFile(project, variant, "mappingFileId.txt"))

          // Only fingerprint inputs when obfuscation is on. In blank-id mode the id is constant
          // and source/classpath changes are irrelevant to the mapping handle.
          //
          // Discover user source files via project.fileTree("src") rather than
          // variant.sources.java/kotlin.all. AGP's accessor includes generated source dirs
          // (R.java, deeplinks, view-binding, etc.) whose producer tasks depend on the same
          // mergeResources pipeline that consumes THIS task's output, which would close a cycle.
          // AGP 8.1.4 has no `static` getter (added in 8.6) that would expose the non-generated
          // subset, so we hand-roll the discovery from on-disk source-set conventions.
          if (!useBlank) {
            this.obfuscatableSources.from(
              project.fileTree("src").matching { patterns ->
                patterns.include(
                  "**/java/**/*.java",
                  "**/java/**/*.kt",
                  "**/kotlin/**/*.java",
                  "**/kotlin/**/*.kt",
                )
                patterns.exclude("test/**", "androidTest/**", "test*/**", "androidTest*/**")
              }
            )
          }
        }

      // It is not possible to disable Android resources in the AGP app plugin.
      variant.sources.res?.addGeneratedSourceDirectory(
        injectMappingFileIdTaskProvider,
        InjectMappingFileIdTask::resourceDir,
      )

      return injectMappingFileIdTaskProvider
    }
  }
}
