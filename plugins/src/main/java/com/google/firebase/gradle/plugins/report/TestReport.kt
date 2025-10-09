/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.gradle.plugins.report


/**
 * Represents a single run of a test in CI. One unit/implementation test workflow run creates many `TestReport`s, one for each tested SDK.
 * @param name SDK name of the associated test run.
 * @param type What type of test result this is, either unit or instrumentation test.
 * @param status Conclusion status of the test run, `SUCCESS`/`FAILURE` for typical results, `OTHER` for ongoing runs and unexpected data.
 * @param commit Commit SHA this test was run on.
 * @param url Link to the GHA test run info, including logs.
 */
data class TestReport(
  val name: String,
  val type: Type,
  val status: Status,
  val commit: String,
  val url: String,
) {
  enum class Type {
    UNIT_TEST,
    INSTRUMENTATION_TEST,
  }

  enum class Status {
    SUCCESS,
    FAILURE,
    OTHER,
  }
}
