/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *
 */

package com.google.firebase.crashlytics.buildtools.gradle.tasks

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.Variant
import com.google.firebase.crashlytics.buildtools.buildids.BuildIdsWriter.BUILD_IDS_RESOURCE_FILENAME
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsBuildtools
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin.Companion.CRASHLYTICS_TASK_GROUP
import com.google.firebase.crashlytics.buildtools.gradle.extensions.capitalized
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/** Adds the mappingFileId as a String resource to the project's Android resource (XML) file */
@Suppress("UnstableApiUsage") // SingleArtifact.MERGED_NATIVE_LIBS
@CacheableTask
abstract class InjectBuildIdsTask : DefaultTask() {
  @get:[InputDirectory PathSensitive(PathSensitivity.RELATIVE) SkipWhenEmpty]
  abstract val mergedNativeLibsDirs: DirectoryProperty
  @get:OutputDirectory abstract val resourceDir: DirectoryProperty

  init {
    group = CRASHLYTICS_TASK_GROUP
    description =
      "Adds the mappingFileId as a String resource to the project's Android resource (XML) file."
  }

  @TaskAction
  fun injectBuildIds() {
    CrashlyticsBuildtools.injectBuildIdsIntoResource(
      mergedNativeLibsDirs.get().asFile,
      resourceFile = File(resourceDir.get().asFile, "values/$BUILD_IDS_RESOURCE_FILENAME"),
    )
  }

  companion object {
    fun register(project: Project, variant: Variant): TaskProvider<InjectBuildIdsTask> {
      val provider =
        project.tasks.register<InjectBuildIdsTask>(
          "injectCrashlyticsBuildIds${variant.name.capitalized()}"
        ) {
          mergedNativeLibsDirs.set(variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS))
          printDebugProperties()
        }

      variant.sources.res?.addGeneratedSourceDirectory(provider, InjectBuildIdsTask::resourceDir)

      return provider
    }
  }

  private fun printDebugProperties() {
    logger.debug("InjectBuildIdsTask:")
    logger.debug("  mergedNativeLibsDirs: ${mergedNativeLibsDirs.orNull?.asFile?.path}")
    logger.debug("  resourceDir: ${resourceDir.orNull?.asFile?.path}")
  }
}
