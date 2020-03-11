// Copyright 2018 Google LLC
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


package com.google.firebase.gradle.plugins.measurement.apksize

import static com.google.firebase.gradle.plugins.measurement.MetricsServiceApi.*
import com.google.firebase.gradle.plugins.measurement.TestLogFinder
import groovy.json.JsonOutput

/** A helper class that generates the APK size measurement JSON report. */
class ApkSizeJsonBuilder {

    private static final String PULL_REQUEST_TABLE = "AndroidPullRequests"
    private static final String PULL_REQUEST_COLUMN = "pull_request_id"
    private static final String APK_SIZE_TABLE = "AndroidApkSizes"
    private static final String SDK_COLUMN = "sdk_id"
    private static final String APK_SIZE_COLUMN = "apk_size"

    // This comes in as a String and goes out as a String, so we might as well keep it a String
    private final List<Tuple3<String, String, Integer>> sdkApkSizes

    ApkSizeJsonBuilder() {
        this.sdkApkSizes = []
    }

    def addApkSize(sdk, type, size) {
        sdkApkSizes.add(new Tuple3(sdk, type, size))
    }

    def toJsonString() {
        if (sdkApkSizes.isEmpty()) {
            throw new IllegalStateException("No sizes were added")
        }

        def results = sdkApkSizes.collect { new Result(it.first, "apk ($it.second)", it.third) }
        def log = TestLogFinder.generateCurrentLogLink()
        def report = new Report(Metric.BinarySize, results, log)

        return report.toJson()
    }
}
