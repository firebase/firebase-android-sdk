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

/** A utility class that finds test log links on different CI systems. */
public class TestLogFinder {

  /** Returns the link to the current running test. */
  public static String generateCurrentLogLink() {
    if (!System.getenv().containsKey("FIREBASE_CI")) {
      System.out.println("No test logs for local runs.");
      return null;
    }

    if (System.getenv().containsKey("PROW_JOB_ID")) {
      System.out.println("Prow CI detected.");

      // https://github.com/kubernetes/test-infra/blob/master/prow/jobs.md
      String name = System.getenv("JOB_NAME");
      String type = System.getenv("JOB_TYPE");
      String build = System.getenv("BUILD_ID");
      String org = System.getenv("REPO_OWNER");
      String repo = System.getenv("REPO_NAME");
      String pr = System.getenv("PULL_NUMBER");

      String domain = "android-ci.firebaseopensource.com";
      String bucket = "android-ci";

      String dirPreSubmit = String.format("pr-logs/pull/%s_%s/%s", org, repo, pr);
      String dirPostSubmit = "logs";
      String dir = "presubmit".equalsIgnoreCase(type) ? dirPreSubmit : dirPostSubmit;
      String path = String.format("%s/%s", name, build);

      return String.format("https://%s/view/gcs/%s/%s/%s", domain, bucket, dir, path);
    }

    return null;
  }
}
