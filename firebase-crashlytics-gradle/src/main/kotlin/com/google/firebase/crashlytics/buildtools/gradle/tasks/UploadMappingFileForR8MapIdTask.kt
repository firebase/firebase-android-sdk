/*
 * Copyright 2026 Google LLC
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
import java.io.File
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

/**
 * Uploads the obfuscation mapping file for AGP 8.12+ builds.
 *
 * Starting with AGP 8.12, R8 stamps a stable, content-derived id (`r8_map_id`/`pg_map_id`) into the
 * merged mapping file as a comment. It stays identical across rebuilds while the obfuscated output
 * is unchanged and changes only when R8's output changes, so it is a stable handle for the uploaded
 * mapping and keeps the release build cache valid (#6770).
 *
 * This task reads that id straight out of the mapping file and uploads with it. The id reaches
 * crash reports through the `r8-map-id` that R8 embeds in the obfuscated stack frames.
 */
@DisableCachingByDefault(because = "Uploading to network")
abstract class UploadMappingFileForR8MapIdTask : DefaultTask() {
  @get:[InputFile PathSensitive(PathSensitivity.NONE) SkipWhenEmpty]
  abstract val mergedMappingFile: RegularFileProperty
  @get:[InputFile PathSensitive(PathSensitivity.NONE) Optional]
  abstract val appIdFile: RegularFileProperty
  @get:Internal abstract val buildDir: DirectoryProperty

  init {
    group = CRASHLYTICS_TASK_GROUP
    description = "Uploads mapping files to Crashlytics for crash deobfuscation."
  }

  @TaskAction
  fun uploadMappingFile() {
    val mappingFile = mergedMappingFile.get().asFile
    val mappingFileId = extractR8MapId(mappingFile)
    if (mappingFileId == null) {
      logger.warn(
        "Crashlytics could not find an r8 map id (`r8_map_id`/`pg_map_id`) in ${mappingFile.path}; " +
          "skipping mapping file upload. This id is emitted by R8 in AGP 8.12 and above when " +
          "obfuscation is enabled."
      )
      return
    }
    try {
      val appId = appIdFile.get().asFile.readText()
      // TODO (rothbutter) Vendor field is always assumed to be PROGUARD
      CrashlyticsBuildtools.uploadMappingFile(
        mappingFile,
        mappingFileId = mappingFileId,
        AppBuildInfo("", appId, buildDir.get().asFile),
        Obfuscator(Obfuscator.Vendor.PROGUARD, "0.0"),
      )
    } catch (ex: ZeroByteFileException) {
      logger.warn(
        ex.message,
        "(Use -Pcom.google.firebase.crashlytics.suppressWarnings=true to suppress this warning)",
      )
    }
  }

  internal companion object {
    // R8 stamps the map id in the mapping file header, e.g. `# r8_map_id: <hex>` and/or
    // `# pg_map_id: <hex>`. Prefer r8_map_id — it is the full map hash matching the r8-map-id R8
    // embeds in obfuscated stack frames (what the backend extracts); pg_map_id is the fallback for
    // older mapping files.
    private val MAP_ID_REGEX = Regex("""^#\s*(r8_map_id|pg_map_id):\s*([0-9a-fA-F]+)\s*$""")

    /**
     * Returns the r8 map id from an R8 mapping file (prefers r8_map_id), or null if not present.
     */
    fun extractR8MapId(mappingFile: File): String? {
      var r8MapId: String? = null
      var pgMapId: String? = null
      mappingFile.useLines { lines ->
        for (line in lines) {
          if (line.isBlank()) continue
          if (!line.startsWith("#")) break // header comments precede the first class mapping
          val match = MAP_ID_REGEX.find(line) ?: continue
          when (match.groupValues[1]) {
            "r8_map_id" -> r8MapId = match.groupValues[2]
            "pg_map_id" -> pgMapId = match.groupValues[2]
          }
          if (r8MapId != null) break // preferred id found
        }
      }
      return r8MapId ?: pgMapId
    }

    fun register(
      project: Project,
      variant: Variant
    ): TaskProvider<UploadMappingFileForR8MapIdTask> {
      val uploadMappingFileTaskProvider =
        project.tasks.register<UploadMappingFileForR8MapIdTask>(
          "uploadCrashlyticsMappingFile${variant.name.capitalized()}"
        ) {
          this.mergedMappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
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
    logger.debug("UploadMappingFileForR8MapIdTask:")
    logger.debug("  mergedMappingFile: ${mergedMappingFile.orNull?.asFile?.path}")
    logger.debug("  appIdFile: ${appIdFile.orNull?.asFile?.path}")
    logger.debug("  buildDir: ${buildDir.orNull?.asFile?.path}")
  }
}
