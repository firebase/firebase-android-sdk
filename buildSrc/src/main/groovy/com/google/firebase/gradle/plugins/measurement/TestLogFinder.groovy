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

package com.google.firebase.gradle.plugins.measurement

/** A utility class that finds test log links on different CI systems. */
class TestLogFinder {

    /** Returns the link to the current running test. */
    static String getCurrentLogLink() {
        if (!System.getenv().containsKey("FIREBASE_CI")) {
            println "No test logs for local runs."
            return null
        }

        if (System.getenv().containsKey("PROW_JOB_ID")) {
            println "Prow CI detected."

            // https://github.com/kubernetes/test-infra/blob/master/prow/jobs.md
            def name = System.getenv("JOB_NAME")
            def type = System.getenv("JOB_TYPE")
            def build = System.getenv("BUILD_ID")
            def org = System.getenv("REPO_OWNER")
            def repo = System.getenv("REPO_NAME")
            def pr = System.getenv("PULL_NUMBER")

            def domain = "android-ci.firebaseopensource.com"
            def bucket = "android-ci"
            def dir = type == "presubmit" ? "pr-logs/pull/${org}_${repo}/${pr}" : "logs"
            def path = "${name}/${build}"
            def url = "https://${domain}/view/gcs/${bucket}/${dir}/${path}"

            return url
        }

    }
}
