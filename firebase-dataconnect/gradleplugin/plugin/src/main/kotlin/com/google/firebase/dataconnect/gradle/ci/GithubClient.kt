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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import kotlin.random.Random
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations

/** Wrapper around the "gh" GitHub client application, for interacting with GitHub. */
class GithubClient(
  val execOperations: ExecOperations,
  val tempDirectory: File,
  val githubRepository: String,
  val logger: Logger
) {

  fun fetchIssueInfo(issueNumber: Int): IssueInfo {
    logger.info("Fetching information from GitHub about issue #{}", issueNumber)

    val stdoutBytes =
      runGithubClient(
        listOf(
          "issue",
          "view",
          issueNumber.toString(),
          "--json",
          "title,body",
        )
      )

    val jsonParser = Json { ignoreUnknownKeys = true }
    @OptIn(ExperimentalSerializationApi::class)
    val issueInfo = jsonParser.decodeFromStream<IssueInfo>(ByteArrayInputStream(stdoutBytes))

    logger.info("Fetched information from GitHub about issue #{}: {}", issueNumber, issueInfo)
    return issueInfo
  }

  @Serializable data class IssueInfo(val title: String, val body: String)

  /**
   * Posts a comment onto a GitHub issue or pull request.
   *
   * @param issueNumber the issue or pull request number on which to comment.
   * @param messageLines the lines of text of the comment to post.
   * @return the URL of the newly-created comment that was posted by this method call.
   */
  fun postComment(issueNumber: Int, messageLines: Iterable<String>): URL {
    val tempFile = File(tempDirectory, Random.nextAlphanumericString(30))
    if (!tempDirectory.exists() && !tempDirectory.mkdirs()) {
      logger.warn(
        "WARNING: unable to create directory: {} [warning code kxd2j66gzm]",
        tempDirectory.absolutePath
      )
    }
    tempFile.writeText(messageLines.joinToString("\n"))

    val stdoutBytes =
      try {
        runGithubClient(
          listOf(
            "issue",
            "comment",
            issueNumber.toString(),
            "--body-file",
            tempFile.absolutePath,
          )
        )
      } finally {
        tempFile.delete()
      }

    return URL(String(stdoutBytes).trim())
  }

  private fun runGithubClient(args: Iterable<String> = emptyList()): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    execOperations.exec { execSpec ->
      execSpec.standardOutput = byteArrayOutputStream
      execSpec.executable("gh")
      args.forEach { execSpec.args(it) }
      execSpec.args("-R")
      execSpec.args(githubRepository)
      logger.info("Running command: {}", execSpec.commandLine.joinToString(" "))
    }
    return byteArrayOutputStream.toByteArray()
  }
}
