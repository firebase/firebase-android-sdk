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
import org.gradle.api.Project


class FirebaseTestServer extends TestServer {
    private static final String BUCKET_NAME = 'android-ci'
    final Project project
    final FirebaseTestLabExtension extension

    FirebaseTestServer(Project project, FirebaseTestLabExtension extension) {
        this.project = project
        this.extension = extension
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

        project.exec {
            commandLine('gcloud','firebase','test','android','run',
              '--type', 'instrumentation',
              "--results-bucket=$BUCKET_NAME", "--app=$testedApkPath", "--test=$testApk",
              '--no-auto-google-login', '--no-record-video', '--no-performance-metrics', '-q',
              *devicesCmd)
        }
    }

    @Override
    boolean isConfigured() {
        return true
    }
}
