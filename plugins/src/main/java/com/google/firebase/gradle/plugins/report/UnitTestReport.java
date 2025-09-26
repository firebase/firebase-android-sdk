// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.internal.Pair;
import org.gradle.internal.impldep.org.eclipse.jgit.annotations.NonNull;

@SuppressWarnings("NewApi")
public class UnitTestReport {
  private static final Pattern PR_NUMBER_MATCHER = Pattern.compile(".*\\(#([0-9]+)\\)");
  private static final String URL_PREFIX =
      "https://api.github.com/repos/firebase/firebase-android-sdk/";
  private static final Gson GSON = new GsonBuilder().create();
  private final HttpClient client;
  private final String apiToken;

  public UnitTestReport(@NonNull String apiToken) {
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.apiToken = apiToken;
  }

  public void createReport(int commitCount) {
    JsonArray response = request("commits", JsonArray.class);
    List<ReportCommit> commits =
        response.getAsJsonArray().asList().stream()
            .limit(commitCount)
            .map(
                (el) -> {
                  JsonObject obj = el.getAsJsonObject();
                  int pr = -1;
                  Matcher matcher =
                      PR_NUMBER_MATCHER.matcher(
                          obj.getAsJsonObject("commit").get("message").getAsString());
                  if (matcher.find()) {
                    pr = Integer.parseInt(matcher.group(1));
                  }
                  return new ReportCommit(obj.get("sha").getAsString(), pr);
                })
            .toList();
    outputReport(commits);
  }

  public void outputReport(@NonNull List<ReportCommit> commits) {
    List<TestReport> reports = new ArrayList<>();
    for (ReportCommit commit : commits) {
      reports.addAll(parseTestReports(commit.sha()));
    }
    StringBuilder output = new StringBuilder();
    output.append("### Unit Tests\n\n");
    output.append(
        generateTable(
            commits, reports.stream().filter(r -> r.type() == TestReport.Type.UNIT_TEST).toList()));
    output.append("\n");
    output.append("### Instrumentation Tests\n\n");
    output.append(
        generateTable(
            commits,
            reports.stream()
                .filter(r -> r.type() == TestReport.Type.INSTRUMENTATION_TEST)
                .toList()));
    output.append("\n");

    try {
      FileWriter writer = new FileWriter("test-report.md");
      writer.append(output.toString());
      writer.close();
    } catch (Exception e) {
      throw new RuntimeException("Error writing report file", e);
    }
  }

  public @NonNull String generateTable(
      @NonNull List<ReportCommit> reportCommits, @NonNull List<TestReport> reports) {
    Map<String, ReportCommit> commitLookup =
        reportCommits.stream().collect(Collectors.toMap(ReportCommit::sha, c -> c));
    List<String> commits = reports.stream().map(TestReport::commit).distinct().toList();
    List<String> sdks = reports.stream().map(TestReport::name).distinct().sorted().toList();
    Map<Pair<String, String>, TestReport> lookup = new HashMap<>();
    for (TestReport report : reports) {
      lookup.put(Pair.of(report.name(), report.commit()), report);
    }
    Map<String, Integer> successPercentage = new HashMap<>();
    int passingSdks = 0;
    // Get success percentage
    for (String sdk : sdks) {
      int sdkTestCount = 0;
      int sdkTestSuccess = 0;
      for (String commit : commits) {
        if (lookup.containsKey(Pair.of(sdk, commit))) {
          TestReport report = lookup.get(Pair.of(sdk, commit));
          if (report.status() != TestReport.Status.OTHER) {
            sdkTestCount++;
            if (report.status() == TestReport.Status.SUCCESS) {
              sdkTestSuccess++;
            }
          }
        }
      }
      if (sdkTestSuccess == sdkTestCount) {
        passingSdks++;
      }
      successPercentage.put(sdk, sdkTestSuccess * 100 / sdkTestCount);
    }
    sdks =
        sdks.stream()
            .filter(s -> successPercentage.get(s) != 100)
            .sorted(Comparator.comparing(successPercentage::get))
            .toList();
    if (sdks.isEmpty()) {
      return "*All tests passing*\n";
    }
    StringBuilder output = new StringBuilder("| |");
    for (String commit : commits) {
      ReportCommit rc = commitLookup.get(commit);
      output.append(" ");
      if (rc != null && rc.pr() != -1) {
        output
            .append("[#")
            .append(rc.pr())
            .append("](https://github.com/firebase/firebase-android-sdk/pull/")
            .append(rc.pr())
            .append(")");
      } else {
        output.append(commit);
      }
      output.append(" |");
    }
    output.append(" Success Rate |\n|");
    output.append(" :--- |");
    output.append(" :---: |".repeat(commits.size()));
    output.append(" :--- |");
    for (String sdk : sdks) {
      output.append("\n| ").append(sdk).append(" |");
      for (String commit : commits) {
        if (lookup.containsKey(Pair.of(sdk, commit))) {
          TestReport report = lookup.get(Pair.of(sdk, commit));
          String icon =
              switch (report.status()) {
                case SUCCESS -> "✅";
                case FAILURE -> "⛔";
                case OTHER -> "➖";
              };
          String link = " [%s](%s)".formatted(icon, report.url());
          output.append(link);
        }
        output.append(" |");
      }
      output.append(" ");
      int successChance = successPercentage.get(sdk);
      if (successChance == 100) {
        output.append("✅ 100%");
      } else {
        output.append("⛔ ").append(successChance).append("%");
      }
      output.append(" |");
    }
    output.append("\n");
    if (passingSdks > 0) {
      output.append("\n*+").append(passingSdks).append(" passing SDKs*\n");
    }
    return output.toString();
  }

  public @NonNull List<TestReport> parseTestReports(@NonNull String commit) {
    JsonObject runs = request("actions/runs?head_sha=" + commit);
    for (JsonElement el : runs.getAsJsonArray("workflow_runs")) {
      JsonObject run = el.getAsJsonObject();
      String name = run.get("name").getAsString();
      if (Objects.equals(name, "CI Tests")) {
        return parseCITests(run.get("id").getAsString(), commit);
      }
    }
    return List.of();
  }

  public @NonNull List<TestReport> parseCITests(@NonNull String id, @NonNull String commit) {
    List<TestReport> reports = new ArrayList<>();
    JsonObject jobs = request("actions/runs/" + id + "/jobs");
    for (JsonElement el : jobs.getAsJsonArray("jobs")) {
      JsonObject job = el.getAsJsonObject();
      String jid = job.get("name").getAsString();
      if (jid.startsWith("Unit Tests (:")) {
        reports.add(parseJob(TestReport.Type.UNIT_TEST, job, commit));
      } else if (jid.startsWith("Instrumentation Tests (:")) {
        reports.add(parseJob(TestReport.Type.INSTRUMENTATION_TEST, job, commit));
      }
    }
    return reports;
  }

  public @NonNull TestReport parseJob(
      @NonNull TestReport.Type type, @NonNull JsonObject job, @NonNull String commit) {
    String name = job.get("name").getAsString().split("\\(:")[1];
    name = name.substring(0, name.length() - 1); // Remove trailing ")"
    TestReport.Status status = TestReport.Status.OTHER;
    if (Objects.equals(job.get("status").getAsString(), "completed")) {
      if (Objects.equals(job.get("conclusion").getAsString(), "success")) {
        status = TestReport.Status.SUCCESS;
      } else {
        status = TestReport.Status.FAILURE;
      }
    }
    String url = job.get("html_url").getAsString();
    return new TestReport(name, type, status, commit, url);
  }

  private JsonObject request(String path) {
    return request(path, JsonObject.class);
  }

  private <T> T request(String path, Class<T> clazz) {
    return request(URI.create(URL_PREFIX + path), clazz);
  }

  /**
   * Abstracts away paginated calling.
   * Naively joins pages together by merging root level arrays.
   */
  private <T> T request(URI uri, Class<T> clazz) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .GET()
            .uri(uri)
            .header("Authorization", "Bearer " + apiToken)
            .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body();
      if (response.statusCode() >= 300) {
        System.err.println(response);
        System.err.println(body);
      }
      T json = GSON.fromJson(body, clazz);
      if (json instanceof JsonObject obj) {
        // Retrieve and merge objects from other pages, if present
        response
            .headers()
            .firstValue("Link")
            .ifPresent(
                link -> {
                  List<String> parts = Arrays.stream(link.split(",")).toList();
                  for (String part : parts) {
                    if (part.endsWith("rel=\"next\"")) {
                      // <foo>; rel="next" -> foo
                      String url = part.split(">;")[0].split("<")[1];
                      JsonObject p = request(URI.create(url), JsonObject.class);
                      for (String key : obj.keySet()) {
                        if (obj.get(key).isJsonArray() && p.has(key) && p.get(key).isJsonArray()) {
                          obj.getAsJsonArray(key).addAll(p.getAsJsonArray(key));
                        }
                      }
                      break;
                    }
                  }
                });
      }
      return json;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
