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


package com.google.firebase.gradle.plugins.measurement.aarsize

/** A helper class that generates the AAR size measurement JSON report. */
class AarSizeJsonBuilder {

    private static final String PULL_REQUEST_TABLE = "AndroidPullRequests"
    private static final String PULL_REQUEST_COLUMN = "pull_request_id"
    private static final String AAR_SIZE_TABLE = "AndroidAarSizes"
    private static final String SDK_COLUMN = "sdk_id"
    private static final String AAR_SIZE_COLUMN = "aar_size"

    // This comes in as a String and goes out as a String, so we might as well keep it a String
    private final String pullRequestNumber
    private final List<Tuple2<Integer, Integer>> sdkAarSizes

    AarSizeJsonBuilder(pullRequestNumber) {
        this.pullRequestNumber = pullRequestNumber
        this.sdkAarSizes = []
    }

    def addAarSize(sdkId, size) {
        sdkAarSizes.add(new Tuple2(sdkId, size))
    }

    def toJsonString() {
        if (sdkAarSizes.isEmpty()) {
            throw new IllegalStateException("Empty - No sizes were added")
        }

        def sizes = sdkAarSizes.collect {
            "[$pullRequestNumber, \"$it.first\", $it.second]"
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
                        table_name: "$AAR_SIZE_TABLE",
                        column_names: ["$PULL_REQUEST_COLUMN", "$SDK_COLUMN", "$AAR_SIZE_COLUMN"],
                        replace_measurements: [$sizes],
                    },
                ],
            }
        """

        return json
    }
}
