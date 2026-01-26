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
 * The results of running an automated test on a particular device.
 *
 * This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Firebase App Distribution API.
 */
data class DeviceExecution(
  /** Output only. An app crash, if any occurred during the test. The value may be `null`. */
  @SerializedName("appCrash") val appCrash: AppCrash? = null,
  /** Output only. A URI to an image of the Robo crawl graph. The value may be `null`. */
  @SerializedName("crawlGraphUri") val crawlGraphUri: String? = null,

  /** Required. The device that the test was run on. The value may be `null`. */
  @SerializedName("device") val device: TestDevice? = null,

  /** Output only. The reason why the test failed. The value may be `null`. */
  @SerializedName("failedReason") val failedReason: String? = null,

  /** Output only. The reason why the test was inconclusive. The value may be `null`. */
  @SerializedName("inconclusiveReason") val inconclusiveReason: String? = null,

  /**
   * Output only. The path to a directory in Cloud Storage that will eventually contain the results
   * for this execution. For example, gs://bucket/Nexus5-18-en-portrait. The value may be `null`.
   */
  @SerializedName("resultsStoragePath") val resultsStoragePath: String? = null,

  /** Output only. The statistics collected during the Robo test. The value may be `null`. */
  @SerializedName("roboStats") val roboStats: RoboStats? = null,

  /**
   * Output only. A list of screenshot image URIs taken from the Robo crawl. The file names are
   * numbered by the order in which they were taken. The value may be `null`.
   */
  @SerializedName("screenshotUris") val screenshotUris: List<String>? = null,

  /** Output only. The state of the test. The value may be `null`. */
  @SerializedName("state") val state: String? = null,

  /** Output only. A URI to a video of the test run. The value may be `null`. */
  @SerializedName("videoUri") val videoUri: String? = null,
)
