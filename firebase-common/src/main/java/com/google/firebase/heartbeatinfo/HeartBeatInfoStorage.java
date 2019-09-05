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

/**
 * Class responsible for storing all heartbeat related information.
 *
 * <p>This exposes functions to check if there is a need to send global/sdk heartbeat.
 */
class HeartBeatInfoStorage {
  private static HeartBeatInfoStorage instance = null;
  private static final String GLOBAL = "fire-global";

  private static final String preferencesName = "FirebaseAppHeartBeat";

  private final SharedPreferences sharedPreferences;

  private HeartBeatInfoStorage(Context applicationContext) {
    this.sharedPreferences =
        applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  HeartBeatInfoStorage(SharedPreferences preferences) {
    this.sharedPreferences = preferences;
  }

  static synchronized HeartBeatInfoStorage getInstance(Context applicationContext) {
    if (instance == null) {
      instance = new HeartBeatInfoStorage(applicationContext);
    }
    return instance;
  }

  /*
   Indicates whether or not we have to send a sdk heartbeat.
   A sdk heartbeat is sent either when there is no heartbeat sent ever for the sdk or
   when the last heartbeat send for the sdk was later than a day before.
  */
  synchronized boolean shouldSendSdkHeartBeat(String heartBeatTag, long millis) {
    if (sharedPreferences.contains(heartBeatTag)) {
      long timeElapsed = millis - sharedPreferences.getLong(heartBeatTag, -1);
      if (timeElapsed >= (long) 1000 * 60 * 60 * 24) {
        sharedPreferences.edit().putLong(heartBeatTag, millis).apply();
        return true;
      }
      return false;
    } else {
      sharedPreferences.edit().putLong(heartBeatTag, millis).apply();
      return true;
    }
  }

  /*
   Indicates whether or not we have to send a global heartbeat.
   A global heartbeat is set only once per day.
  */
  synchronized boolean shouldSendGlobalHeartBeat(long millis) {
    return shouldSendSdkHeartBeat(GLOBAL, millis);
  }
}
