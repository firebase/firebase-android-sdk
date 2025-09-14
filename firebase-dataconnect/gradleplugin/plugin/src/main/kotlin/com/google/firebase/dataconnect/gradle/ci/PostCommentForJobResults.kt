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

import java.io.File
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations

class PostCommentForJobResults(
  val jobResults: List<JobResult>,
  val githubIssue: Int,
  val githubRepository: String,
  val githubEventName: String,
  val githubRef: String,
  val githubWorkflow: String,
  val githubSha: String,
  val githubRepositoryHtmlUrl: String,
  val githubRunId: String,
  val githubRunNumber: String,
  val githubRunAttempt: String,
  val workDirectory: File,
  execOperations: ExecOperations,
  val logger: Logger
) {

  private val githubClient = GithubClient(execOperations, workDirectory, githubRepository, logger)

  fun run() {
    logger.info("jobResults=[{}]{", jobResults.size)
    jobResults.forEach { logger.info("  {}: {}", it.jobId, it.result) }
    logger.info("}")
    logger.info("githubIssue={}", githubIssue)
    logger.info("githubRepository={}", githubRepository)
    logger.info("githubEventName={}", githubEventName)
    logger.info("githubRef={}", githubRef)
    logger.info("githubWorkflow={}", githubWorkflow)
    logger.info("githubSha={}", githubSha)
    logger.info("githubRepositoryHtmlUrl={}", githubRepositoryHtmlUrl)
    logger.info("githubRunId={}", githubRunId)
    logger.info("githubRunNumber={}", githubRunNumber)
    logger.info("githubRunAttempt={}", githubRunAttempt)
    logger.info("workDirectory={}", workDirectory.absolutePath)

    val messageLines = calculateMessageLines()

    val issueUrl = "$githubRepositoryHtmlUrl/issues/$githubIssue"
    logger.info("Posting the following comment to GitHub Issue {}:", issueUrl)
    messageLines.forEach { logger.info("> {}", it) }
    val commentUrl = githubClient.postComment(githubIssue, messageLines)
    logger.lifecycle("Comment posted successfully: {}", commentUrl)
  }

  private fun calculateMessageLines(): List<String> = buildList {
    parseGithubPrNumberFromGithubRef()?.let { prNumber ->
      val prInfo = githubClient.fetchIssueInfo(prNumber)
      add("Posting from Pull Request $githubRepositoryHtmlUrl/pull/$prNumber (${prInfo.title})")
    }

    add("Result of workflow '$githubWorkflow' at $githubSha:")
    for (jobResult in jobResults) {
      jobResult.run {
        val resultSymbol = if (result == "success") "✅" else "❌"
        add("  - $jobId: $resultSymbol $result")
      }
    }

    add("")
    add("$githubRepositoryHtmlUrl/actions/runs/$githubRunId")

    add("")
    add(
      listOf(
          "event_name=`$githubEventName`",
          "run_id=`$githubRunId`",
          "run_number=`$githubRunNumber`",
          "run_attempt=`$githubRunAttempt`"
        )
        .joinToString(" ")
    )
  }

  private fun parseGithubPrNumberFromGithubRef(): Int? {
    logger.info("Extracting PR number from githubRef: {}", githubRef)
    val prNumber: Int? =
      Regex("refs/pull/([0-9]+)/merge").matchEntire(githubRef)?.groupValues?.get(1)?.toInt()
    logger.info("Extracted PR number from githubRef {}: {}", githubRef, prNumber)
    return prNumber
  }

  data class JobResult(val jobId: String, val result: String)
}
