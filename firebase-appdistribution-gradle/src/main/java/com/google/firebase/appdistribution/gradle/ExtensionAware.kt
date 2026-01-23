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

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.google.firebase.appdistribution.gradle.AppDistributionExtension.DeprecatedAppDistributionExtension
import com.google.firebase.appdistribution.gradle.AppDistributionExtension.ProjectDefaultAppDistributionExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

/**
 * Extension methods to ensure all firebaseAppDistribution blocks are resolving to the enclosed,
 * ExtensionAware scope, instead of just the Project, when using Kotlin build scripts.
 */
@Deprecated(
  message = "The firebaseAppDistribution is deprecated for use at the root of a project.",
  replaceWith = ReplaceWith("firebaseAppDistributionDefault")
)
fun Project.firebaseAppDistribution(action: AppDistributionExtension.() -> Unit) =
  ((this as ExtensionAware).extensions)
    .getByType(DeprecatedAppDistributionExtension::class.java)
    .action()

/**
 * Extension methods to ensure all firebaseAppDistribution blocks are resolving to the enclosed,
 * ExtensionAware scope, instead of just the Project, when using Kotlin build scripts.
 */
fun Project.firebaseAppDistributionDefault(
  action: ProjectDefaultAppDistributionExtension.() -> Unit
) =
  ((this as ExtensionAware).extensions)
    .getByType(ProjectDefaultAppDistributionExtension::class.java)
    .action()

fun BuildType.firebaseAppDistribution(action: AppDistributionExtension.() -> Unit) =
  ((this as ExtensionAware).extensions).getByType(AppDistributionExtension::class.java).action()

fun ProductFlavor.firebaseAppDistribution(action: AppDistributionExtension.() -> Unit) =
  ((this as ExtensionAware).extensions).getByType(AppDistributionExtension::class.java).action()
