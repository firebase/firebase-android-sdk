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

package com.google.firebase.heartbeatinfo;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Class responsible for storing all heartbeat related information.
 *
 * <p>This exposes functions to check if there is a need to send global/sdk heartbeat.
 */
class HeartBeatInfoStorage {
  private static HeartBeatInfoStorage instance = null;
  private static final String GLOBAL = "fire-global";

  private static final String preferencesName = "FirebaseAppHeartBeat";

  // Stores a key value mapping from timestamp to the sdkName and heartBeat code.
  private static final String storagePreferencesName = "FirebaseAppHeartBeatStorage";

  private final SharedPreferences sharedPreferences;
  private final SharedPreferences heartBeatSharedPreferences;

  private HeartBeatInfoStorage(Context applicationContext) {
    this.sharedPreferences =
        applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
    this.heartBeatSharedPreferences =
        applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  HeartBeatInfoStorage(
      SharedPreferences preferences, SharedPreferences heartBeatSharedPreferences) {
    this.sharedPreferences = preferences;
    this.heartBeatSharedPreferences = heartBeatSharedPreferences;
  }

  static synchronized HeartBeatInfoStorage getInstance(Context applicationContext) {
    if (instance == null) {
      instance = new HeartBeatInfoStorage(applicationContext);
    }
    return instance;
  }

  synchronized void storeHeartBeatInformation(String heartBeatTag, long millis) {
    this.heartBeatSharedPreferences.edit().putString(String.valueOf(millis), heartBeatTag).apply();
  }

  synchronized long getLastGlobalHeartBeat() {
    return sharedPreferences.getLong(GLOBAL, -1);
  }

  synchronized void updateGlobalHeartBeat(long millis) {
    sharedPreferences.edit().putLong(GLOBAL, millis).apply();
  }

  synchronized List<SdkHeartBeatResult> getStoredHeartBeats(boolean shouldClear) {
    ArrayList<SdkHeartBeatResult> sdkHeartBeatResults = new ArrayList<>();
    for (Map.Entry<String, ?> entry : heartBeatSharedPreferences.getAll().entrySet()) {
      long millis = Long.parseLong(entry.getKey());
      String sdkName = ((String) entry.getValue());
      sdkHeartBeatResults.add(SdkHeartBeatResult.create(sdkName, millis));
    }
    Collections.sort(sdkHeartBeatResults);
    if (shouldClear) clearStoredHeartBeats();
    return sdkHeartBeatResults;
  }

  void clearStoredHeartBeats() {
    heartBeatSharedPreferences.edit().clear().apply();
  }

  boolean isValidHeartBeat(long base, long target) {
    return !((new Date(base).getDate()) == (new Date(target).getDate()));
  }

  /*
   Indicates whether or not we have to send a sdk heartbeat.
   A sdk heartbeat is sent either when there is no heartbeat sent ever for the sdk or
   when the last heartbeat send for the sdk was later than a day before.
  */
  synchronized boolean shouldSendSdkHeartBeat(String heartBeatTag, long millis, boolean update) {
    if (sharedPreferences.contains(heartBeatTag)) {
      if (isValidHeartBeat(sharedPreferences.getLong(heartBeatTag, -1), millis)) {
        if (update) {
          sharedPreferences.edit().putLong(heartBeatTag, millis).apply();
        }
        return true;
      }
      return false;
    } else {
      if (update) {
        sharedPreferences.edit().putLong(heartBeatTag, millis).apply();
      }
      return true;
    }
  }

  /*
   Indicates whether or not we have to send a global heartbeat.
   A global heartbeat is set only once per day.
  */
  synchronized boolean shouldSendGlobalHeartBeat(long millis, boolean update) {
    return shouldSendSdkHeartBeat(GLOBAL, millis, update);
  }
}
