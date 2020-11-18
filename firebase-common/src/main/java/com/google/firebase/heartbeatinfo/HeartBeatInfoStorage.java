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
import java.text.SimpleDateFormat;
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

  private static final String PREFERENCES_NAME = "FirebaseAppHeartBeat";

  private static final String HEART_BEAT_COUNT_TAG = "fire-count";

  // As soon as you hit the limit of heartbeats. The number of stored heartbeats is halved.
  private static final int HEART_BEAT_COUNT_LIMIT = 200;

  private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("dd/MM/yyyy z");

  // Stores a key value mapping from timestamp to the sdkName and heartBeat code.
  private static final String STORAGE_PREFERENCES_NAME = "FirebaseAppHeartBeatStorage";

  private final SharedPreferences sharedPreferences;
  private final SharedPreferences heartBeatSharedPreferences;

  private HeartBeatInfoStorage(Context applicationContext) {
    this.sharedPreferences =
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    this.heartBeatSharedPreferences =
        applicationContext.getSharedPreferences(STORAGE_PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  HeartBeatInfoStorage(
      SharedPreferences preferences, SharedPreferences heartBeatSharedPreferences) {
    this.sharedPreferences = preferences;
    this.heartBeatSharedPreferences = heartBeatSharedPreferences;
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  int getHeartBeatCount() {
    return (int) this.sharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
  }

  static synchronized HeartBeatInfoStorage getInstance(Context applicationContext) {
    if (instance == null) {
      instance = new HeartBeatInfoStorage(applicationContext);
    }
    return instance;
  }

  synchronized void storeHeartBeatInformation(String heartBeatTag, long millis) {
    long heartBeatCount = this.sharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    this.heartBeatSharedPreferences.edit().putString(String.valueOf(millis), heartBeatTag).apply();
    this.sharedPreferences.edit().putLong(HEART_BEAT_COUNT_TAG, heartBeatCount + 1).apply();
    heartBeatCount += 1;
    if (heartBeatCount > HEART_BEAT_COUNT_LIMIT) {
      this.cleanUpStoredHeartBeats();
    }
  }

  private synchronized void cleanUpStoredHeartBeats() {
    long heartBeatCount = this.sharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    ArrayList<Long> timestampList = new ArrayList<>();
    for (Map.Entry<String, ?> entry : heartBeatSharedPreferences.getAll().entrySet()) {
      timestampList.add(Long.parseLong(entry.getKey()));
    }
    Collections.sort(timestampList);
    for (Long millis : timestampList) {
      this.heartBeatSharedPreferences.edit().remove(String.valueOf(millis)).apply();
      this.sharedPreferences.edit().putLong(HEART_BEAT_COUNT_TAG, heartBeatCount - 1).apply();
      heartBeatCount -= 1;
      if (heartBeatCount <= (HEART_BEAT_COUNT_LIMIT / 2)) return;
    }
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

  synchronized void clearStoredHeartBeats() {
    heartBeatSharedPreferences.edit().clear().apply();
    sharedPreferences.edit().remove(HEART_BEAT_COUNT_TAG).apply();
  }

  static boolean isSameDateUtc(long base, long target) {
    Date baseDate = new Date(base);
    Date targetDate = new Date(target);
    return !(FORMATTER.format(baseDate).equals(FORMATTER.format(targetDate)));
  }

  /*
   Indicates whether or not we have to send a sdk heartbeat.
   A sdk heartbeat is sent either when there is no heartbeat sent ever for the sdk or
   when the last heartbeat send for the sdk was later than a day before.
  */
  synchronized boolean shouldSendSdkHeartBeat(String heartBeatTag, long millis) {
    if (sharedPreferences.contains(heartBeatTag)) {
      if (isSameDateUtc(sharedPreferences.getLong(heartBeatTag, -1), millis)) {
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
