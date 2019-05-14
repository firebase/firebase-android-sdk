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

/** A helper class that generates the APK size measurement JSON report. */
class ApkSizeJsonBuilder {

    private static final String PULL_REQUEST_TABLE = "PullRequests"
    private static final String PULL_REQUEST_COLUMN = "pull_request_id"
    private static final String APK_SIZE_TABLE = "ApkSizes"
    private static final String SDK_COLUMN = "sdk_id"
    private static final String APK_SIZE_COLUMN = "apk_size"

    // This comes in as a String and goes out as a String, so we might as well keep it a String
    private final String pullRequestNumber
    private final List<Tuple2<Integer, Integer>> sdkSizes

    ApkSizeJsonBuilder(pullRequestNumber) {
        this.pullRequestNumber = pullRequestNumber
        this.sdkSizes = []
    }

    def addApkSize(sdkId, size) {
        sdkSizes.add(new Tuple2(sdkId, size))
    }

    def toJsonString() {
        if (sdkSizes.isEmpty()) {
            throw new IllegalStateException("No sizes were added")
        }

        def sizes = sdkSizes.collect {
            "[$pullRequestNumber, $it.first, $it.second]"
        }.join(", ")

        def json = """
            {
                tables: [
                    {
                        table_name: "$PULL_REQUEST_TABLE",
                        column_names: ["$PULL_REQUEST_COLUMN"],
                        replace_measurements: [[$pullRequestNumber]],
                    },
                    {
                        table_name: "$APK_SIZE_TABLE",
                        column_names: ["$PULL_REQUEST_COLUMN", "$SDK_COLUMN", "$APK_SIZE_COLUMN"],
                        replace_measurements: [$sizes],
                    },
                ],
            }
        """

        return json
    }
}
