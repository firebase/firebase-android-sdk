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

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
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
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/** Inject mapping file id task. */
@CacheableTask
abstract class InjectMappingFileIdTask : DefaultTask() {
  @get:Input abstract val useBlankMappingFileId: Property<Boolean>

  // When obfuscation is on, the mapping id must change whenever the obfuscated output could change,
  // and stay stable otherwise (so it doesn't invalidate the cached release pipeline on every
  // build).
  //
  // We deliberately do NOT fingerprint the obfuscated classes directly (ScopedArtifact.CLASSES):
  // this task contributes the id as a generated `res` dir, which feeds mergeResources, and the
  // compiled classes are produced downstream of that (res -> mergeResources -> R.jar -> compile).
  // Wiring CLASSES as an input therefore closes a task-dependency cycle on every app. Instead we
  // fingerprint the cycle-free, upstream inputs that determine the mapping.
  @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
  abstract val obfuscatableSources: ConfigurableFileCollection

  // External runtime classpath: changes here (dependency or toolchain version bumps) change the
  // obfuscated output even when no source file changes.
  @get:Classpath abstract val obfuscatableClasspath: ConfigurableFileCollection

  // ProGuard/R8 configuration: rule changes change the mapping.
  @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
  abstract val proguardConfigFiles: ConfigurableFileCollection

  // AGP version ships R8; a toolchain bump can change the mapping with no other input change.
  @get:[Input Optional]
  abstract val androidGradlePluginVersion: Property<String>

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
    @Suppress(
      "UnstableApiUsage"
    ) // isMinifyEnabled, runtimeConfiguration, proguardFiles, pluginVersion
    fun register(
      project: Project,
      variant: ApplicationVariant,
      crashlyticsExtension: CrashlyticsVariantExtension,
    ): TaskProvider<InjectMappingFileIdTask> {
      val useBlank =
        !crashlyticsExtension.mappingFileUploadEnabled.getOrElse(variant.isMinifyEnabled)

      val agpVersion =
        project.extensions.getByType<ApplicationAndroidComponentsExtension>().pluginVersion.let {
          "${it.major}.${it.minor}.${it.micro}"
        }

      val injectMappingFileIdTaskProvider =
        project.tasks.register<InjectMappingFileIdTask>(
          "injectCrashlyticsMappingFileId${variant.name.capitalized()}"
        ) {
          this.useBlankMappingFileId.set(useBlank)
          this.mappingFileIdFile.set(buildFile(project, variant, "mappingFileId.txt"))

          // Only fingerprint inputs when obfuscation is on. In blank-id mode the id is constant, so
          // source/classpath/rule changes are irrelevant to the mapping handle.
          //
          // User sources are discovered via project.fileTree("src") rather than
          // variant.sources.java/kotlin.all: AGP's `all` accessor includes generated source dirs
          // (view-binding, etc.) whose producer tasks depend on the same mergeResources pipeline
          // that consumes THIS task's output, which would close a cycle. The `static` accessor that
          // would expose only the non-generated subset was added in AGP 8.6, but this plugin
          // compiles against the 8.1 API, so we hand-roll the discovery from source-set
          // conventions.
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
            this.obfuscatableClasspath.from(variant.runtimeConfiguration)
            this.proguardConfigFiles.from(variant.proguardFiles)
            this.androidGradlePluginVersion.set(agpVersion)
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
