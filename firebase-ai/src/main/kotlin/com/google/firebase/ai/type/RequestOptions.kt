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

package com.google.firebase.ai.type

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** Configurable options unique to how requests to the backend are performed. */
public class RequestOptions
internal constructor(
  internal val timeout: Duration,
  internal val endpoint: String = "https://firebasevertexai.googleapis.com",
  internal val apiVersion: String = "v1beta",
) {

  /**
   * Constructor for RequestOptions.
   *
   * @param timeoutInMillis the maximum amount of time, in milliseconds, for a request to take, from
   * the first request to first response.
   */
  @JvmOverloads
  public constructor(
    timeoutInMillis: Long = 180.seconds.inWholeMilliseconds,
  ) : this(
    timeout = timeoutInMillis.toDuration(DurationUnit.MILLISECONDS),
  )
}
