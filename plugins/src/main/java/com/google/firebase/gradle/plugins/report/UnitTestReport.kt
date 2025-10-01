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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.FileWriter
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.gradle.internal.Pair
import java.io.File

@SuppressWarnings("NewApi")
class UnitTestReport(private val apiToken: String) {
  private val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  fun createReport(outputFile: File, commitCount: Int) {
    val response = request("commits?per_page=$commitCount", JsonArray::class.java)
    val commits =
      response
        .getAsJsonArray()
        .asList()
        .stream()
        .limit(commitCount.toLong())
        .map { el: JsonElement ->
          val obj = el.getAsJsonObject()
          var pr = -1
          val matcher: Matcher =
            PR_NUMBER_MATCHER.matcher(obj.getAsJsonObject("commit").get("message").asString)
          if (matcher.find()) {
            pr = matcher.group(1).toInt()
          }
          ReportCommit(obj.get("sha").asString, pr)
        }
        .toList()
    outputReport(outputFile, commits)
  }

  private fun outputReport(outputFile: File, commits: List<ReportCommit>) {
    val reports: MutableList<TestReport> = ArrayList()
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
      val writer = FileWriter(outputFile)
      writer.append(output.toString())
      writer.close()
    } catch (e: Exception) {
      throw RuntimeException("Error writing report file", e)
    }
  }

  private fun generateTable(reportCommits: List<ReportCommit>, reports: List<TestReport>): String {
    val commitLookup = reportCommits.associateBy(ReportCommit::sha)
    val commits = reports.map(TestReport::commit).distinct()
    var sdks = reports.map(TestReport::name).distinct().sorted()
    val lookup = reports.associateBy({ report -> Pair.of(report.name, report.commit) })
    val successPercentage: MutableMap<String, Int> = HashMap()
    var passingSdks = 0
    // Get success percentage
    for (sdk in sdks) {
      var sdkTestCount = 0
      var sdkTestSuccess = 0
      for (commit in commits) {
        if (lookup.containsKey(Pair.of(sdk, commit))) {
          val report: TestReport = lookup.get(Pair.of(sdk, commit))!!
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
        .sortedBy { o: String -> successPercentage[o]!! }
    if (sdks.isEmpty()) {
      return "*All tests passing*\n"
    }
    val output = StringBuilder("| |")
    for (commit in commits) {
      val rc = commitLookup.get(commit)
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
      output.append("\n| ").append(sdk).append(" |")
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
      val successChance: Int = successPercentage.get(sdk)!!
      if (successChance == 100) {
        output.append("✅ 100%")
      } else {
        output.append("⛔ ").append(successChance).append("%")
      }
      output.append(" |")
    }
    output.append("\n")
    if (passingSdks > 0) {
      output.append("\n*+").append(passingSdks).append(" passing SDKs*\n")
    }
    return output.toString()
  }

  private fun parseTestReports(commit: String): List<TestReport> {
    val runs = request("actions/runs?head_sha=" + commit)
    for (el in runs.getAsJsonArray("workflow_runs")) {
      val run = el.getAsJsonObject()
      val name = run.get("name").getAsString()
      if (name == "CI Tests") {
        return parseCITests(run.get("id").getAsString(), commit)
      }
    }
    return listOf()
  }

  private fun parseCITests(id: String, commit: String): List<TestReport> {
    val reports: MutableList<TestReport> = ArrayList()
    val jobs = request("actions/runs/" + id + "/jobs")
    for (el in jobs.getAsJsonArray("jobs")) {
      val job = el.getAsJsonObject()
      val jid = job.get("name").getAsString()
      if (jid.startsWith("Unit Tests (:")) {
        reports.add(parseJob(TestReport.Type.UNIT_TEST, job, commit))
      } else if (jid.startsWith("Instrumentation Tests (:")) {
        reports.add(parseJob(TestReport.Type.INSTRUMENTATION_TEST, job, commit))
      }
    }
    return reports
  }

  private fun parseJob(type: TestReport.Type, job: JsonObject, commit: String): TestReport {
    var name =
      job
        .get("name")
        .getAsString()
        .split("\\(:".toRegex())
        .dropLastWhile { it.isEmpty() }
        .toTypedArray()[1]
    name = name.substring(0, name.length - 1) // Remove trailing ")"
    var status = TestReport.Status.OTHER
    if (job.get("status").asString == "completed") {
      if (job.get("conclusion").asString == "success") {
        status = TestReport.Status.SUCCESS
      } else {
        status = TestReport.Status.FAILURE
      }
    }
    val url = job.get("html_url").getAsString()
    return TestReport(name, type, status, commit, url)
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
  private fun <T> request(uri: URI, clazz: Class<T>): T {
    val request =
      HttpRequest.newBuilder()
        .GET()
        .uri(uri)
        .header("Authorization", "Bearer $apiToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()
    try {
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      val body = response.body()
      if (response.statusCode() >= 300) {
        System.err.println(response)
        System.err.println(body)
      }
      val json: T = GSON.fromJson(body, clazz)
      if (json is JsonObject) {
        // Retrieve and merge objects from other pages, if present
        response.headers().firstValue("Link").ifPresent { link: String ->
          val parts = link.split(",".toRegex()).dropLastWhile { it.isEmpty() }
          for (part in parts) {
            if (part.endsWith("rel=\"next\"")) {
              // <foo>; rel="next" -> foo
              val url =
                part
                  .split(">;".toRegex())
                  .dropLastWhile { it.isEmpty() }
                  .toTypedArray()[0]
                  .split("<".toRegex())
                  .dropLastWhile { it.isEmpty() }
                  .toTypedArray()[1]
              val p = request<JsonObject>(URI.create(url), JsonObject::class.java)
              for (key in json.keySet()) {
                if (json.get(key).isJsonArray && p.has(key) && p.get(key).isJsonArray) {
                  json.getAsJsonArray(key).addAll(p.getAsJsonArray(key))
                }
              }
              break
            }
          }
        }
      }
      return json
    } catch (e: IOException) {
      throw RuntimeException(e)
    } catch (e: InterruptedException) {
      throw RuntimeException(e)
    }
  }

  companion object {
    private val PR_NUMBER_MATCHER: Pattern = Pattern.compile(".*\\(#([0-9]+)\\)")
    private const val URL_PREFIX = "https://api.github.com/repos/firebase/firebase-android-sdk/"
    private val GSON: Gson = GsonBuilder().create()
  }
}
