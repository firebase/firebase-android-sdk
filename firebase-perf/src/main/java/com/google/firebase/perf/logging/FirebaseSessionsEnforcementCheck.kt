/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.perf.logging

import com.google.firebase.perf.session.PerfSession
import com.google.firebase.perf.session.isLegacy
import com.google.firebase.perf.v1.PerfSession as ProtoPerfSession

class FirebaseSessionsEnforcementCheck {
  companion object {
    /** When enabled, failed preconditions will cause assertion errors for debugging. */
    @JvmStatic var enforcement: Boolean = false
    private var logger: AndroidLogger = AndroidLogger.getInstance()

    @JvmStatic
    fun checkSession(sessions: List<ProtoPerfSession>, failureMessage: String) {
      sessions.forEach { checkSession(it.sessionId, failureMessage) }
    }

    @JvmStatic
    fun checkSession(session: PerfSession, failureMessage: String) {
      checkSession(session.sessionId(), failureMessage)
    }

    @JvmStatic
    fun checkSession(sessionId: String, failureMessage: String) {
      if (sessionId.isLegacy()) {
        logger.debug("legacy session ${sessionId}: $failureMessage")
        assert(!enforcement) { failureMessage }
      }
    }
  }
}
