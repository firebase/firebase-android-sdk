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

package com.google.firebase.vertexai.common

import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Configurable options unique to how requests to the backend are performed.
 *
 * @property timeout the maximum amount of time for a request to take, from the first request to
 * first response.
 * @property apiVersion the api endpoint to call.
 */
class RequestOptions(
  val timeout: Duration,
  val apiVersion: String = "v1beta",
  val endpoint: String = "https://generativelanguage.googleapis.com",
) {
  @JvmOverloads
  constructor(
    timeout: Long? = HttpTimeout.INFINITE_TIMEOUT_MS,
    apiVersion: String = "v1beta",
    endpoint: String = "https://generativelanguage.googleapis.com",
  ) : this(
    (timeout ?: HttpTimeout.INFINITE_TIMEOUT_MS).toDuration(DurationUnit.MILLISECONDS),
    apiVersion,
    endpoint,
  )
}
