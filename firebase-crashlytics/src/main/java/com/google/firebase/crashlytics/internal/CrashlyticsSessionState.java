// Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.internal;

import androidx.annotation.NonNull;

/**
 * Crashlytics session state shared with Crashlytics Telemetry, read once when Telemetry starts.
 *
 * @hide
 */
public final class CrashlyticsSessionState {
  @NonNull private final String sessionId;

  public CrashlyticsSessionState(@NonNull String sessionId) {
    this.sessionId = sessionId;
  }

  /**
   * Returns the ID of the Crashlytics session opened when this process started.
   *
   * <p>This value never changes, even when Crashlytics opens a new session later in the same
   * process, such as after an on-demand fatal exception.
   */
  @NonNull
  public String getSessionId() {
    return sessionId;
  }
}
