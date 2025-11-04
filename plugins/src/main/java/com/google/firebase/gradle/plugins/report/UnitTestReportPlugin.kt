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

package com.google.firebase.gradle.plugins.report

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class UnitTestReportPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.tasks.register<UnitTestReportTask>("generateTestReport") {
      outputFile.set(project.file("test-report.md"))
      commitCount.set(8 as Integer)
      apiToken.set(System.getenv("GH_TOKEN"))
    }
  }
}
