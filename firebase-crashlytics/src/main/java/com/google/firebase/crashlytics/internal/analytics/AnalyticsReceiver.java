// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.analytics;

import androidx.annotation.Nullable;

/** Receiver for Firebase Analytics events reported to Crashlytics */
public interface AnalyticsReceiver {

  /**
   * Listener for Firebase app exception events that originated from Crashlytics. This functions as
   * a callback so we can confirm that Crashlytics-originating events have been received by the
   * Firebase Analytics SDK.
   */
  interface CrashlyticsAppExceptionEventListener {
    void onCrashlyticsAppExceptionEvent();
  }

  /**
   * Register analytics receiver with its associated event source.
   *
   * @return true if successfully registered, false otherwise
   */
  boolean register();

  /** Unregister breadcrumb receiver with its associated event source. */
  void unregister();

  void setCrashlyticsAppExceptionEventListener(
      @Nullable CrashlyticsAppExceptionEventListener listener);
}
