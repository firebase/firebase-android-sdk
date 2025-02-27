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

import com.google.firebase.sessions.SessionDatastore

/**
 * Fake implementaiton of the [SessionDatastore] that allows for inspecting and modifying the
 * currently stored values in unit tests.
 */
internal class FakeSessionDatastore : SessionDatastore {

  /** The currently stored value */
  private var currentSessionId: String? = null

  override fun updateSessionId(sessionId: String) {
    currentSessionId = sessionId
  }

  override fun getCurrentSessionId() = currentSessionId
}
