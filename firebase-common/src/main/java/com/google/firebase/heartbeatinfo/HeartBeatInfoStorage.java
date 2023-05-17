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
import android.os.Build;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Class responsible for storing all heartbeat related information.
 *
 * <p>This exposes functions to store heartbeats and retrieve them in the form of HeartBeatResult.
 */
class HeartBeatInfoStorage {
  private static HeartBeatInfoStorage instance = null;

  private static final String GLOBAL = "fire-global";

  private static final String PREFERENCES_NAME = "FirebaseAppHeartBeat";

  private static final String HEARTBEAT_PREFERENCES_NAME = "FirebaseHeartBeat";

  private static final String HEART_BEAT_COUNT_TAG = "fire-count";

  private static final String LAST_STORED_DATE = "last-used-date";

  // As soon as you hit the limit of heartbeats. The number of stored heartbeats is halved.
  private static final int HEART_BEAT_COUNT_LIMIT = 30;

  private final SharedPreferences firebaseSharedPreferences;

  public HeartBeatInfoStorage(Context applicationContext, String persistenceKey) {
    this.firebaseSharedPreferences =
        applicationContext.getSharedPreferences(
            HEARTBEAT_PREFERENCES_NAME + persistenceKey, Context.MODE_PRIVATE);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  HeartBeatInfoStorage(SharedPreferences firebaseSharedPreferences) {
    this.firebaseSharedPreferences = firebaseSharedPreferences;
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  int getHeartBeatCount() {
    return (int) this.firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
  }

  synchronized void deleteAllHeartBeats() {
    SharedPreferences.Editor editor = firebaseSharedPreferences.edit();
    int counter = 0;
    for (Map.Entry<String, ?> entry : this.firebaseSharedPreferences.getAll().entrySet()) {
      if (entry.getValue() instanceof Set) {
        // All other heartbeats other than the heartbeats stored today will be deleted.
        Set<String> dates = (Set<String>) entry.getValue();
        String today = getFormattedDate(System.currentTimeMillis());
        String key = entry.getKey();
        if (dates.contains(today)) {
          Set<String> userAgentDateSet = new HashSet<>();
          userAgentDateSet.add(today);
          counter += 1;
          editor.putStringSet(key, userAgentDateSet);
        } else {
          editor.remove(key);
        }
      }
    }
    if (counter == 0) {
      editor.remove(HEART_BEAT_COUNT_TAG);
    } else {
      editor.putLong(HEART_BEAT_COUNT_TAG, counter);
    }

    editor.commit();
  }

  synchronized List<HeartBeatResult> getAllHeartBeats() {
    ArrayList<HeartBeatResult> heartBeatResults = new ArrayList<>();
    for (Map.Entry<String, ?> entry : this.firebaseSharedPreferences.getAll().entrySet()) {
      if (entry.getValue() instanceof Set) {
        Set<String> dates = new HashSet<>((Set<String>) entry.getValue());
        String today = getFormattedDate(System.currentTimeMillis());
        dates.remove(today);
        if (!dates.isEmpty()) {
          heartBeatResults.add(
              HeartBeatResult.create(entry.getKey(), new ArrayList<String>(dates)));
        }
      }
    }
    updateGlobalHeartBeat(System.currentTimeMillis());
    return heartBeatResults;
  }

  private synchronized String getStoredUserAgentString(String dateString) {
    for (Map.Entry<String, ?> entry : firebaseSharedPreferences.getAll().entrySet()) {
      if (entry.getValue() instanceof Set) {
        Set<String> dateSet = (Set<String>) entry.getValue();
        for (String date : dateSet) {
          if (dateString.equals(date)) {
            return entry.getKey();
          }
        }
      }
    }
    return null;
  }

  private synchronized void updateStoredUserAgent(String userAgent, String dateString) {
    removeStoredDate(dateString);
    Set<String> userAgentDateSet =
        new HashSet<String>(
            firebaseSharedPreferences.getStringSet(userAgent, new HashSet<String>()));
    userAgentDateSet.add(dateString);
    firebaseSharedPreferences.edit().putStringSet(userAgent, userAgentDateSet).commit();
  }

  private synchronized void removeStoredDate(String dateString) {
    // Find stored heartbeat and clear it.
    String userAgentString = getStoredUserAgentString(dateString);
    if (userAgentString == null) {
      return;
    }
    Set<String> userAgentDateSet =
        new HashSet<String>(
            firebaseSharedPreferences.getStringSet(userAgentString, new HashSet<String>()));
    userAgentDateSet.remove(dateString);
    if (userAgentDateSet.isEmpty()) {
      firebaseSharedPreferences.edit().remove(userAgentString).commit();
    } else {
      firebaseSharedPreferences.edit().putStringSet(userAgentString, userAgentDateSet).commit();
    }
  }

  synchronized void postHeartBeatCleanUp() {
    String dateString = getFormattedDate(System.currentTimeMillis());
    firebaseSharedPreferences.edit().putString(LAST_STORED_DATE, dateString).commit();
    removeStoredDate(dateString);
  }

  private synchronized String getFormattedDate(long millis) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Instant instant = new Date(millis).toInstant();
      LocalDateTime ldt = instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
      return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE);
    } else {
      return new SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(new Date(millis));
    }
  }

  synchronized void storeHeartBeat(long millis, String userAgentString) {
    String dateString = getFormattedDate(millis);
    String lastDateString = firebaseSharedPreferences.getString(LAST_STORED_DATE, "");
    if (lastDateString.equals(dateString)) {
      String storedUserAgentString = getStoredUserAgentString(dateString);
      if (storedUserAgentString == null) {
        // Heartbeat already sent for today.
        return;
      }
      if (storedUserAgentString.equals(userAgentString)) {
        // UserAgent not updated.
        return;
      } else {
        updateStoredUserAgent(userAgentString, dateString);
        return;
      }
    }
    long heartBeatCount = firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    if (heartBeatCount + 1 == HEART_BEAT_COUNT_LIMIT) {
      cleanUpStoredHeartBeats();
      heartBeatCount = firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    }
    Set<String> userAgentDateSet =
        new HashSet<String>(
            firebaseSharedPreferences.getStringSet(userAgentString, new HashSet<String>()));
    userAgentDateSet.add(dateString);
    heartBeatCount += 1;
    firebaseSharedPreferences
        .edit()
        .putStringSet(userAgentString, userAgentDateSet)
        .putLong(HEART_BEAT_COUNT_TAG, heartBeatCount)
        .putString(LAST_STORED_DATE, dateString)
        .commit();
  }

  private synchronized void cleanUpStoredHeartBeats() {
    long heartBeatCount = firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    String lowestDate = null;
    String userAgentString = "";
    for (Map.Entry<String, ?> entry : firebaseSharedPreferences.getAll().entrySet()) {
      if (entry.getValue() instanceof Set) {
        Set<String> dateSet = (Set<String>) entry.getValue();
        for (String date : dateSet) {
          if (lowestDate == null || lowestDate.compareTo(date) > 0) {
            lowestDate = date;
            userAgentString = entry.getKey();
          }
        }
      }
    }
    Set<String> userAgentDateSet =
        new HashSet<String>(
            firebaseSharedPreferences.getStringSet(userAgentString, new HashSet<String>()));
    userAgentDateSet.remove(lowestDate);
    firebaseSharedPreferences
        .edit()
        .putStringSet(userAgentString, userAgentDateSet)
        .putLong(HEART_BEAT_COUNT_TAG, heartBeatCount - 1)
        .commit();
  }

  synchronized long getLastGlobalHeartBeat() {
    return firebaseSharedPreferences.getLong(GLOBAL, -1);
  }

  synchronized void updateGlobalHeartBeat(long millis) {
    firebaseSharedPreferences.edit().putLong(GLOBAL, millis).commit();
  }

  synchronized boolean isSameDateUtc(long base, long target) {
    return getFormattedDate(base).equals(getFormattedDate(target));
  }

  /*
   Indicates whether or not we have to send a sdk heartbeat.
   A sdk heartbeat is sent either when there is no heartbeat sent ever for the sdk or
   when the last heartbeat send for the sdk was later than a day before.
  */
  synchronized boolean shouldSendSdkHeartBeat(String heartBeatTag, long millis) {
    if (firebaseSharedPreferences.contains(heartBeatTag)) {
      if (!this.isSameDateUtc(firebaseSharedPreferences.getLong(heartBeatTag, -1), millis)) {
        firebaseSharedPreferences.edit().putLong(heartBeatTag, millis).commit();
        return true;
      }
      return false;
    } else {
      firebaseSharedPreferences.edit().putLong(heartBeatTag, millis).commit();
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
