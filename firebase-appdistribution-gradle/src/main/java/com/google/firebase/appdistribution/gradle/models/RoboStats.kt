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
 * Statistics collected during a Robo test.
 *
 * This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Firebase App Distribution API.
 */
data class RoboStats(
  /** Output only. Number of actions that crawler performed. The value may be `null`. */
  @SerializedName("actionsPerformed") val actionsPerformed: Int? = null,

  /** Output only. Duration of crawl. The value may be `null`. */
  @SerializedName("crawlDuration") val crawlDuration: String? = null,

  /** Output only. Number of distinct screens visited. The value may be `null`. */
  @SerializedName("distinctVisitedScreens") val distinctVisitedScreens: Int? = null,

  /** Output only. Whether the main activity crawl timed out. The value may be `null`. */
  @SerializedName("mainActivityCrawlTimedOut") val mainActivityCrawlTimedOut: Boolean? = null,
)
