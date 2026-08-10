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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/**
 * Injects a mapping file id into the app's Android resources, read by the Crashlytics SDK at
 * runtime for deobfuscation. The id source is chosen by the AGP version when the task is
 * registered:
 * - **AGP 8.12+** ([registerForR8MapId]): a fixed value — the [CrashlyticsBuildtools.USE_R8_MAP_ID]
 * that signals the SDK to use R8's `r8-map-id`, or [CrashlyticsBuildtools.BLANK_MAPPING_FILE_ID]
 * when no mapping is uploaded. Being constant, the resource is byte-identical across rebuilds and
 * does not invalidate the release build cache (#6770).
 * - **AGP < 8.12** ([register]): a blank id when no mapping is uploaded, otherwise a fresh random
 * id generated on every build.
 */
@CacheableTask
abstract class InjectMappingFileIdTask : DefaultTask() {
  /** Fixed id to inject. When unset, a fresh random id is generated on every run. */
  @get:[Input Optional]
  abstract val mappingFileId: Property<String>

  /** Optional id text file; written for consumers that read the id from disk (the upload task). */
  @get:[OutputFile Optional]
  abstract val mappingFileIdFile: RegularFileProperty

  @get:OutputDirectory abstract val resourceDir: DirectoryProperty

  init {
    group = CrashlyticsPlugin.CRASHLYTICS_TASK_GROUP
    description =
      "Generates and injects a mapping file id into the app, used by Crashlytics for deobfuscation."
  }

  @TaskAction
  fun injectMappingFileId() {
    val id = mappingFileId.orNull ?: CrashlyticsBuildtools.generateMappingFileId()
    if (mappingFileIdFile.isPresent) {
      mappingFileIdFile.get().asFile.writeText(id)
    }
    CrashlyticsBuildtools.injectMappingFileIdIntoResource(
      resourceFile = File(resourceDir.get().asFile, "values/$MAPPING_FILE_ID_RESOURCE_FILENAME"),
      id,
    )
  }

  internal companion object {
    /**
     * Registers the task for AGP < 8.12: a blank id when no mapping is uploaded, otherwise a fresh
     * random id on every build.
     */
    @Suppress("UnstableApiUsage") // isMinifyEnabled
    fun register(
      project: Project,
      variant: ApplicationVariant,
      crashlyticsExtension: CrashlyticsVariantExtension,
    ): TaskProvider<InjectMappingFileIdTask> =
      registerTask(project, variant) {
        if (!crashlyticsExtension.mappingFileUploadEnabled.getOrElse(variant.isMinifyEnabled)) {
          this.mappingFileId.set(CrashlyticsBuildtools.BLANK_MAPPING_FILE_ID)
        }
        this.mappingFileIdFile.set(buildFile(project, variant, "mappingFileId.txt"))
        // A fixed id (blank) stays up to date; an unset id regenerates on every build.
        outputs.upToDateWhen { mappingFileId.isPresent }
      }

    /**
     * Registers the task for AGP 8.12+: the constant r8-map-id sentinel, or a blank id when no
     * mapping is uploaded.
     */
    fun registerForR8MapId(
      project: Project,
      variant: ApplicationVariant,
      mappingFileUploadEnabled: Boolean,
    ): TaskProvider<InjectMappingFileIdTask> =
      registerTask(project, variant) {
        this.mappingFileId.set(
          if (mappingFileUploadEnabled) CrashlyticsBuildtools.USE_R8_MAP_ID
          else CrashlyticsBuildtools.BLANK_MAPPING_FILE_ID
        )
      }

    @Suppress("UnstableApiUsage")
    private fun registerTask(
      project: Project,
      variant: ApplicationVariant,
      configure: InjectMappingFileIdTask.() -> Unit,
    ): TaskProvider<InjectMappingFileIdTask> {
      val provider =
        project.tasks.register<InjectMappingFileIdTask>(
          "injectCrashlyticsMappingFileId${variant.name.capitalized()}",
          configure,
        )

      // It is not possible to disable Android resources in the AGP app plugin.
      variant.sources.res?.addGeneratedSourceDirectory(
        provider,
        InjectMappingFileIdTask::resourceDir
      )

      return provider
    }
  }
}
