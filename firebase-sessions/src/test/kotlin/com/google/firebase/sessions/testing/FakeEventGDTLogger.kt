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

import com.google.firebase.sessions.EventGDTLogger
import com.google.firebase.sessions.EventGDTLoggerInterface
import com.google.firebase.sessions.SessionEvent

/**
 * The [FakeEventGDTLogger] is for mocking [EventGDTLogger].
 *
 * @hide
 */
internal class FakeEventGDTLogger : EventGDTLoggerInterface {

  // Lets tests assert the right event was logged.
  var loggedEvent: SessionEvent? = null

  override fun log(sessionEvent: SessionEvent) {
    loggedEvent = sessionEvent
  }
}
