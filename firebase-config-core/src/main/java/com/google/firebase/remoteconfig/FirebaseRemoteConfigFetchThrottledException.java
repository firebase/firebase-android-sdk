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

/** An exception thrown when a {@link FirebaseRemoteConfig#fetch()} call is throttled. */
public class FirebaseRemoteConfigFetchThrottledException extends FirebaseRemoteConfigException {
  private final long throttleEndTimeMillis;

  /**
   * Creates a throttled exception with the earliest time that a fetch call might be made without
   * being throttled.
   *
   * @param throttleEndTimeMillis the time, in millis since epoch, until which all fetch calls will
   *     be throttled.
   */
  public FirebaseRemoteConfigFetchThrottledException(long throttleEndTimeMillis) {
    this("Fetch was throttled.", throttleEndTimeMillis);
  }

  /**
   * Creates a throttled exception with the given message and the earliest time that a fetch call
   * might be made without being throttled.
   *
   * @param message The error message to display to callers.
   * @param throttledEndTimeInMillis the time, in millis since epoch, until which all fetch calls
   *     will be throttled.
   * @hide
   */
  public FirebaseRemoteConfigFetchThrottledException(
      String message, long throttledEndTimeInMillis) {
    super(message);
    throttleEndTimeMillis = throttledEndTimeInMillis;
  }

  /** Returns the time, in millis since epoch, until which all fetch calls will be throttled. */
  public long getThrottleEndTimeMillis() {
    return throttleEndTimeMillis;
  }
}
