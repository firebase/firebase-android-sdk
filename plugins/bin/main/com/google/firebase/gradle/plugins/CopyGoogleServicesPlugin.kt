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

package com.google.firebase.gradle.plugins

import com.android.build.gradle.BaseExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Copies the root google-services.json into the project directory during build time.
 *
 * If a path is provided via `FIREBASE_GOOGLE_SERVICES_PATH`, that will be used instead. The file
 * will also be renamed to `google-services.json`, so provided files do *not* need to be properly
 * named.
 *
 * Will also register the `com.google.gms.google-services` plugin if a test task is running.
 */
abstract class CopyGoogleServicesPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val copyRootGoogleServices = registerCopyRootGoogleServicesTask(project)

    project.allprojects {
      // fixes dependencies with gradle tasks that do not properly dependOn `preBuild`
      tasks.configureEach {
        if (name !== "copyRootGoogleServices") dependsOn(copyRootGoogleServices)
      }
    }

    if (project.isRunningTestTask()) {
      println("[test] applying google-services plugin")
      project.plugins.apply("com.google.gms.google-services")
    }
  }

  private fun Project.isRunningTestTask(): Boolean {
    val testTasks = listOf("AndroidTest", "connectedCheck", "deviceCheck")

    return gradle.startParameter.taskNames.any { testTasks.any(it::contains) }
  }

  private fun registerCopyRootGoogleServicesTask(project: Project) =
    project.tasks.register<Copy>("copyRootGoogleServices") {
      val sourcePath =
        System.getenv("FIREBASE_GOOGLE_SERVICES_PATH") ?: "${project.rootDir}/google-services.json"

      val library = project.extensions.getByType<BaseExtension>()

      val targetPackageLine = "\"package_name\": \"${library.namespace}\""
      val packageLineRegex = Regex("\"package_name\":\\s+\".*\"")

      from(sourcePath)
      into(project.projectDir)

      rename { "google-services.json" }

      if (fileIsMissingPackageName(sourcePath, targetPackageLine)) {
        /**
         * Modifies `google-services.json` such that all declared `package_name` entries are
         * replaced with the project's namespace. This tricks the google services plugin into
         * thinking that the target `package_name` is a Firebase App and allows connection to the
         * Firebase project.
         *
         * Note that all events generated from that app will then go to whatever the first client
         * entry is in the `google-services.json` file.
         */
        filter { it.replace(packageLineRegex, targetPackageLine) }
      }
    }

  private fun fileIsMissingPackageName(path: String, targetPackageLine: String): Boolean {
    val file = File(path)
    if (!file.exists()) return true

    return !file.readText().contains(targetPackageLine)
  }
}
