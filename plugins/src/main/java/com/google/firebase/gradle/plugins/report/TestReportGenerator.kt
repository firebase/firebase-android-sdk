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

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import java.util.Calendar
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

@SuppressWarnings("NewApi")
class TestReportGenerator(private val apiToken: String) {
  private val LOG: Logger = LoggerFactory.getLogger("firebase-test-report")

  fun createReport(outputFile: File, commitCount: Int) {
    val response: JsonObject =
      request(
        URI.create("https://api.github.com/graphql"),
        JsonObject::class.java,
        generateGraphQLQuery(commitCount),
      )
    val commits =
      (response["data"]
          ?.jsonObject
          ?.get("repository")
          ?.jsonObject
          ?.get("ref")
          ?.jsonObject
          ?.get("target")
          ?.jsonObject
          ?.get("history")
          ?.jsonObject
          ?.get("nodes")
          ?.jsonArray ?: throw RuntimeException("Missing fields in response: $response"))
        .map { el: JsonElement ->
          val obj = el as JsonObject
          ReportCommit(
            obj["oid"]?.jsonPrimitive?.content
              ?: throw RuntimeException("Couldn't find commit SHA"),
            obj["associatedPullRequests"]
              ?.jsonObject
              ?.get("nodes")
              ?.jsonArray
              ?.get(0)
              ?.jsonObject
              ?.get("number")
              ?.jsonPrimitive
              ?.int ?: throw RuntimeException("Couldn't find PR number for commit $obj"),
          )
        }
    val dailyTests = arrayListOf<TestReport>()
    // GraphQL does not expose actions in an easy to index way, and the REST API does
    val dailyTestResponse = request("actions/workflows/ai-daily-tests.yml/runs?per_page=7")
    val arr =
      dailyTestResponse["workflow_runs"]?.jsonArray
        ?: throw RuntimeException("Workflow request failed")
    for (test in arr) {
      val obj = test.jsonObject
      dailyTests.add(
        TestReport(
          "ai-daily-test",
          TestReport.Type.DAILY_TEST,
          TestReport.Status.of(obj),
          obj["head_commit"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            ?: throw RuntimeException("Missing head_commit"),
          obj["html_url"]?.jsonPrimitive?.content ?: throw RuntimeException("Missing url"),
        )
      )
    }
    outputReport(outputFile, commits, dailyTests)
  }

  private fun outputReport(
    outputFile: File,
    commits: List<ReportCommit>,
    dailyTests: List<TestReport>,
  ) {
    val reports = commits.flatMap { commit -> parseTestReports(commit.sha) }
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
    output.append("### Daily Tests\n\n")
    output.append(generateDailyTable(dailyTests))
    output.append("\n")

    try {
      outputFile.writeText(output.toString())
    } catch (e: Exception) {
      throw RuntimeException("Error writing report file", e)
    }
  }

  private fun calculateSuccess(
    sdks: List<String>,
    commits: List<String>,
    testLookup: Map<Pair<String, String>, TestReport>,
  ): Map<String, Int> {
    val successPercentage: MutableMap<String, Int> = hashMapOf()
    for (sdk in sdks) {
      var sdkTestCount = 0
      var sdkTestSuccess = 0
      for (commit in commits) {
        if (testLookup.containsKey(Pair.of(sdk, commit))) {
          val report: TestReport = testLookup[Pair.of(sdk, commit)]!!
          if (report.status != TestReport.Status.OTHER) {
            sdkTestCount++
            if (report.status == TestReport.Status.SUCCESS) {
              sdkTestSuccess++
            }
          }
        }
      }
      successPercentage.put(sdk, sdkTestSuccess * 100 / sdkTestCount)
    }
    return successPercentage
  }

  private fun generateTable(reportCommits: List<ReportCommit>, reports: List<TestReport>): String {
    val commitLookup = reportCommits.associateBy(ReportCommit::sha)
    val commits = reports.map(TestReport::commit).distinct()
    var sdks = reports.map(TestReport::name).distinct().sorted()
    val testLookup = reports.associateBy({ report -> Pair.of(report.name, report.commit) })
    val successPercentage = calculateSuccess(sdks, commits, testLookup)
    sdks =
      sdks
        .filter { s: String? -> successPercentage[s] != 100 }
        .sortedBy { o: String -> successPercentage[o] ?: 0 }
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
        if (testLookup.containsKey(Pair.of(sdk, commit))) {
          val report: TestReport = testLookup[Pair.of(sdk, commit)]!!
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
      val successChance: Int =
        successPercentage[sdk] ?: throw RuntimeException("Success percentage missing for $sdk")
      if (successChance == 100) {
        output.append("✅ 100%")
      } else {
        output.append("⛔ $successChance%")
      }
      output.append(" |")
    }
    output.append("\n")
    val passingSdks = successPercentage.values.count { it == 100 }
    if (passingSdks > 0) {
      output.append("\n*+$passingSdks passing SDKs")
    }
    return output.toString()
  }

  private fun generateDailyTable(reports: List<TestReport>): String {
    val output = StringBuilder("| |")
    val now = LocalDate.now()
    for ((offset, report) in reports.withIndex().reversed()) {
      val time = now.minusDays(offset.toLong())
      val month = shortMonths.getOrDefault(time.month, "???")
      val day = time.dayOfMonth
      output.append(" $month $day")
      output.append(" |")
    }
    output.append(" Success Rate |\n|")
    output.append(" :--- |")
    output.append(" :---: |".repeat(reports.size))
    output.append(" :--- |")
    output.append("\n| AI Daily Tests |")
    for (report in reports) {
      val icon =
        when (report.status) {
          TestReport.Status.SUCCESS -> "✅"
          TestReport.Status.FAILURE -> "⛔"
          TestReport.Status.OTHER -> "➖"
        }
      val link: String = " [%s](%s)".format(icon, report.url)
      output.append(link)
      output.append(" |")
    }
    output.append(" ")
    val successChance: Int =
      reports.filter { r -> r.status == TestReport.Status.SUCCESS }.size * 100 / reports.size
    if (successChance == 100) {
      output.append("✅ 100%")
    } else {
      output.append("⛔ $successChance%")
    }
    output.append(" |")
    output.append("\n")
    return output.toString()
  }

  private fun parseTestReports(commit: String): List<TestReport> {
    val runs = request("actions/runs?head_sha=$commit")
    for (el in runs["workflow_runs"] as JsonArray) {
      val run = el as JsonObject
      val name =
        run["name"]?.jsonPrimitive?.content ?: throw RuntimeException("Couldn't find CI name")
      if (name == "CI Tests") {
        return parseCITests(
          run["id"]?.jsonPrimitive?.content
            ?: throw RuntimeException("Couldn't find run id for $commit run $name"),
          commit,
        )
      }
    }
    return emptyList()
  }

  private fun parseCITests(id: String, commit: String): List<TestReport> {
    val reports: MutableList<TestReport> = mutableListOf()
    val jobs = request("actions/runs/$id/jobs")
    for (el in jobs["jobs"] as JsonArray) {
      val job = el as JsonObject
      val jobName =
        job["name"]?.jsonPrimitive?.content
          ?: throw RuntimeException("Couldn't find name for job $id")
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
      (job["name"]?.jsonPrimitive ?: throw RuntimeException("Job missing name"))
        .content
        .split("(:")
        .dropLastWhile { it.isEmpty() }
        .toTypedArray()[1]
    name = name.substring(0, name.length - 1) // Remove trailing ")"
    val status = TestReport.Status.of(job)
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
          )
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
      HttpRequest.newBuilder()
        .apply {
          if (payload == null) {
            GET()
          } else {
            POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
          }
        }
        .uri(uri)
        .header("Authorization", "Bearer $apiToken")
        .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
        .build()
    try {
      val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
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
              val m = NEXT_LINK_REGEX.matchEntire(part)
              if (m != null) {
                val url = m.groups[0]?.value ?: throw RuntimeException("Malformed groups")
                val p = request(URI.create(url), JsonObject::class.java)
                return@map JsonObject(
                  json.keys.associateWith { key: String ->
                    if (json[key] is JsonArray && p.containsKey(key) && p[key] is JsonArray) {
                      return@associateWith JsonArray(
                        (json[key] as JsonArray) + (p[key] as JsonArray)
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
    private const val GITHUB_API_VERSION = "2022-11-28"
    // Pulls the URL corresponding to the rel="next" link header, if present.
    // Ignores other link header values ("prev", etc) and ignores parameters
    // eg `<http://www.foo.bar/>; baz="qux"; rel="next";` -> `http://www.foo.bar/`
    private val NEXT_LINK_REGEX =
      Regex(
        "<([^>]*)>" + // eg `<http://www.foo.bar/>`
          "\\s*;" + // Link separator
          "(\\s*\\w+=\"\\w*\"\\s*;)" + // Capture other parameters, eg `foo="bar";`
          "\\s*rel=\"next\"" // Matches specifically rel=next
      )
    private val CLIENT: HttpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val shortMonths: Map<Month, String> = mapOf(
      Month.JANUARY to "Jan",
      Month.FEBRUARY to "Feb",
      Month.MARCH to "Mar",
      Month.APRIL to "Apr",
      Month.MAY to "May",
      Month.JUNE to "Jun",
      Month.JULY to "Jul",
      Month.AUGUST to "Aug",
      Month.SEPTEMBER to "Sep",
      Month.OCTOBER to "Oct",
      Month.NOVEMBER to "Nov",
      Month.DECEMBER to "Dec",
    )
  }
}
