// Copyright 2019 Google LLC
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

package com.google.firebase.gradle.plugins.ci.device

import com.android.builder.testing.api.TestServer
import com.google.firebase.gradle.plugins.ci.Environment

import java.nio.file.Paths
import org.gradle.api.Project


class FirebaseTestServer extends TestServer {
    private static final String DEFAULT_BUCKET_NAME = 'ftl-results'
    final Project project
    final FirebaseTestLabExtension extension
    final Random random

    FirebaseTestServer(Project project, FirebaseTestLabExtension extension) {
        this.project = project
        this.extension = extension
        this.random = new Random(System.currentTimeMillis())
    }

    @Override
    String getName() {
        return "firebase-test-lab"
    }

    @Override
    void uploadApks(String variantName, File testApk, File testedApk) {
        // test lab requires an "app" apk, so we give an empty apk to it.
        String testedApkPath = testedApk ?: "$project.rootDir/buildSrc/resources/dummy.apk"

        project.logger.warn("Uploading for $variantName: testApk=$testApk, testedApk=$testedApkPath")

        def devicesCmd = extension.devices.collectMany { ['--device', it] }

        def resultsArgs = getResultUploadArgs()

        project.exec {
            commandLine('gcloud', 'firebase', 'test', 'android', 'run',
                    '--type=instrumentation',
                    "--app=$testedApkPath", "--test=$testApk",
                    '--no-auto-google-login', '--no-record-video', '--no-performance-metrics', '-q',
                    "--results-history-name=$project.path",
                    *resultsArgs, *devicesCmd)
        }
    }

    @Override
    boolean isConfigured() {
        return extension.enabled
    }

    private List<String> getResultUploadArgs() {
        String resultsBucket = null
        String resultsDir = null

        Optional<String> resultsBucketFromEnv = Optional.ofNullable(System.getenv('FTL_RESULTS_BUCKET')).map(Environment.&expand)
        Optional<String> resultsDirFromEnv = Optional.ofNullable(System.getenv('FTL_RESULTS_DIR')).map(Environment.&expand)

        Optional<String> ci = Optional.ofNullable(System.getenv('FIREBASE_CI'))
        Optional<String> repoOwner = Optional.ofNullable(System.getenv('REPO_OWNER'))
        Optional<String> jobType = Optional.ofNullable(System.getenv('JOB_TYPE'))

        if (resultsBucketFromEnv.isPresent()) {
            resultsBucket = resultsBucketFromEnv.get()
        } else {
            if (ci.isPresent()) {
                if (repoOwner.get().equalsIgnoreCase('firebase')) {
                    resultsBucket = 'android-ci'
                } else {
                    resultsBucket = 'fireescape'
                }
            } else {
                resultsBucket = DEFAULT_BUCKET_NAME
            }
        }

        if (resultsDirFromEnv.isPresent()) {
            resultsDir = Paths.get(resultsDir.get(), "${project.path}_${random.nextLong()}")
        } else {
            if (ci.isPresent()) {
                if (jobType.get().equalsIgnoreCase('presubmit')) {
                    // TODO(yifany): get these log locations programmatically
                    resultsDir = Environment.expand("pr-logs/pull/$(REPO_OWNER)_$(REPO_NAME)/$(PULL_NUMBER)/$(JOB_NAME)/$(BUILD_ID)/artifacts/")
                } else if (jobType.get().equalsIgnoreCase('postsubmit')){
                    resultsDir = Environment.expand("logs/$(JOB_NAME)/$(BUILD_ID)/artifacts/")
                }
            }
        }

        List<String> args = ['--results-bucket', resultsBucket]
        if (resultsDir) {
            args += ['--results-dir', resultsDir]
        }
        return args
    }
}
