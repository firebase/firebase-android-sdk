// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig;

import androidx.annotation.NonNull;

/** Wraps the current state of the FirebaseRemoteConfig singleton object. */
public interface FirebaseRemoteConfigInfo {
  /**
   * Gets the timestamp (milliseconds since epoch) of the last successful fetch, regardless of
   * whether the fetch was activated or not.
   *
   * @return -1 if no fetch attempt has been made yet. Otherwise, returns the timestamp of the last
   *     successful fetch operation.
   */
  long getFetchTimeMillis();

  /**
   * Gets the status of the most recent fetch attempt.
   *
   * @return Will return one of {@link FirebaseRemoteConfig#LAST_FETCH_STATUS_SUCCESS}, {@link
   *     FirebaseRemoteConfig#LAST_FETCH_STATUS_FAILURE}, {@link
   *     FirebaseRemoteConfig#LAST_FETCH_STATUS_THROTTLED}, or {@link
   *     FirebaseRemoteConfig#LAST_FETCH_STATUS_NO_FETCH_YET}.
   */
  int getLastFetchStatus();

  /**
   * Gets the current settings of the FirebaseRemoteConfig singleton object.
   *
   * @return A {@link FirebaseRemoteConfigSettings} object indicating the current settings.
   */
  @NonNull
  FirebaseRemoteConfigSettings getConfigSettings();
}
