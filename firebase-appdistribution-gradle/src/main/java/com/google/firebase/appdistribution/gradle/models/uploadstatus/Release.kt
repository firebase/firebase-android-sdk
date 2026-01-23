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
package com.google.firebase.appdistribution.gradle.models.uploadstatus

import com.google.gson.annotations.SerializedName

data class Release(
  @SerializedName("name") val name: String,
  @SerializedName("displayVersion") val displayVersion: String,
  @SerializedName("buildVersion") val buildVersion: String,
  @SerializedName("firebaseConsoleUri") val firebaseConsoleUri: String,
  @SerializedName("testingUri") val testingUri: String,
  @SerializedName("binaryDownloadUri") val binaryDownloadUri: String
)
