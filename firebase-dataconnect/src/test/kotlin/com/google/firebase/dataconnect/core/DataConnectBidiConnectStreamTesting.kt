/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.util.coroutines.ConflatedSignal

/**
 * Notifies a signal provided to the given block when any [DataConnectBidiConnectStream] object
 * enters exponential backoff before retrying a connection.
 */
internal inline fun <T> DataConnectBidiConnectStream.Companion.signalOnRetryForTesting(
  block: (ConflatedSignal<Long>) -> T
): T {
  val signal = ConflatedSignal<Long>()
  val callback = { backoffMillis: Long -> signal.signal(backoffMillis) }

  setOnRetryBackoffForTesting(callback)
  try {
    return block(signal)
  } finally {
    unsetOnRetryBackoffForTesting(callback)
  }
}
