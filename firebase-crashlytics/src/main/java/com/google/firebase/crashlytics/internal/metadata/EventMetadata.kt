// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.metadata

/**
 * A class that represents information to attach to a specific event.
 *
 * @property sessionId the sessionId to attach to the event.
 * @property timestamp the timestamp to attach to the event.
 * @property additionalCustomKeys a [Map<String, String>] of key value pairs to attach to the event,
 * in addition to the global custom keys.
 */
internal data class EventMetadata
@JvmOverloads
constructor(
  val sessionId: String,
  val timestamp: Long,
  val additionalCustomKeys: Map<String, String> = mapOf()
)
