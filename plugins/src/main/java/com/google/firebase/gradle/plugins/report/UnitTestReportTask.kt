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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Creates a markdown unit test report file based on recent runs of GitHub Actions. Task simply
 * aggregates live test data and does not rely on the current state of the repository.
 *
 * @property outputFile The file path to output the markdown test report to.
 * @property commitCount The number of remote commits to aggregate test results from.
 * @property apiToken The GitHub API token with adequate permissions to read test result data and
 *   execute GraphQL queries.
 */
abstract class UnitTestReportTask : DefaultTask() {
  @get:OutputFile abstract val outputFile: RegularFileProperty

  @get:Input abstract val commitCount: Property<Integer>

  @get:Input abstract val apiToken: Property<String>

  @TaskAction
  fun make() {
    UnitTestReport(apiToken.get()).createReport(outputFile.asFile.get(), commitCount.get().toInt())
  }
}
