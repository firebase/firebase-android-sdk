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

import java.io.FileWriter
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.stream.Stream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.internal.Pair
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.use

@SuppressWarnings("NewApi")
class TestReportGenerator(private val apiToken: String) {
  private val LOG: Logger = LoggerFactory.getLogger("firebase-test-report")
  private val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  fun createReport(commitCount: Int) {
    val response =
      request(
        URI.create("https://api.github.com/graphql"),
        JsonObject::class.java,
        generateGraphQLQuery(commitCount),
      )
    val commits =
      (response["data"]
        ?.jsonObject["repository"]
        ?.jsonObject["ref"]
        ?.jsonObject["target"]
        ?.jsonObject["history"]
        ?.jsonObject["nodes"]
        ?.jsonArray ?: throw RuntimeException("Missing fields in response: $response"))
        .stream()
        .limit(commitCount.toLong())
        .map { el: JsonElement ->
          val obj = el as JsonObject
          ReportCommit(
            obj["oid"]?.jsonPrimitive?.content ?: throw RuntimeException("Couldn't find commit SHA"),
            obj["associatedPullRequests"]
              ?.jsonObject["nodes"]
              ?.jsonArray[0]
              ?.jsonObject["number"]
              ?.jsonPrimitive
              ?.int ?: throw RuntimeException("Couldn't find PR number for commit $obj"),
          )
        }
        .toList()
    outputReport(commits)
  }

  private fun outputReport(commits: List<ReportCommit>) {
    val reports: MutableList<TestReport> = mutableListOf()
    for (commit in commits) {
      reports.addAll(parseTestReports(commit.sha))
    }
    val output = StringBuilder()
    output.append("### Unit Tests\n\n")
    output.append(
      generateTable(
        commits,
        reports.filter { r: TestReport -> r.type == TestReport.Type.UNIT_TEST },
      )
    )
    output.append("\n")
    output.append("### Instrumentation Tests\n\n")
    output.append(
      generateTable(
        commits,
        reports.filter { r: TestReport -> r.type == TestReport.Type.INSTRUMENTATION_TEST },
      )
    )
    output.append("\n")

    try {
      File("test-report.md").writeText(output.toString())
    } catch (e: Exception) {
      throw RuntimeException("Error writing report file", e)
    }
  }

  private fun generateTable(reportCommits: List<ReportCommit>, reports: List<TestReport>): String {
    val commitLookup = reportCommits.associateBy(ReportCommit::sha)
    val commits = reports.map(TestReport::commit).distinct()
    var sdks = reports.map(TestReport::name).distinct().sorted()
    val lookup = reports.associateBy({ report -> Pair.of(report.name, report.commit) })
    val successPercentage: MutableMap<String, Int> = hashMapOf()
    var passingSdks = 0
    // Get success percentage
    for (sdk in sdks) {
      var sdkTestCount = 0
      var sdkTestSuccess = 0
      for (commit in commits) {
        if (lookup.containsKey(Pair.of(sdk, commit))) {
          val report: TestReport = lookup[Pair.of(sdk, commit)]!!
          if (report.status != TestReport.Status.OTHER) {
            sdkTestCount++
            if (report.status == TestReport.Status.SUCCESS) {
              sdkTestSuccess++
            }
          }
        }
      }
      if (sdkTestSuccess == sdkTestCount) {
        passingSdks++
      }
      successPercentage.put(sdk, sdkTestSuccess * 100 / sdkTestCount)
    }
    sdks =
      sdks
        .filter { s: String? -> successPercentage[s] != 100 }
        .sortedBy { o: String -> successPercentage[o]?: 0 }
    if (sdks.isEmpty()) {
      return "*All tests passing*\n"
    }
    val output = StringBuilder("| |")
    for (commit in commits) {
      val rc = commitLookup[commit]
      output.append(" ")
      if (rc != null && rc.pr != -1) {
        output.append("[#${rc.pr}](https://github.com/firebase/firebase-android-sdk/pull/${rc.pr})")
      } else {
        output.append(commit)
      }
      output.append(" |")
    }
    output.append(" Success Rate |\n|")
    output.append(" :--- |")
    output.append(" :---: |".repeat(commits.size))
    output.append(" :--- |")
    for (sdk in sdks) {
      output.append("\n| $sdk |")
      for (commit in commits) {
        if (lookup.containsKey(Pair.of(sdk, commit))) {
          val report: TestReport = lookup[Pair.of(sdk, commit)]!!
          val icon =
            when (report.status) {
              TestReport.Status.SUCCESS -> "✅"
              TestReport.Status.FAILURE -> "⛔"
              TestReport.Status.OTHER -> "➖"
            }
          val link: String = " [%s](%s)".format(icon, report.url)
          output.append(link)
        }
        output.append(" |")
      }
      output.append(" ")
      val successChance: Int = successPercentage[sdk] ?: throw RuntimeException("Success percentage missing for $sdk")
      if (successChance == 100) {
        output.append("✅ 100%")
      } else {
        output.append("⛔ $successChance%")
      }
      output.append(" |")
    }
    output.append("\n")
    if (passingSdks > 0) {
      output.append("\n*+$passingSdks passing SDKs")
    }
    return output.toString()
  }

  private fun parseTestReports(commit: String): List<TestReport> {
    val runs = request("actions/runs?head_sha=$commit")
    for (el in runs["workflow_runs"] as JsonArray) {
      val run = el as JsonObject
      val name = run["name"]?.jsonPrimitive?.content ?: throw RuntimeException("Couldn't find CI name")
      if (name == "CI Tests") {
        return parseCITests(run["id"]?.jsonPrimitive?.content ?: throw RuntimeException("Couldn't find run id for $commit run $name"), commit)
      }
    }
    return emptyList()
  }

  private fun parseCITests(id: String, commit: String): List<TestReport> {
    val reports: MutableList<TestReport> = mutableListOf()
    val jobs = request("actions/runs/$id/jobs")
    for (el in jobs["jobs"] as JsonArray) {
      val job = el as JsonObject
      val jobName = job["name"]?.jsonPrimitive?.content ?: throw RuntimeException("Couldn't find name for job $id")
      if (jobName.startsWith("Unit Tests (:")) {
        reports.add(parseJob(TestReport.Type.UNIT_TEST, job, commit))
      } else if (jobName.startsWith("Instrumentation Tests (:")) {
        reports.add(parseJob(TestReport.Type.INSTRUMENTATION_TEST, job, commit))
      }
    }
    return reports
  }

  private fun parseJob(type: TestReport.Type, job: JsonObject, commit: String): TestReport {
    var name =
      (job["name"]
        ?.jsonPrimitive ?: throw RuntimeException("Job missing name"))
        .content
        .split("(:")
        .dropLastWhile { it.isEmpty() }
        .toTypedArray()[1]
    name = name.substring(0, name.length - 1) // Remove trailing ")"
    val status =
    if (job["status"]?.jsonPrimitive?.content == "completed") {
      if (job["conclusion"]?.jsonPrimitive?.content == "success") {
        TestReport.Status.SUCCESS
      } else {
        TestReport.Status.FAILURE
      }
    } else {
      TestReport.Status.OTHER
    }
    val url = job["html_url"]?.jsonPrimitive?.content ?: throw RuntimeException("PR missing URL")
    return TestReport(name, type, status, commit, url)
  }

  private fun generateGraphQLQuery(commitCount: Int): JsonObject {
    return JsonObject(
      mapOf(
          "query" to
          JsonPrimitive(
            """
  query {
    repository(owner: "firebase", name: "firebase-android-sdk") {
      ref(qualifiedName: "refs/heads/main") {
        target {
          ... on Commit {
            history(first: ${commitCount}) {
              nodes {
                messageHeadline
                oid
                associatedPullRequests(first: 1) {
                  nodes {
                    number
                    title
                  }
                }
              }
            }
          }
        }
      }
    }
  }
    """
          ),
        )
    )
  }

  private fun request(path: String): JsonObject {
    return request(path, JsonObject::class.java)
  }

  private fun <T> request(path: String, clazz: Class<T>): T {
    return request(URI.create(URL_PREFIX + path), clazz)
  }

  /**
   * Abstracts away paginated calling. Naively joins pages together by merging root level arrays.
   */
  private fun <T> request(uri: URI, clazz: Class<T>, payload: JsonObject? = null): T {
    val request =
      HttpRequest.newBuilder().apply {
        if (payload == null) {
          GET()
        } else {
          POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
        }
      }
        .uri(uri)
        .header("Authorization", "Bearer $apiToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()
    try {
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      val body = response.body()
      if (response.statusCode() >= 300) {
        LOG.error(response.toString())
        LOG.error(body)
      }
      val json =
        when (clazz) {
          JsonObject::class.java -> Json.decodeFromString<JsonObject>(body)
          JsonArray::class.java -> Json.decodeFromString<JsonArray>(body)
          else -> throw IllegalArgumentException("Unsupported deserialization type of $clazz")
        }
      if (json is JsonObject) {
        // Retrieve and merge objects from other pages, if present
        return response
          .headers()
          .firstValue("Link")
          .map { link: String ->
            val parts = link.split(",").dropLastWhile { it.isEmpty() }
            for (part in parts) {
              if (part.endsWith("rel=\"next\"")) {
                // <foo>; rel="next" -> foo
                val url =
                  part
                    .split(">;")
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()[0]
                    .split("<")
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()[1]
                val p = request<JsonObject>(URI.create(url), JsonObject::class.java)
                return@map JsonObject(
                  json.keys.associateWith { key: String ->
                    if (json[key] is JsonArray && p.containsKey(key) && p[key] is JsonArray) {
                      return@associateWith JsonArray(
                        Stream.concat(
                            (json[key] as JsonArray).stream(),
                            (p[key] as JsonArray).stream(),
                          )
                          .toList()
                      )
                    }
                    return@associateWith json[key]!!
                  }
                )
              }
            }
            return@map json
          }
          .orElse(json) as T
      }
      return json as T
    } catch (e: IOException) {
      throw RuntimeException(e)
    } catch (e: InterruptedException) {
      throw RuntimeException(e)
    }
  }

  companion object {
    private const val URL_PREFIX = "https://api.github.com/repos/firebase/firebase-android-sdk/"
  }
}
