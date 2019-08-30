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

package com.google.firebase.internal;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.heartbeatinfo.HeartBeatInfo;

public class HeartBeatInfoStorage {
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
  public HeartBeatInfoStorage(SharedPreferences preferences) {
    this.sharedPreferences = preferences;
  }

  public static void initialize(Context applicationContext) {
    if (instance == null) {
      instance = new HeartBeatInfoStorage(applicationContext);
    }
    // send warning
  }

  public static HeartBeatInfoStorage getInstance() {
    if (instance == null) {
      throw new IllegalStateException("Not initialized!");
    }
    return instance;
  }

  public boolean shouldSendSdkHeartBeat(String sdkName, long millis) {
    if (sharedPreferences.contains(sdkName)) {
      long timeElapsed = millis - sharedPreferences.getLong(sdkName, -1);
      if (timeElapsed >= (long) 1000 * 60 * 60 * 24) {
        sharedPreferences.edit().putLong(sdkName, millis).apply();
        return true;
      }
      return false;
    } else {
      sharedPreferences.edit().putLong(sdkName, millis).apply();
      return true;
    }
  }

  public boolean shouldSendGlobalHeartBeat(long millis) {
    return shouldSendSdkHeartBeat(GLOBAL, millis);
  }
}
