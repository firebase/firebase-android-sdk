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
import com.google.firebase.crashlytics.buildtools.AppBuildInfo
import com.google.firebase.crashlytics.buildtools.Obfuscator
import com.google.firebase.crashlytics.buildtools.exception.ZeroByteFileException
import com.google.firebase.crashlytics.buildtools.gradle.AppIdFetcher
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsBuildtools
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin.Companion.CRASHLYTICS_TASK_GROUP
import com.google.firebase.crashlytics.buildtools.gradle.extensions.capitalized
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Uploading to network")
abstract class UploadMappingFileTask : DefaultTask() {
  @get:[InputFile PathSensitive(PathSensitivity.NAME_ONLY) SkipWhenEmpty]
  abstract val mergedMappingFile: RegularFileProperty
  @get:[InputFile PathSensitive(PathSensitivity.NONE)]
  abstract val mappingFileIdFile: RegularFileProperty
  @get:[InputFile PathSensitive(PathSensitivity.NONE) Optional]
  abstract val appIdFile: RegularFileProperty
  @get:Internal abstract val buildDir: DirectoryProperty

  init {
    group = CRASHLYTICS_TASK_GROUP
    description = "Uploads mapping files to Crashlytics for crash deobfuscation."
  }

  @TaskAction
  fun uploadMappingFile() =
    try {
      val appId = appIdFile.get().asFile.readText()
      val mappingFile = mergedMappingFile.get().asFile
      // TODO (rothbutter) Vendor field is always assumed to be PROGUARD
      CrashlyticsBuildtools.uploadMappingFile(
        mappingFile,
        mappingFileId = mappingFileIdFile.get().asFile.readText(),
        AppBuildInfo("", appId, buildDir.get().asFile),
        Obfuscator(Obfuscator.Vendor.PROGUARD, "0.0"),
      )
    } catch (ex: ZeroByteFileException) {
      logger.warn(
        ex.message,
        "(Use -Pcom.google.firebase.crashlytics.suppressWarnings=true to suppress this warning)",
      )
    }

  internal companion object {
    fun register(
      project: Project,
      variant: Variant,
      injectMappingFileIdTask: TaskProvider<InjectMappingFileIdTask>,
    ): TaskProvider<UploadMappingFileTask> {
      val uploadMappingFileTaskProvider =
        project.tasks.register<UploadMappingFileTask>(
          "uploadCrashlyticsMappingFile${variant.name.capitalized()}"
        ) {
          this.mergedMappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
          this.mappingFileIdFile.set(injectMappingFileIdTask.flatMap { it.mappingFileIdFile })
          this.appIdFile.set(AppIdFetcher.getGoogleServicesAppId(project, variant))

          this.buildDir.set(CrashlyticsPlugin.buildDir(project, variant))

          printDebugProperties()
        }

      // Do this outside of the configuration lambda because some lazy loading is happening.
      project.afterEvaluate {
        it.tasks
          .findByPath("minify${variant.name.capitalized()}WithR8")
          ?.finalizedBy(uploadMappingFileTaskProvider)
      }

      return uploadMappingFileTaskProvider
    }
  }

  private fun printDebugProperties() {
    logger.debug("UploadMappingFileTask:")
    logger.debug("  mergedMappingFile: ${mergedMappingFile.orNull?.asFile?.path}")
    logger.debug("  mappingFileIdFile: ${mappingFileIdFile.orNull?.asFile?.path}")
    logger.debug("  appIdFile: ${appIdFile.orNull?.asFile?.path}")
    logger.debug("  buildDir: ${buildDir.orNull?.asFile?.path}")
  }
}
