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

package com.google.firebase.appdistribution.gradle.tasks

import com.android.build.api.artifact.SingleArtifact.APK
import com.android.build.api.artifact.SingleArtifact.BUNDLE
import com.android.build.api.variant.ApplicationVariant
import com.google.firebase.appdistribution.gradle.AppDistributionExtension
import com.google.firebase.appdistribution.gradle.AppDistributionVariantExtension
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.Property

/**
 * Configures `UploadDistributionTask`. Only to be called during configuration time. Do not call
 * during task execution time.
 */
object UploadDistributionTaskConfigurator {
  @JvmStatic
  fun configure(
    task: UploadDistributionTask,
    variant: ApplicationVariant,
    extension: AppDistributionVariantExtension
  ) {
    task.apply {
      setMustRunAfter(listOf(bundleTaskName(variant), assembleTaskName(variant)))
      appId.set(extension.appId)
      artifactPath.setAbsolutePathFrom(extension.artifactPath, project)
      artifactType.set(extension.artifactType)
      groups.set(extension.groups)
      groupsFile.setAbsolutePathFrom(extension.groupsFile, project)
      releaseNotes.set(extension.releaseNotes)
      releaseNotesFile.setAbsolutePathFrom(extension.releaseNotesFile, project)
      testers.set(extension.testers)
      testersFile.setAbsolutePathFrom(extension.testersFile, project)
      testDevices.set(extension.testDevices)
      testerDevicesFile.setAbsolutePathFrom(extension.testDevicesFile, project)
      testUsername.set(extension.testUsername)
      testPassword.set(extension.testPassword)
      testPasswordFile.set(extension.testPasswordFile)
      testUsernameResource.set(extension.testUsernameResource)
      testPasswordResource.set(extension.testPasswordResource)
      testNonBlocking.set(extension.testNonBlocking)
      serviceCredentialsFile.setAbsolutePathFrom(extension.serviceCredentialsFile, project)
      inferredAab.value(variant.artifacts.get(BUNDLE))
      inferredApkDirectory.value(variant.artifacts.get(APK))
      testCases.value(extension.testCases)
      testCasesFile.value(nullOrAbsolutePath(extension.testCasesFile.getOrNull(), project))
      configureGoogleServicesPluginPath(this, variant)
    }
  }

  private fun Property<String>.setAbsolutePathFrom(
    extensionProperty: Property<String?>,
    project: Project
  ) {
    set(nullOrAbsolutePath(extensionProperty.getOrNull(), project))
  }

  @JvmStatic
  @Deprecated(
    "Configuration on UploadDistributionTask in AGP versions < 8.1.0 uses an old extension API and is deprecated. We recommend AGP 9+ to be compatible with future plugin releases."
  )
  fun configure(
    task: UploadDistributionTask,
    variant: ApplicationVariant,
    extension: AppDistributionExtension
  ) {
    task.apply {
      setMustRunAfter(listOf(bundleTaskName(variant), assembleTaskName(variant)))
      appId.set(extension.appId)
      artifactPath.set(nullOrAbsolutePath(extension.artifactPath, project))
      artifactType.set(extension.artifactType)
      groups.set(extension.groups)
      groupsFile.set(nullOrAbsolutePath(extension.groupsFile, project))
      releaseNotes.set(extension.releaseNotes)
      releaseNotesFile.set(nullOrAbsolutePath(extension.releaseNotesFile, project))
      testers.set(extension.testers)
      testersFile.set(nullOrAbsolutePath(extension.testersFile, project))
      testDevices.set(extension.testDevices)
      testerDevicesFile.set(nullOrAbsolutePath(extension.testDevicesFile, project))
      testUsername.set(extension.testUsername)
      testPassword.set(extension.testPassword)
      testPasswordFile.set(extension.testPasswordFile)
      testUsernameResource.set(extension.testUsernameResource)
      testPasswordResource.set(extension.testPasswordResource)
      testNonBlocking.set(extension.testNonBlocking)
      serviceCredentialsFile.set(nullOrAbsolutePath(extension.serviceCredentialsFile, project))
      inferredAab.value(variant.artifacts.get(BUNDLE))
      inferredApkDirectory.value(variant.artifacts.get(APK))
      testCases.value(extension.testCases)
      testCasesFile.value(nullOrAbsolutePath(extension.testCasesFile, project))
      configureGoogleServicesPluginPath(this, variant)
    }
  }

  private fun configureGoogleServicesPluginPath(
    task: UploadDistributionTask,
    variant: ApplicationVariant
  ) {
    // See below for definition of task name
    // https://github.com/google/play-services-plugins/blob/master/google-services-plugin/src/main/kotlin/com/google/gms/googleservices/GoogleServicesPlugin.kt#L84
    val googleServicesTaskName = "process${capitalizedVariantName(variant)}GoogleServices"
    val googleServicesOutputDirectory =
      try {
        task.project.tasks.named(googleServicesTaskName).flatMap { googleServicesTask ->
          task.mustRunAfter(googleServicesTask)
          val resourceDirProp = googleServicesTask.property("intermediateDir") as File
          task.project.objects.directoryProperty().fileValue(resourceDirProp)
        }
      } catch (e: UnknownTaskException) {
        // If there is no Google Services Task, be okay with it,
        // we'll fall back to the defined appId
        null
      }
    if (googleServicesOutputDirectory != null) {
      task.googleServicesDirectory.set(googleServicesOutputDirectory)
    }
  }

  private fun capitalizedVariantName(variant: ApplicationVariant) =
    variant.name.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }

  private fun assembleTaskName(variant: ApplicationVariant) =
    "assemble${capitalizedVariantName(variant)}"

  private fun bundleTaskName(variant: ApplicationVariant) =
    "bundle${capitalizedVariantName(variant)}"

  // Returns null or the absolute path given a relative path. Calls `getProject()`, so don't call
  // this method during task execution
  private fun nullOrAbsolutePath(stringPath: String?, project: Project): String? {
    return if (stringPath == null) {
      null
    } else
      try {
        val path = Paths.get(stringPath)
        if (path.isAbsolute) {
          path.toString()
        } else {
          val rootPath: Path = Paths.get(project.getRootDir().getPath())
          rootPath.resolve(path).toString()
        }
      } catch (e: InvalidPathException) {
        throw GradleException("$stringPath is an invalid path", e)
      }
  }
}
