/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.appdistribution.gradle.models

import com.google.gson.annotations.SerializedName

/**
 * The results of running an automated test on a release.
 *
 * This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Firebase App Distribution API.
 */
data class ReleaseTest(
  /**
   * Output only. Timestamp when the test was run.
   *
   * @return value or `null` for none
   */
  /** Output only. Timestamp when the test was run. The value may be `null`. */
  @SerializedName("createTime") val createTime: String? = null,
  /**
   * Required. The results of the test on each device.
   *
   * @return value or `null` for none
   */
  /** Required. The results of the test on each device. The value may be `null`. */
  @SerializedName("deviceExecutions") val deviceExecutions: List<DeviceExecution>? = null,

  /**
   * Optional. Input only. Login credentials for the test. Input only.
   *
   * @return value or `null` for none
   */
  /** Optional. Input only. Login credentials for the test. Input only. The value may be `null`. */
  @SerializedName("loginCredential") val loginCredential: LoginCredential? = null,

  /**
   * The name of the release test resource. Format:
   * `projects/{project_number}/apps/{app_id}/releases/{release_id}/tests/{test_id}` The value may
   * be `null`.
   */
  @SerializedName("name") val name: String? = null,

  /**
   * The test case that was used to generate this release test. Format:
   * projects/{project_number}/apps/{app}/testCases/{test_case}`
   */
  @SerializedName("testCase") val testCase: String? = null
)
