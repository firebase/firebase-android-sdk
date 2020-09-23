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

import androidx.annotation.NonNull;

/** This class defines Crashlytics lifecycle events */
interface CrashlyticsLifecycleEvents {

  /**
   * Called when a new session is opened.
   *
   * @param sessionId the identifier for the new session
   */
  void onBeginSession(@NonNull String sessionId, long timestamp);

  /**
   * Called when a message is logged by the user.
   *
   * @param timestamp the timestamp of the message (in milliseconds since app launch)
   * @param log the log message
   */
  void onLog(long timestamp, String log);

  /**
   * Called when a custom key is set by the user.
   *
   * @param key
   * @param value
   */
  void onCustomKey(String key, String value);

  /**
   * Called when a user ID is set by the user.
   *
   * @param userId
   */
  void onUserId(String userId);
}
