/*
 * Copyright 2025 Google LLC
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
import com.android.build.api.variant.Variant
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
import com.google.firebase.crashlytics.buildtools.gradle.extensions.capitalized
import java.io.File
import java.io.IOException
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/**
 * Task that injects version control info as an Android string resource.
 *
 * The version control info is used by App Quality Insights. If none is found, this task injects an
 * empty string to prevent the SDK from falling back to reading the file, which is slower.
 */
@CacheableTask
abstract class InjectVersionControlInfoTask : DefaultTask() {
  @get:[InputFile Optional PathSensitive(PathSensitivity.NAME_ONLY)]
  abstract val versionControlInfoFile: RegularFileProperty

  /**
   * This property helps Gradle cache the task correctly, since the [versionControlInfoFile] might
   * not even exist. We always need this task to run, even if there's no file, so we can output an
   * empty string. This way, the SDK consuming this resource doesn't have to fall back to a slower
   * file system read. Marking the file's presence (or absence) as an `@Input` means Gradle knows
   * exactly when to re-run or use a cached result. Thi is a cleaner way to handle optional inputs
   * and caching without getting into complex custom `upToDateWhen` logic.
   */
  @get:Input
  val hasVersionControlInfoFile
    get() = versionControlInfoFile.isPresent

  @get:OutputDirectory abstract val resourceDir: DirectoryProperty

  init {
    group = CrashlyticsPlugin.CRASHLYTICS_TASK_GROUP
    description = "Injects the version control info into the app. Used by App Quality Insights."
  }

  @TaskAction
  fun injectVersionControlInfo() {
    val versionControlInfo =
      try {
        if (hasVersionControlInfoFile) {
          versionControlInfoFile.asFile.get().readText()
        } else {
          ""
        }
      } catch (ex: IOException) {
        logger.warn("Failed to read version control info from file", ex)
        ""
      }

    val output =
      """
      <resources>
        <string name="com.google.firebase.crashlytics.version_control_info">${escapeXml(versionControlInfo)}</string>
      </resources>
      """
        .trimIndent()

    try {
      val valuesDir = File(resourceDir.get().asFile, "values/")
      valuesDir.mkdirs()
      val file = File(valuesDir, VERSION_CONTROL_INFO_RESOURCE_FILENAME)
      file.writeText(output)
    } catch (ex: IOException) {
      logger.warn(
        "Could not write version control info into $VERSION_CONTROL_INFO_RESOURCE_FILENAME",
        ex,
      )
    }
  }

  /** Escapes [text] for use as an XML string value enclosed in quotes. */
  private fun escapeXml(text: String): String {
    return "\"${text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "\\\"") // &quot; would end the attribute prematurely
      .replace("'", "&apos;")
      .replace("\n", "\\n")}\"" // Optional, keeps output on one line
  }

  internal companion object {
    private const val VERSION_CONTROL_INFO_RESOURCE_FILENAME: String =
      "com_google_firebase_crashlytics_versioncontrolinfo.xml"

    /** Registers the [InjectVersionControlInfoTask] for the given Android application [variant]. */
    fun register(
      project: Project,
      variant: ApplicationVariant,
    ): TaskProvider<InjectVersionControlInfoTask> {
      val injectVersionControlInfoTaskProvider =
        project.tasks.register<InjectVersionControlInfoTask>(
          "injectCrashlyticsVersionControlInfo${variant.name.capitalized()}"
        ) {
          // This may fail if AGP changes its internal APIs, so treat it as best-effort
          // The retrieved provider is set as the optional input for this task
          getVcInfoFileProvider(project, variant)?.let(versionControlInfoFile::set)
        }

      variant.sources.res?.addGeneratedSourceDirectory(
        injectVersionControlInfoTaskProvider,
        InjectVersionControlInfoTask::resourceDir,
      )

      return injectVersionControlInfoTaskProvider
    }

    /**
     * Safely retrieves the version control info file [Provider] from the internal AGP task, or
     * returns `null` if the AGP task isn't found or its API changes.
     *
     * This function handles potential changes in AGP's internal APIs by gracefully falling back.
     * This mechanism will no longer be needed after https://issuetracker.google.com/418029665,
     * which will provide a public API for this information.
     */
    private fun getVcInfoFileProvider(project: Project, variant: Variant): Provider<RegularFile>? =
      try {
        project.tasks.named("extract${variant.name.capitalized()}VersionControlInfo").flatMap {
          versionControlTask ->
          try {
            val vcInfoFile = versionControlTask::class.java.getMethod("getVcInfoFile")
            vcInfoFile.invoke(versionControlTask) as RegularFileProperty
          } catch (_: NoSuchMethodException) {
            // The getVcInfoFile method might change in AGP, so fallback to an empty file
            ensureEmptyFile(CrashlyticsPlugin.buildFile(project, variant, "vcInfoFile"))
          }
        }
      } catch (_: UnknownTaskException) {
        // The extract version control info task might not be defined for this variant
        null
      }

    /**
     * Ensures the file specified by the [fileProvider] exists and is empty.
     *
     * This is used as a fallback to provide an empty file when AGP's internal API for version
     * control info extraction is unavailable or changes. The task will then read this empty file
     * and inject an empty string resource.
     */
    private fun ensureEmptyFile(fileProvider: Provider<RegularFile>): Provider<RegularFile> {
      return fileProvider.also {
        try {
          val file = it.get().asFile
          file.parentFile.mkdirs()
          // Creates or overwrites with empty content
          file.writeText("")
        } catch (_: IOException) {
          // Failed to create the file; let it continue silently
          // This path should not be hit if parent directories can be created
        }
      }
    }
  }
}
