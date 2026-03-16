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

import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.provider.Property

/** A variant extension, containing properties that can be configured at a variant level. */
public abstract class AppDistributionVariantExtension
@Inject
constructor(config: VariantExtensionConfig<*>) : VariantExtension, Serializable {

  abstract val artifactPath: Property<String?>
  abstract val artifactType: Property<String?>
  abstract val serviceCredentialsFile: Property<String?>
  abstract val appId: Property<String?>
  abstract val releaseNotes: Property<String?>
  abstract val releaseNotesFile: Property<String?>
  abstract val testers: Property<String?>
  abstract val testersFile: Property<String?>
  abstract val groups: Property<String?>
  abstract val groupsFile: Property<String?>
  abstract val testDevices: Property<String?>
  abstract val testDevicesFile: Property<String?>
  abstract val testUsername: Property<String?>
  abstract val testPassword: Property<String?>
  abstract val testPasswordFile: Property<String?>
  abstract val testUsernameResource: Property<String?>
  abstract val testPasswordResource: Property<String?>
  abstract val testNonBlocking: Property<Boolean?>
  abstract val testCases: Property<String?>
  abstract val testCasesFile: Property<String?>

  init {
    val buildTypeExtension = config.buildTypeExtension(AppDistributionExtension::class.java)
    val productFlavorsExtensions =
      config.productFlavorsExtensions(AppDistributionExtension::class.java)
    AppDistributionExtensionCombiner.coalesce(buildTypeExtension, this)
    productFlavorsExtensions.forEach({ ext ->
      AppDistributionExtensionCombiner.coalesce(ext, this)
    })
  }
}
