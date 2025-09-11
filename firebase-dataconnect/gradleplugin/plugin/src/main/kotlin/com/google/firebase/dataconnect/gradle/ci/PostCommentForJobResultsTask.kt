/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.gradle.ci

import com.google.firebase.dataconnect.gradle.ci.PostCommentForJobResults.JobResult
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations

@Suppress("unused")
abstract class PostCommentForJobResultsTask : DefaultTask() {

  @get:Input
  @get:Option(
    option = "job-result",
    description =
      "The results of the jobs in question, of the form 'job-id:\${{ needs.job-id.result }}' " +
        "where 'job-id' is the id of the corresponding job in the 'needs' section of the job."
  )
  abstract val jobResults: ListProperty<String>

  @get:Input
  @get:Option(
    option = "github-issue",
    description = "The GitHub Issue number to which to post a comment"
  )
  abstract val githubIssue: Property<Int>

  @get:Input
  @get:Option(
    option = "github-repository",
    description = "The value of \${{ github.repository }} in the workflow"
  )
  abstract val githubRepository: Property<String>

  @get:Input
  @get:Option(
    option = "github-event-name",
    description = "The value of \${{ github.event_name }} in the workflow"
  )
  abstract val githubEventName: Property<String>

  @get:Input
  @get:Option(
    option = "github-ref",
    description = "The value of \${{ github.ref }} in the workflow"
  )
  abstract val githubRef: Property<String>

  @get:Input
  @get:Option(
    option = "github-workflow",
    description = "The value of \${{ github.workflow }} in the workflow"
  )
  abstract val githubWorkflow: Property<String>

  @get:Input
  @get:Option(
    option = "github-sha",
    description = "The value of \${{ github.sha }} in the workflow"
  )
  abstract val githubSha: Property<String>

  @get:Input
  @get:Option(
    option = "github-repository-html-url",
    description = "The value of \${{ github.event.repository.html_url }} in the workflow"
  )
  abstract val githubRepositoryHtmlUrl: Property<String>

  @get:Input
  @get:Option(
    option = "github-run-id",
    description = "The value of \${{ github.run_id }} in the workflow"
  )
  abstract val githubRunId: Property<String>

  @get:Input
  @get:Option(
    option = "github-run-number",
    description = "The value of \${{ github.run_number }} in the workflow"
  )
  abstract val githubRunNumber: Property<String>

  @get:Input
  @get:Option(
    option = "github-run-attempt",
    description = "The value of \${{ github.run_attempt }} in the workflow"
  )
  abstract val githubRunAttempt: Property<String>

  @get:Internal abstract val workDirectory: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  init {
    // Make sure the task ALWAYS runs and is never skipped because Gradle deems it "up to date".
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun run() {
    PostCommentForJobResults(
        jobResults = jobResults.get().map { it.toJobResult() },
        githubIssue = githubIssue.get(),
        githubRepository = githubRepository.get(),
        githubEventName = githubEventName.get(),
        githubRef = githubRef.get(),
        githubWorkflow = githubWorkflow.get(),
        githubSha = githubSha.get(),
        githubRepositoryHtmlUrl = githubRepositoryHtmlUrl.get(),
        githubRunId = githubRunId.get(),
        githubRunNumber = githubRunNumber.get(),
        githubRunAttempt = githubRunAttempt.get(),
        workDirectory = workDirectory.get().asFile,
        execOperations = execOperations,
        logger = logger,
      )
      .run()
  }

  class JobResultParseException(message: String) : Exception(message)

  companion object {

    fun String.toJobResult(): JobResult {
      val colonIndex = indexOf(':')
      if (colonIndex < 0) {
        throw JobResultParseException(
          "Invalid job result: $this (should have the form: jobId:jobResult)"
        )
      }
      return JobResult(jobId = substring(0, colonIndex), result = substring(colonIndex + 1))
    }
  }
}
