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

package com.google.firebase.gradle.plugins.measurement.utils.reports

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Holds multiple tables {@link Table} and can be serialized into the format
 * complies to the uploader tool.
 *
 * <p>For example:
 * {
 *   "tables": [
 *     {
 *       "table_name": "PullRequests",
 *       "column_names": [
 *         "pull_request_id"
 *       ],
 *       "replace_measurements": [["777"]]
 *     },
 *     {
 *       "table_name": "Coverage2",
 *       "column_names": [
 *         "pull_request_id",
 *         "sdk_id",
 *         "coverage_percent"
 *       ],
 *       "replace_measurements": [
 *         ["777", 0, 0.7061310782241015],
 *         ["777", 1, 0.9090909090909091],
 *         ["777", 2, 0.509516668589226],
 *         ["777", 3, 0.7685185185185185],
 *         ["777", 4, 1.0],
 *         ["777", 5, 0.4179763137749358],
 *         ["777", 6, 0.2857142857142857],
 *         ["777", 7, 0.038461538461538464],
 *         ["777", 8, 0.28852056476365867],
 *         ["777", 9, 0.8417399352151782]
 *       ]
 *     }
 *   ]
 * }
 */
class JsonReport {
    @SerializedName("tables")
    List<Table> tables

    @Override
    String toString() {
        return new Gson().toJson(this)
    }
}
