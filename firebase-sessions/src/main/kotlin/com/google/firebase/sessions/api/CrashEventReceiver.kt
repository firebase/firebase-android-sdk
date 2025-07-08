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

package com.google.firebase.sessions.api

/**
 * Internal API used by Firebase Crashlytics to notify the Firebase Sessions SDK of fatal crashes.
 *
 * This object provides a static-like entry point that Crashlytics calls to inform Sessions a fatal
 * crash has occurred.
 */
object CrashEventReceiver {
  /**
   * Notifies the Firebase Sessions SDK that a fatal crash has occurred.
   *
   * This method should be called by Firebase Crashlytics as soon as it detects a fatal crash. It
   * safely processes the crash notification, treating the crash as a background event, to ensure
   * that the session state is updated correctly.
   *
   * @see SharedSessionRepository.appBackground
   */
  @JvmStatic
  fun notifyCrashOccurred() {
    // TODO(mrober): Implement in #7039
  }
}
