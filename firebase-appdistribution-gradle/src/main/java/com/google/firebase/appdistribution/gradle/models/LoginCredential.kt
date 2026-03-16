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
 * Login credential for automated tests
 *
 * This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Firebase App Distribution API.
 */
data class LoginCredential(
  /**
   * Optional. Hints to the crawler for identifying input fields
   *
   * @return value or `null` for none
   */
  /** Optional. Hints to the crawler for identifying input fields The value may be `null`. */
  @SerializedName("fieldHints") val fieldHints: LoginCredentialFieldHints? = null,
  /**
   * Optional. Are these credentials for Google?
   *
   * @return value or `null` for none
   */
  /** Optional. Are these credentials for Google? The value may be `null`. */
  var google: Boolean? = null,
  /**
   * Optional. Password for automated tests
   *
   * @return value or `null` for none
   */
  /** Optional. Password for automated tests The value may be `null`. */
  var password: String? = null,
  /**
   * Optional. Username for automated tests
   *
   * @return value or `null` for none
   */
  /** Optional. Username for automated tests The value may be `null`. */
  var username: String? = null,
)
