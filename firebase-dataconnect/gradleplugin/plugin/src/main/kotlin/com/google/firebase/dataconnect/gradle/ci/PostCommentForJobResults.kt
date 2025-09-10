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

import com.google.firebase.dataconnect.gradle.plugin.nextAlphanumericString
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.logging.Logger
import org.gradle.internal.impldep.org.apache.commons.io.output.ByteArrayOutputStream
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
  val execOperations: ExecOperations,
  val logger: Logger
) {

  fun run() {
    logger.info("jobResults={}", jobResults)
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
    postCommentToGithubIssue(messageLines)
  }

  private fun postCommentToGithubIssue(messageLines: List<String>) {
    val tempFile = File(workDirectory, Random.nextAlphanumericString(30))
    logger.info("Writing GitHub Issue comment into text file: {}", tempFile.absolutePath)
    workDirectory.mkdirs()
    tempFile.writeText(messageLines.joinToString("\n"))

    execOperations.exec { execSpec ->
      execSpec.executable("gh")
      execSpec.args("issue")
      execSpec.args("comment")
      execSpec.args(githubIssue.toString())
      execSpec.args("--body-file")
      execSpec.args(tempFile.absolutePath)
      execSpec.args("-R")
      execSpec.args(githubRepository)
      logger.info("Running command: {}", execSpec.commandLine.joinToString(" "))
    }
  }

  private fun calculateMessageLines(): List<String> = buildList {
    val prNumber: Int? = parseGithubPrNumberFromGithubRef()
    val prInfo: GitHubPrInfo? = if (prNumber === null) null else fetchGithubPrInfo(prNumber)
    if (prInfo !== null) {
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
    logger.info("Extracted PR number from githubRef: {}", githubRef)
    return prNumber
  }

  private fun fetchGithubPrInfo(prNumber: Int): GitHubPrInfo {
    logger.info("Fetching information from GitHub about PR #{}", prNumber)
    val byteArrayOutputStream = ByteArrayOutputStream()
    execOperations.exec { execSpec ->
      execSpec.standardOutput = byteArrayOutputStream
      execSpec.executable("gh")
      execSpec.args("issue")
      execSpec.args("view")
      execSpec.args(prNumber.toString())
      execSpec.args("--json")
      execSpec.args("title,body")
      execSpec.args("-R")
      execSpec.args(githubRepository)
      logger.info("Running command: {}", execSpec.commandLine.joinToString(" "))
    }

    val jsonParser = Json { ignoreUnknownKeys = true }
    @OptIn(ExperimentalSerializationApi::class)
    val githubPrInfo =
      jsonParser.decodeFromStream<GitHubPrInfo>(
        ByteArrayInputStream(byteArrayOutputStream.toByteArray())
      )

    logger.info("Fetched information from GitHub about PR #{}: {}", prNumber, githubPrInfo)
    return githubPrInfo
  }

  data class JobResult(val jobId: String, val result: String)

  @Serializable private data class GitHubPrInfo(val title: String, val body: String)
}
