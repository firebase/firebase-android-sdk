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

class DebugEnforcementCheck {
  companion object {
    /** When enabled, failed preconditions will cause assertion errors for debugging. */
    @JvmStatic var enforcement: Boolean = false
    private var logger: AndroidLogger = AndroidLogger.getInstance()

    public fun checkSession(isAqsAvailable: Boolean, failureMessage: String) {
      if (!isAqsAvailable) {
        Companion.logger.debug(failureMessage)
        assert(!enforcement) { failureMessage }
      }
    }
  }
}
