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
import com.google.android.gms.tasks.Task;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsConnectorBridge.BreadcrumbHandler;

/** Receiver for Firebase Analytics events reported to Crashlytics */
// TODO: Change to AnaltyicsBridge or something
public interface AnalyticsBridge {
  //
  // /**
  //  * Listener for Firebase app exception events that originated from Crashlytics. This functions as
  //  * a callback so we can confirm that Crashlytics-originating events have been received by the
  //  * Firebase Analytics SDK.
  //  */
  // interface CrashlyticsAppExceptionEventListener {
  //   void onCrashlyticsAppExceptionEvent();
  // }

  /**
   * Register analytics receiver with its associated event source.
   */
  // boolean register();
  //
  // /** Unregister breadcrumb receiver with its associated event source. */
  // void unregister();
  void registerBreadcrumbHandler(BreadcrumbHandler breadcrumbHandler);

  void recordFatalFirebaseEvent(long timestamp);

  Task<Void> getAnalyticsTaskChain();
}
