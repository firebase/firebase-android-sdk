/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions.testing

import com.google.firebase.sessions.settings.CrashlyticsSettingsFetcher
import kotlin.time.Duration
import org.json.JSONObject

/**
 * Fake [CrashlyticsSettingsFetcher] that, when fetched, will produce given [responseJSONObject]
 * after simulating a [networkDelay], while keeping count of calls to [doConfigFetch].
 */
internal class FakeRemoteConfigFetcher(
  var responseJSONObject: JSONObject = JSONObject(),
  private val networkDelay: Duration = Duration.ZERO,
) : CrashlyticsSettingsFetcher {
  /** The number of times [doConfigFetch] was called on this instance. */
  var timesCalled: Int = 0
    private set

  override suspend fun doConfigFetch(
    headerOptions: Map<String, String>,
    onSuccess: suspend (JSONObject) -> Unit,
    onFailure: suspend (String) -> Unit,
  ) {
    timesCalled++
    if (networkDelay.isPositive()) {
      @Suppress("BlockingMethodInNonBlockingContext") // Using sleep, not delay, for runTest.
      Thread.sleep(networkDelay.inWholeMilliseconds)
    }
    onSuccess(responseJSONObject)
  }
}
