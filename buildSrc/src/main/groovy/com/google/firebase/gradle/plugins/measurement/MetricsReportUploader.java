// Copyright 2020 Google LLC
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

package com.google.firebase.gradle.plugins.measurement;

import org.gradle.api.Project;

/** A helper class for uploading a metric report to the metrics service. */
public class MetricsReportUploader {
  private static final String METRICS_SERVICE_URL = System.getenv("METRICS_SERVICE_URL");

  /** Uploads the given metric report . */
  public static void upload(Project project, String report) {
    if (!System.getenv().containsKey("FIREBASE_CI")) {
      project.getLogger().quiet("Metrics upload is enabled only on CI.");
      return;
    }

    if (!System.getenv().containsKey("PROW_JOB_ID")) {
      project.getLogger().quiet("Expecting Prow. Other CI is not supported now.");
      return;
    }

    String owner = System.getenv("REPO_OWNER");
    String repo = System.getenv("REPO_NAME");
    String baseCommit = System.getenv("PULL_BASE_SHA");
    String headCommit = System.getenv("PULL_PULL_SHA");
    String pullRequest = System.getenv("PULL_NUMBER");

    String commit = headCommit != null && !headCommit.isEmpty() ? headCommit : headCommit;

    post(project, report, owner, repo, commit, baseCommit, pullRequest);
  }

  private static void post(
      Project project,
      String report,
      String owner,
      String repo,
      String commit,
      String baseCommit,
      String pullRequest) {
    String post = "-X POST";
    String headerAuth = "-H \"Authorization: Bearer $(gcloud auth print-identity-token)\"";
    String headerContentType = "-H \"Content-Type: application/json\"";
    String body = String.format("-d @%s", report);

    String template = "%s/repos/%s/%s/commits/%s/reports";
    String endpoint = String.format(template, METRICS_SERVICE_URL, owner, repo, commit);
    if (pullRequest != null && !pullRequest.isEmpty()) {
      endpoint += String.format("?base_commit=%s&pull_request=%s", baseCommit, pullRequest);
    }

    String request =
        String.format(
            "curl %s %s %s %s \"%s\"", post, headerAuth, headerContentType, body, endpoint);

    project.getLogger().quiet("Making post request: {} ...", request);

    project.exec(
        it -> {
          it.commandLine("bash", "-c", request);
        });
  }
}
