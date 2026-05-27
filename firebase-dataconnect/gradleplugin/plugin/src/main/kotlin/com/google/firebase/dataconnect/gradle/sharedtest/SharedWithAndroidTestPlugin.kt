/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.gradle.sharedtest

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.Variant
import java.util.Locale
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.register

/**
 * A Gradle plugin that, when applied, makes all files in `src/test/kotlin` that are annotated with
 * `@file:SharedWithAndroidTest` available to code in `src/androidTest/kotlin`, enabling test
 * utilities written for unit tests to also be available to integration tests.
 *
 * This is achieved by adding a "code generation" step to the `androidTest` target which simply
 * copies the appropriately-annotated files into the "generated code" directory.
 *
 * To apply this plugin to an Android application or library, simply register the plugin alongside
 * other Gradle plugins in `build.gradle.kts`:
 *
 * ```
 * plugins {
 *   // other plugins
 *   id("com.google.firebase.dataconnect.sharedtest")
 * }
 * ```
 */
@Suppress("unused")
abstract class SharedWithAndroidTestPlugin : Plugin<Project> {
  override fun apply(project: Project) = applyPlugin(project.extensions, project.tasks)
}

private fun applyPlugin(extensions: ExtensionContainer, tasks: TaskContainer) {
  val androidComponents = extensions.getByType(AndroidComponentsExtension::class.java)
  androidComponents.onVariants { variant -> handleVariant(variant, tasks) }
}

private fun handleVariant(variant: Variant, tasks: TaskContainer) {
  val androidTest = (variant as? HasAndroidTest)?.androidTest ?: return
  val variantNameTitleCase = variant.name.replaceFirstChar { it.titlecase(Locale.US) }

  val task =
    tasks.register<CopySharedWithAndroidTestFiles>(
      "copy${variantNameTitleCase}SharedWithAndroidTestFiles"
    ) {
      val projectDirectory = project.layout.projectDirectory
      inputBaseDirectory.set(projectDirectory)
      inputDirectory.set(projectDirectory.dir("src/test/kotlin"))
    }

  androidTest.sources.java!!.addGeneratedSourceDirectory(
    task,
    CopySharedWithAndroidTestFiles::outputDirectory
  )
}
