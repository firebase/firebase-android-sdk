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

/** A place holder for all api objects of metric service. */
class MetricsServiceApi {

    /** An enum for all supported health metrics. */
    static enum Metric {
        BinarySize
    }

    /** An api object for a test result. */
    static class Result {
        String sdk
        String type
        double value

        Result(sdk, type, value) {
            this.sdk = sdk
            this.type = type
            this.value = value
        }
    }

    /** An api object for a test report. */
    static class Report {
        Metric metric
        List<Result> results
        String log

        Report(Metric metric, List<Result> results, String log) {
            this.metric = metric
            this.results = results
            this.log = log
        }
    }

}
