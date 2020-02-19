// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;

/** This class defines Crashlytics lifecycle events */
interface CrashlyticsLifecycleEvents {

  /**
   * Called when a new session is opened.
   *
   * @param sessionId the identifier for the new session
   */
  void onBeginSession(String sessionId);

  /**
   * Called when a fatal event occurs.
   *
   * @param event the fatal event
   * @param thread the thread on which the fatal event occurred
   */
  void onFatalEvent(Throwable event, Thread thread);

  /**
   * Called when a non-fatal event occurs.
   *
   * @param event the non-fatal event
   * @param thread the thread on which the non-fatal event occurred
   */
  void onNonFatalEvent(Throwable event, Thread thread);

  /** Called when the current session should be closed */
  void onEndSession();

  /**
   * Called before sending reports to clean up any still-open sessions, attach any associated events
   * to those sessions, and prepare finalized reports to be sent.
   */
  void onFinalizeSessions();

  /**
   * Called when Crashlytics can send reports, after app settings data is available.
   *
   * @param appSettingsData app settings data for augmenting the report, if necessary
   */
  void onSendReports(AppSettingsData appSettingsData);
}
