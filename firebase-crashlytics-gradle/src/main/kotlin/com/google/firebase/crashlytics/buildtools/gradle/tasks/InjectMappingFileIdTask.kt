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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/** Inject mapping file id task. */
@CacheableTask
abstract class InjectMappingFileIdTask : DefaultTask() {
  @get:Internal abstract val useBlankMappingFileId: Property<Boolean>
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
        // Reuse the on-disk id when valid so release builds stay reproducible and don't invalidate
        // downstream tasks (mergeResources, R8, packaging, bundling). A fresh UUID is only minted
        // on the first build or after `clean`.
        existingMappingFileId()?.takeUnless { it == CrashlyticsBuildtools.BLANK_MAPPING_FILE_ID }
          ?: CrashlyticsBuildtools.generateMappingFileId()
      }
    mappingFileIdFile.get().asFile.writeText(mappingFileId)

    CrashlyticsBuildtools.injectMappingFileIdIntoResource(
      resourceFile = File(resourceDir.get().asFile, "values/$MAPPING_FILE_ID_RESOURCE_FILENAME"),
      mappingFileId,
    )
  }

  /**
   * Returns the mapping file id currently stored on disk, or null if the file is missing or empty.
   * Used to keep the task up-to-date across builds and to avoid generating a new random id on
   * every release build.
   */
  private fun existingMappingFileId(): String? {
    val file: File = mappingFileIdFile.get().asFile
    if (!file.exists()) return null
    return file.readText().trim().ifEmpty { null }
  }

  internal companion object {
    @Suppress("UnstableApiUsage") // isMinifyEnabled
    fun register(
      project: Project,
      variant: ApplicationVariant,
      crashlyticsExtension: CrashlyticsVariantExtension,
    ): TaskProvider<InjectMappingFileIdTask> {
      val injectMappingFileIdTaskProvider =
        project.tasks.register<InjectMappingFileIdTask>(
          "injectCrashlyticsMappingFileId${variant.name.capitalized()}"
        ) {
          this.useBlankMappingFileId.set(
            !crashlyticsExtension.mappingFileUploadEnabled.getOrElse(variant.isMinifyEnabled)
          )
          this.mappingFileIdFile.set(buildFile(project, variant, "mappingFileId.txt"))

          outputs.upToDateWhen {
            val existing = existingMappingFileId()
            if (useBlankMappingFileId.get()) {
              existing == CrashlyticsBuildtools.BLANK_MAPPING_FILE_ID
            } else {
              existing != null && existing != CrashlyticsBuildtools.BLANK_MAPPING_FILE_ID
            }
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
