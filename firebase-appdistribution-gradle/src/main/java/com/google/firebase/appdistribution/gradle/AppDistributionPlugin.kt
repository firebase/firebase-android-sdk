/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.appdistribution.gradle

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.google.firebase.appdistribution.gradle.AppDistributionExtension.Companion.isDefault
import com.google.firebase.appdistribution.gradle.AppDistributionExtension.DeprecatedAppDistributionExtension
import com.google.firebase.appdistribution.gradle.AppDistributionExtension.ProjectDefaultAppDistributionExtension
import com.google.firebase.appdistribution.gradle.tasks.AddTestersTask
import com.google.firebase.appdistribution.gradle.tasks.RemoveTestersTask
import com.google.firebase.appdistribution.gradle.tasks.UploadDistributionTask
import com.google.firebase.appdistribution.gradle.tasks.UploadDistributionTaskConfigurator.configure
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging

/**
 * App Distribution Gradle [Plugin]. Checks if the project is an Android project and if so,
 * registers [RemoveTestersTask] and [AddTestersTask] tasks on the project and
 * [UploadDistributionTask] on app variants that contain a `firebaseAppDistribution` block
 */
class AppDistributionPlugin : Plugin<Project> {
  private var foundAndroidPlugin = false

  override fun apply(project: Project) {
    // The closure passed to withPlugin is called when the android plugin is applied
    project.plugins.withType(AppPlugin::class.java) {
      foundAndroidPlugin = true
      // If android plugin is applied, continue with setup
      val componentsExtension =
        project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
      if (componentsExtension.pluginVersion < AndroidPluginVersion(7, 0, 0)) {
        throw GradleException(
          "The App Distribution gradle plugin requires a minimum Android Gradle Plugin version of 7.0.0"
        )
      }
      val agpIsBefore8_1_0 = componentsExtension.pluginVersion < AndroidPluginVersion(8, 1, 0)
      project.tasks.register(ADD_TESTERS_TASK_NAME, AddTestersTask::class.java)
      project.tasks.register(REMOVE_TESTERS_TASK_NAME, RemoveTestersTask::class.java)

      if (agpIsBefore8_1_0) {
        setUpOnAgpBefore8_1_0(project)
      } else {
        setUp(project)
      }
    }

    // Called once the project is evaluated i.e. all plugins are applied
    project.afterEvaluate {
      // If android plugin was not applied throw exception
      check(foundAndroidPlugin) {
        "$EXTENSION_NAME must only be used with Android application projects. Please apply the 'com.android.application' plugin."
      }
    }
  }

  private fun setUp(project: Project) {
    val androidComponents =
      project.getExtensions().getByType(ApplicationAndroidComponentsExtension::class.java)
    project.extensions.add(EXTENSION_NAME, DeprecatedAppDistributionExtension::class.java)
    project.extensions.add(EXTENSION_NAME_ROOT, ProjectDefaultAppDistributionExtension::class.java)
    androidComponents.registerExtension(
      DslExtension.Builder(EXTENSION_NAME)
        .extendBuildTypeWith(AppDistributionExtension::class.java)
        .extendProductFlavorWith(AppDistributionExtension::class.java)
        .build()
    ) { config: VariantExtensionConfig<*> ->
      project.objects.newInstance(AppDistributionVariantExtension::class.java, config)
    }

    androidComponents.onVariants() { variant ->
      val extension =
        variant.getExtension(AppDistributionVariantExtension::class.java)
          ?: throw GradleException(
            "The Firebase App Distribution plugin was unable to find its extension."
          )
      val deprecatedProjectExtension =
        project.extensions.getByType(DeprecatedAppDistributionExtension::class.java)
      val defaultProjectExtension =
        project.extensions.getByType(ProjectDefaultAppDistributionExtension::class.java)
      if (!deprecatedProjectExtension.isDefault()) {
        logger.warn(
          "Detected use of deprecated firebaseAppDistribution { } block. If you used this block in your root project, switch to firebaseAppDistributionDefault { } instead. If you used this block in a productFlavors or buildTypes, import `com.google.firebase.appdistribution.gradle.firebaseAppDistribution` to fix this issue."
        )
      }

      // Use any project-level defaults, if available.
      val mergedExtension =
        AppDistributionExtensionCombiner.coalesce(
          AppDistributionExtensionCombiner.coalesce(extension, deprecatedProjectExtension),
          defaultProjectExtension
        )

      project.tasks.register(
        uploadDistributionTaskName(variant),
        UploadDistributionTask::class.java
      ) { task ->
        configure(task, variant, mergedExtension)
      }
    }
  }

  /** This setup was used on AGP versions < 8.1.0 */
  @Deprecated(
    message =
      "Configuration of the Firebase App Distribution Plugin on versions of the AGP prior to 8.1.0 uses a deprecated extension API. We recommend AGP 9+ to be compatible with future plugin releases."
  )
  private fun setUpOnAgpBefore8_1_0(project: Project) {
    project.extensions.add(EXTENSION_NAME, DeprecatedAppDistributionExtension::class.java)
    project.extensions.add(EXTENSION_NAME_ROOT, ProjectDefaultAppDistributionExtension::class.java)
    val appExtension = project.extensions.getByType(AppExtension::class.java)
    appExtension.buildTypes.all { buildType ->
      BuildVariantHelper.getExtensions(buildType)
        .add(EXTENSION_NAME, AppDistributionExtension::class.java)
    }
    appExtension.productFlavors.all { flavor ->
      BuildVariantHelper.getExtensions(flavor)
        .add(EXTENSION_NAME, AppDistributionExtension::class.java)
    }
    val componentsExtension =
      project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    componentsExtension.onVariants(componentsExtension.selector().all()) { appVariant ->
      val deprecatedExtension =
        project.properties[EXTENSION_NAME] as AppDistributionExtension?
          ?: AppDistributionExtension.createDefault()
      val defaultExtension =
        project.properties[EXTENSION_NAME_ROOT] as AppDistributionExtension?
          ?: AppDistributionExtension.createDefault()
      val buildType =
        appExtension.buildTypes.first { extensionBuildType: BuildType ->
          appVariant.buildType != null && appVariant.buildType == extensionBuildType.name
        }
      // `getProductFlavors()` returns an array of pairs where the first element in the pair
      // is the flavor dimension and the second element is the flavor name
      val appVariantFlavorNames = appVariant.productFlavors.map { it.second }.toSet()
      // Only return the flavors for this particular variant
      val flavors =
        appExtension.productFlavors.filter { extensionFlavor: ProductFlavor ->
          appVariantFlavorNames.contains(extensionFlavor.name)
        }

      if (!deprecatedExtension.isDefault()) {
        logger.warn(
          "Detected use of deprecated firebaseAppDistribution { } block. If you used this block in your root project, switch to firebaseAppDistributionDefault { } instead. If you used this block in a productFlavors or buildTypes, import `com.google.firebase.appdistribution.gradle.firebaseAppDistribution` to fix this issue."
        )
      }

      val origExtension =
        AppDistributionExtensionCombiner.coalesce(deprecatedExtension, defaultExtension)

      val mergedExtension =
        AppDistributionExtensionCombiner.getMergedProperties(origExtension, buildType, flavors)

      project.tasks.register(
        uploadDistributionTaskName(appVariant),
        UploadDistributionTask::class.java
      ) { task ->
        configure(task, appVariant, mergedExtension)
      }
    }
  }

  companion object {
    const val EXTENSION_NAME = "firebaseAppDistribution"
    const val EXTENSION_NAME_ROOT = "firebaseAppDistributionDefault"
    private const val ANDROID_PLUGIN_ID = "com.android.application"
    private const val ADD_TESTERS_TASK_NAME = "appDistributionAddTesters"
    private const val REMOVE_TESTERS_TASK_NAME = "appDistributionRemoveTesters"
    private val logger = Logging.getLogger(this::class.java)

    private fun uploadDistributionTaskName(variant: ApplicationVariant): String {
      // Force capitalization for Gradle 1.9
      val taskSuffix =
        "${variant.name.substring(0, 1).uppercase(Locale.getDefault())}${variant.name.substring(1)}"
      return "appDistributionUpload$taskSuffix"
    }
  }
}
