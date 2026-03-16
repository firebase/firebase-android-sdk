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
package com.google.firebase.appdistribution.gradle

import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.google.firebase.appdistribution.gradle.AppDistributionExtension.Companion.createDefault
import com.google.firebase.appdistribution.gradle.BuildVariantHelper.getExtensions
import org.gradle.api.provider.Property

/** Utility functions for combining multiple extension configurations into one. */
internal object AppDistributionExtensionCombiner {

  fun coalesce(
    primary: AppDistributionExtension,
    default: AppDistributionExtension
  ): AppDistributionExtension {
    val returnExtension = createDefault()
    returnExtension.artifactPath = primary.artifactPath ?: default.artifactPath
    returnExtension.artifactType = primary.artifactType ?: default.artifactType
    returnExtension.appId = primary.appId ?: default.appId
    returnExtension.serviceCredentialsFile =
      primary.serviceCredentialsFile ?: default.serviceCredentialsFile
    returnExtension.releaseNotes = primary.releaseNotes ?: default.releaseNotes
    returnExtension.releaseNotesFile = primary.releaseNotesFile ?: default.releaseNotesFile
    returnExtension.testers = primary.testers ?: default.testers
    returnExtension.testersFile = primary.testersFile ?: default.testersFile
    returnExtension.groups = primary.groups ?: default.groups
    returnExtension.groupsFile = primary.groupsFile ?: default.groupsFile
    returnExtension.testDevices = primary.testDevices ?: default.testDevices
    returnExtension.testDevicesFile = primary.testDevicesFile ?: default.testDevicesFile
    returnExtension.testUsername = primary.testUsername ?: default.testUsername
    returnExtension.testPassword = primary.testPassword ?: default.testPassword
    returnExtension.testPasswordFile = primary.testPasswordFile ?: default.testPasswordFile
    returnExtension.testUsernameResource =
      primary.testUsernameResource ?: default.testUsernameResource
    returnExtension.testPasswordResource =
      primary.testPasswordResource ?: default.testPasswordResource
    returnExtension.testNonBlocking = primary.testNonBlocking ?: default.testNonBlocking
    returnExtension.testCases = primary.testCases ?: default.testCases
    returnExtension.testCasesFile = primary.testCasesFile ?: default.testCasesFile
    return returnExtension
  }

  fun getMergedProperties(
    projectExtension: AppDistributionExtension,
    buildType: BuildType,
    productFlavors: List<ProductFlavor>
  ): AppDistributionExtension {
    var extension = projectExtension

    val buildTypeExtension =
      BuildVariantHelper.getExtensions(buildType).findByName(AppDistributionPlugin.EXTENSION_NAME)
        as AppDistributionExtension?

    extension =
      if (buildTypeExtension != null) {
        coalesce(buildTypeExtension, extension)
      } else {
        extension
      }
    for (flavor in productFlavors) {
      val flavorExtension =
        getExtensions(flavor).findByName(AppDistributionPlugin.EXTENSION_NAME)
          as AppDistributionExtension?
      extension =
        if (flavorExtension != null) {
          coalesce(flavorExtension, extension)
        } else {
          extension
        }
    }

    return extension
  }

  fun coalesce(
    preferred: AppDistributionExtension,
    default: AppDistributionVariantExtension
  ): AppDistributionVariantExtension {
    coalesce(preferred.artifactPath, default.artifactPath)
    coalesce(preferred.artifactType, default.artifactType)
    coalesce(preferred.appId, default.appId)
    coalesce(preferred.serviceCredentialsFile, default.serviceCredentialsFile)
    coalesce(preferred.releaseNotes, default.releaseNotes)
    coalesce(preferred.releaseNotesFile, default.releaseNotesFile)
    coalesce(preferred.testers, default.testers)
    coalesce(preferred.testersFile, default.testersFile)
    coalesce(preferred.groups, default.groups)
    coalesce(preferred.groupsFile, default.groupsFile)
    coalesce(preferred.testDevices, default.testDevices)
    coalesce(preferred.testDevicesFile, default.testDevicesFile)
    coalesce(preferred.testUsername, default.testUsername)
    coalesce(preferred.testPassword, default.testPassword)
    coalesce(preferred.testPasswordFile, default.testPasswordFile)
    coalesce(preferred.testUsernameResource, default.testUsernameResource)
    coalesce(preferred.testPasswordResource, default.testPasswordResource)
    coalesce(preferred.testNonBlocking, default.testNonBlocking)
    coalesce(preferred.testCases, default.testCases)
    coalesce(preferred.testCasesFile, default.testCasesFile)
    // default has been mutated.
    return default
  }

  /**
   * Combine a generic [com.google.firebase.appdistribution.gradle.AppDistributionExtension] with
   * this variant extension. Prioritize values from the first input.
   *
   * Mutates whichever input is the AppDistributionVariantExtension and returns it.
   */
  fun coalesce(
    preferred: AppDistributionVariantExtension,
    default: AppDistributionExtension
  ): AppDistributionVariantExtension {
    coalesce(preferred.artifactPath, default.artifactPath)
    coalesce(preferred.artifactType, default.artifactType)
    coalesce(preferred.appId, default.appId)
    coalesce(preferred.serviceCredentialsFile, default.serviceCredentialsFile)
    coalesce(preferred.releaseNotes, default.releaseNotes)
    coalesce(preferred.releaseNotesFile, default.releaseNotesFile)
    coalesce(preferred.testers, default.testers)
    coalesce(preferred.testersFile, default.testersFile)
    coalesce(preferred.groups, default.groups)
    coalesce(preferred.groupsFile, default.groupsFile)
    coalesce(preferred.testDevices, default.testDevices)
    coalesce(preferred.testDevicesFile, default.testDevicesFile)
    coalesce(preferred.testUsername, default.testUsername)
    coalesce(preferred.testPassword, default.testPassword)
    coalesce(preferred.testPasswordFile, default.testPasswordFile)
    coalesce(preferred.testUsernameResource, default.testUsernameResource)
    coalesce(preferred.testPasswordResource, default.testPasswordResource)
    coalesce(preferred.testNonBlocking, default.testNonBlocking)
    coalesce(preferred.testCases, default.testCases)
    coalesce(preferred.testCasesFile, default.testCasesFile)
    // Preferred has been mutated
    return preferred
  }

  private fun coalesce(preferred: String?, default: Property<String?>): Unit {
    default.set(preferred ?: default.getOrNull())
  }
  private fun coalesce(preferred: Boolean?, default: Property<Boolean?>): Unit {
    default.set(preferred ?: default.getOrNull())
  }
  private fun coalesce(preferred: Property<String?>, default: String?): Unit {
    preferred.set(preferred.getOrNull() ?: default)
  }
  private fun coalesce(preferred: Property<Boolean?>, default: Boolean?): Unit {
    preferred.set(preferred.getOrNull() ?: default)
  }
}
