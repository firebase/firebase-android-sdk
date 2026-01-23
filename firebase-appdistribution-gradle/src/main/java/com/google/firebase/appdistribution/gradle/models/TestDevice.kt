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
 * A device on which automated tests can be run.
 *
 * This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Firebase App Distribution API.
 */
data class TestDevice(
  /**
   * Optional. The locale of the device (e.g. "en_US" for US English) during the test. The value may
   * be `null`.
   */
  @SerializedName("locale") val locale: String? = null,

  /** Required. The device model. The value may be `null`. */
  @SerializedName("model") val model: String? = null,

  /** Optional. The orientation of the device during the test. The value may be `null`. */
  @SerializedName("orientation") val orientation: String? = null,

  /** Required. The version of the device (API level on Android). The value may be `null`. */
  @SerializedName("version") val version: String? = null,
) {
  override fun toString(): String {
    return "TestDevice{" +
      "model='" +
      model +
      '\'' +
      "version='" +
      version +
      '\'' +
      "orientation='" +
      orientation +
      '\'' +
      "locale='" +
      locale +
      '\'' +
      '}'
  }
}
