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
 * An app crash that occurred during an automated test.
 *
 * This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Firebase App Distribution API.
 */
data class AppCrash(
  /** Output only. The message associated with the crash. The value may be `null`. */
  @SerializedName("message") val message: String? = null,
  /** Output only. The raw stack trace. The value may be `null`. */
  @SerializedName("stackTrace") val stackTrace: String? = null
)
