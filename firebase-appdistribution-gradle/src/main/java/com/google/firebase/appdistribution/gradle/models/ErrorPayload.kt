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
 * POJO to deserialize the relevant content of an error payload in a [WrappedErrorResponse]. EG:
 * ```
 * {
 * "code": 400,
 * "message": "Request contains an invalid argument.",
 * "status": "INVALID_ARGUMENT"
 * }
 * ```
 */
data class ErrorPayload(
  @SerializedName("message") val message: String? = null,
  @SerializedName("code") val code: Int = 0,
  @SerializedName("status") val status: String? = null,
  @SerializedName("details") val details: List<ErrorDetail>? = null,
)

data class ErrorDetail(
  @SerializedName("@type") val type: String? = null,
  @SerializedName("message") val message: String? = null,
  @SerializedName("locale") val locale: String? = null,
)
