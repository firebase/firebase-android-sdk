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

package com.google.firebase.gradle.plugins

import net.ltgt.gradle.errorprone.ErrorPronePlugin as LtgtErrorPronePlugin
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Root orchestration plugin that enables ErrorProne diagnostics on all subprojects when the
 * `withErrorProne` marker task is requested on the Gradle command line.
 */
class ErrorPronePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val isErrorProneRequested = project.gradle.startParameter.taskNames.contains("withErrorProne")

    if (isErrorProneRequested) {
      project.subprojects {
        apply<LtgtErrorPronePlugin>()
        dependencies {
          add("errorprone", "com.google.errorprone:error_prone_core:$ERRORPRONE_VERSION")
          add("errorproneJavac", "com.google.errorprone:javac:$ERRORPRONE_JAVAC_VERSION")
        }
        tasks.withType<JavaCompile>().configureEach {
          options.errorprone.excludedPaths.set(".*/build/generated/.*")
          options.errorprone.disable("JdkObsolete", "ProtoTimestampGetSecondsGetNano")
        }
      }
    }

    // Marker task to enable errorprone diagnostics.
    project.tasks.register("withErrorProne")
  }

  companion object {
    private const val ERRORPRONE_VERSION = "2.26.0"
    private const val ERRORPRONE_JAVAC_VERSION = "9+181-r4173-1"
  }
}
