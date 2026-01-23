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

package com.google.firebase.crashlytics.buildtools.gradle

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.google.firebase.crashlytics.buildtools.gradle.tasks.GenerateSymbolFileTask
import com.google.firebase.crashlytics.buildtools.gradle.tasks.InjectBuildIdsTask
import com.google.firebase.crashlytics.buildtools.gradle.tasks.InjectMappingFileIdTask
import com.google.firebase.crashlytics.buildtools.gradle.tasks.InjectVersionControlInfoTask
import com.google.firebase.crashlytics.buildtools.gradle.tasks.UploadMappingFileTask
import com.google.firebase.crashlytics.buildtools.gradle.tasks.UploadSymbolFileTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.util.GradleVersion

@Suppress("UnstableApiUsage") // The AGP extension api.
class CrashlyticsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    validateDependencies(project)

    // The rest of the setup must occur after the Android Application plugin has been applied.
    var hasAppPluginApplied = false
    project.plugins.withType<AppPlugin> {
      hasAppPluginApplied = true
      val androidComponents = project.extensions.getByType<ApplicationAndroidComponentsExtension>()
      validateDependencies(androidComponents)
      registerExtension(project, androidComponents)
      registerTasks(project, androidComponents)
      CrashlyticsBuildtools.configure(project)
    }

    project.afterEvaluate {
      if (!hasAppPluginApplied) {
        throw GradleException(
          "Crashlytics requires the `com.android.application` plugin to be applied"
        )
      }
    }
  }

  private fun validateDependencies(project: Project) {
    if (GradleVersion.version(project.gradle.gradleVersion) < GradleVersion.version("8.0")) {
      throw GradleException(
        "The Crashlytics Gradle plugin 3 requires Gradle 8.0 or above. $UPGRADE_MSG"
      )
    }

    project.plugins.withType<LibraryPlugin> {
      throw GradleException(
        "Applying the Firebase Crashlytics plugin to a library project is unsupported. " +
          "It should only be applied to the application module of your project."
      )
    }
  }

  private fun validateDependencies(androidComponents: ApplicationAndroidComponentsExtension) {
    if (androidComponents.pluginVersion < AndroidPluginVersion(8, 1)) {
      throw GradleException(
        "The Crashlytics Gradle plugin 3 requires `com.android.application` version 8.1 or above. $UPGRADE_MSG"
      )
    }
  }

  private fun registerExtension(
    project: Project,
    androidComponents: ApplicationAndroidComponentsExtension,
  ) {
    androidComponents.registerExtension(
      DslExtension.Builder(CRASHLYTICS_EXTENSION_NAME)
        .extendBuildTypeWith(CrashlyticsExtension::class.java)
        .extendProductFlavorWith(CrashlyticsExtension::class.java)
        .build()
    ) { config: VariantExtensionConfig<*> ->
      project.objects.newInstance(CrashlyticsVariantExtension::class.java, config)
    }
  }

  private fun registerTasks(
    project: Project,
    androidComponents: ApplicationAndroidComponentsExtension,
  ) {
    androidComponents.onVariants { variant ->
      val crashlyticsExtension: CrashlyticsVariantExtension =
        variant.getExtension(CrashlyticsVariantExtension::class.java)
          ?: project.objects.newInstance(CrashlyticsVariantExtension::class.java)

      InjectVersionControlInfoTask.register(project, variant)

      val injectMappingFileIdTask: TaskProvider<InjectMappingFileIdTask> =
        InjectMappingFileIdTask.register(project, variant, crashlyticsExtension)

      if (crashlyticsExtension.mappingFileUploadEnabled.getOrElse(variant.isMinifyEnabled)) {
        UploadMappingFileTask.register(project, variant, injectMappingFileIdTask)
      }

      if (crashlyticsExtension.nativeSymbolUploadEnabled.getOrElse(false)) {
        val generateSymbolFileTask =
          GenerateSymbolFileTask.register(project, variant, crashlyticsExtension)

        UploadSymbolFileTask.register(project, variant, generateSymbolFileTask)

        InjectBuildIdsTask.register(project, variant)
      }
    }
  }

  internal companion object {
    const val CRASHLYTICS_TASK_GROUP = "Firebase Crashlytics"
    private const val CRASHLYTICS_EXTENSION_NAME = "firebaseCrashlytics"

    const val UPGRADE_MSG =
      "For more information, see https://firebase.google.com/docs/crashlytics/upgrade-to-crashlytics-gradle-plugin-v3"

    /** Get a variant-specific Crashlytics dir. Always use this to namespace by variant name. */
    fun buildDir(project: Project, variant: Variant, dirName: String = ""): Provider<Directory> =
      project.layout.buildDirectory.dir("crashlytics/${variant.name}/$dirName")

    /** Get a variant-specific Crashlytics file. Always use this to namespace by variant name. */
    fun buildFile(project: Project, variant: Variant, fileName: String): Provider<RegularFile> =
      project.layout.buildDirectory.file("crashlytics/${variant.name}/$fileName")
  }
}
