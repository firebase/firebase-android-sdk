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

import org.gradle.api.Project

/** A helper class for uploading a metric report to the metrics service. */
class MetricsReportUploader {
    private static final def METRICS_SERVICE_URL = System.getenv("METRICS_SERVICE_URL")

    /** Uploads the given metric report .*/
    static void upload(Project project, String report) {
        if (!System.getenv().containsKey("FIREBASE_CI")) {
            project.logger.quiet "Metrics upload is enabled only on CI."
            return
        }

        def owner = null
        def repo = null
        def baseCommit = null
        def headCommit = null
        def pullRequest = null

        if (System.getenv().containsKey("PROW_JOB_ID")) {
            project.logger.quiet "Prow CI detected."

            owner = System.getenv("REPO_OWNER")
            repo = System.getenv("REPO_NAME")
            baseCommit = System.getenv("PULL_BASE_SHA")
            headCommit = System.getenv("PULL_PULL_SHA") ?: baseCommit
            pullRequest = System.getenv("PULL_NUMBER")
        } else {
            project.logger.quiet "CI other than Prow is not supported currently."
        }

        post(project, report, owner, repo, headCommit, baseCommit, pullRequest)
    }

    private static void post(Project project, String report, String owner, String repo,
                             String commit, String baseCommit, String pullRequest) {
        def post = '-X POST'
        def headerAuth = '-H "Authorization: Bearer $(gcloud auth print-identity-token)"'
        def headerContentType = '-H "Content-Type: application/json"'
        def body = "-d @${report}"

        def endpoint = "${METRICS_SERVICE_URL}/repos/${owner}/${repo}/commits/${commit}/reports"
        if (baseCommit && pullRequest) {
            endpoint += "?base_commit=${baseCommit}&pull_request=${pullRequest}"
        }

        def request = "curl ${post} ${headerAuth} ${headerContentType} ${body} \"${endpoint}\""

        project.logger.quiet "Making post request: ${request} ..."

        project.exec {
            commandLine 'bash', '-c', request
        }
    }

}
