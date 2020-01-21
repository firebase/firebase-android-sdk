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

package com.google.firebase.crashlytics.ndk;

import androidx.annotation.NonNull;

/**
 * Controller interface for the Crashlytics NDK kit. Handles retrieval and management of NDK crash
 * data.
 */
interface NativeComponentController {

  /**
   * Initialize NDK crash handling.
   *
   * @return true if native crash handling was initialized successfully.
   */
  boolean initialize(String sessionId);

  boolean hasCrashDataForSession(String sessionId);

  boolean finalizeSession(String sessionId);

  @NonNull
  SessionFiles getFilesForSession(String sessionId);

  void writeBeginSession(String sessionId, String generator, long startedAtSeconds);

  void writeSessionApp(
      String sessionId,
      String appIdentifier,
      String versionCode,
      String versionName,
      String installUuid,
      int deliveryMechanism,
      String unityVersion);

  void writeSessionOs(String sessionId, String osRelease, String osCodeName, boolean isRooted);

  void writeSessionDevice(
      String sessionId,
      int arch,
      String model,
      int availableProcessors,
      long totalRam,
      long diskSpace,
      boolean isEmulator,
      int state,
      String manufacturer,
      String modelClass);
}
