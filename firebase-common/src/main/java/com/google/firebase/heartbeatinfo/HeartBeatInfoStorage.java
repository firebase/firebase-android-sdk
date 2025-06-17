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
import android.os.Build;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import com.google.firebase.datastorage.JavaDataStorage;
import com.google.firebase.datastorage.JavaDataStorageKt;
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

  private static final Preferences.Key<Long> GLOBAL = PreferencesKeys.longKey("fire-global");

  private static final String PREFERENCES_NAME = "FirebaseAppHeartBeat";

  private static final String HEARTBEAT_PREFERENCES_NAME = "FirebaseHeartBeat";

  private static final Preferences.Key<Long> HEART_BEAT_COUNT_TAG =
      PreferencesKeys.longKey("fire-count");

  private static final Preferences.Key<String> LAST_STORED_DATE =
      PreferencesKeys.stringKey("last-used-date");

  // As soon as you hit the limit of heartbeats. The number of stored heartbeats is halved.
  private static final int HEART_BEAT_COUNT_LIMIT = 30;

  private final JavaDataStorage firebaseDataStore;

  public HeartBeatInfoStorage(Context applicationContext, String persistenceKey) {
    this.firebaseDataStore =
        new JavaDataStorage(applicationContext, HEARTBEAT_PREFERENCES_NAME + persistenceKey);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  HeartBeatInfoStorage(JavaDataStorage javaDataStorage) {
    this.firebaseDataStore = javaDataStorage;
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  int getHeartBeatCount() {
    return this.firebaseDataStore.getSync(HEART_BEAT_COUNT_TAG, 0L).intValue();
  }

  synchronized void deleteAllHeartBeats() {
    firebaseDataStore.editSync(
        (pref) -> {
          long counter = 0;
          for (Map.Entry<Preferences.Key<?>, Object> entry : pref.asMap().entrySet()) {
            if (entry.getValue() instanceof Set) {
              // All other heartbeats other than the heartbeats stored today will be deleted.
              Preferences.Key<Set<String>> key = (Preferences.Key<Set<String>>) entry.getKey();
              Set<String> dates = (Set<String>) entry.getValue();
              String today = getFormattedDate(System.currentTimeMillis());

              if (dates.contains(today)) {
                pref.set(key, Set.of(today));
                counter += 1;
              } else {
                pref.remove(key);
              }
            }
          }
          if (counter == 0) {
            pref.remove(HEART_BEAT_COUNT_TAG);
          } else {
            pref.set(HEART_BEAT_COUNT_TAG, counter);
          }

          return null;
        });
  }

  synchronized List<HeartBeatResult> getAllHeartBeats() {
    ArrayList<HeartBeatResult> heartBeatResults = new ArrayList<>();
    String today = getFormattedDate(System.currentTimeMillis());

    for (Map.Entry<Preferences.Key<?>, Object> entry :
        this.firebaseDataStore.getAllSync().entrySet()) {
      if (entry.getValue() instanceof Set) {
        Set<String> dates = new HashSet<>((Set<String>) entry.getValue());
        dates.remove(today);
        if (!dates.isEmpty()) {
          heartBeatResults.add(
              HeartBeatResult.create(entry.getKey().getName(), new ArrayList<>(dates)));
        }
      }
    }

    updateGlobalHeartBeat(System.currentTimeMillis());

    return heartBeatResults;
  }

  private synchronized Preferences.Key<Set<String>> getStoredUserAgentString(
      MutablePreferences preferences, String dateString) {
    for (Map.Entry<Preferences.Key<?>, Object> entry : preferences.asMap().entrySet()) {
      if (entry.getValue() instanceof Set) {
        Set<String> dateSet = (Set<String>) entry.getValue();
        for (String date : dateSet) {
          if (dateString.equals(date)) {
            return PreferencesKeys.stringSetKey(entry.getKey().getName());
          }
        }
      }
    }
    return null;
  }

  private synchronized void updateStoredUserAgent(
      MutablePreferences preferences, Preferences.Key<Set<String>> userAgent, String dateString) {
    removeStoredDate(preferences, dateString);
    Set<String> userAgentDateSet =
        new HashSet<>(JavaDataStorageKt.getOrDefault(preferences, userAgent, new HashSet<>()));
    userAgentDateSet.add(dateString);
    preferences.set(userAgent, userAgentDateSet);
  }

  private synchronized void removeStoredDate(MutablePreferences preferences, String dateString) {
    // Find stored heartbeat and clear it.
    Preferences.Key<Set<String>> userAgent = getStoredUserAgentString(preferences, dateString);
    if (userAgent == null) {
      return;
    }
    Set<String> userAgentDateSet =
        new HashSet<>(JavaDataStorageKt.getOrDefault(preferences, userAgent, new HashSet<>()));
    userAgentDateSet.remove(dateString);
    if (userAgentDateSet.isEmpty()) {
      preferences.remove(userAgent);
    } else {
      preferences.set(userAgent, userAgentDateSet);
    }
  }

  synchronized void postHeartBeatCleanUp() {
    String dateString = getFormattedDate(System.currentTimeMillis());

    firebaseDataStore.editSync(
        (pref) -> {
          pref.set(LAST_STORED_DATE, dateString);
          removeStoredDate(pref, dateString);
          return null;
        });
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
    Preferences.Key<Set<String>> userAgent = PreferencesKeys.stringSetKey(userAgentString);
    firebaseDataStore.editSync(
        (pref) -> {
          String lastDateString = JavaDataStorageKt.getOrDefault(pref, LAST_STORED_DATE, "");
          if (lastDateString.equals(dateString)) {
            Preferences.Key<Set<String>> storedUserAgent =
                getStoredUserAgentString(pref, dateString);
            if (storedUserAgent == null) {
              // Heartbeat already sent for today.
              return null;
            } else if (storedUserAgent.getName().equals(userAgentString)) {
              // UserAgent not updated.
              return null;
            } else {
              updateStoredUserAgent(pref, userAgent, dateString);
              return null;
            }
          }
          long heartBeatCount = JavaDataStorageKt.getOrDefault(pref, HEART_BEAT_COUNT_TAG, 0L);
          if (heartBeatCount + 1 == HEART_BEAT_COUNT_LIMIT) {
            heartBeatCount = cleanUpStoredHeartBeats(pref);
          }
          Set<String> userAgentDateSet =
              new HashSet<>(JavaDataStorageKt.getOrDefault(pref, userAgent, new HashSet<>()));
          userAgentDateSet.add(dateString);
          heartBeatCount += 1;

          pref.set(userAgent, userAgentDateSet);
          pref.set(HEART_BEAT_COUNT_TAG, heartBeatCount);
          pref.set(LAST_STORED_DATE, dateString);

          return null;
        });
  }

  private synchronized long cleanUpStoredHeartBeats(MutablePreferences preferences) {
    long heartBeatCount = JavaDataStorageKt.getOrDefault(preferences, HEART_BEAT_COUNT_TAG, 0L);

    String lowestDate = null;
    String userAgentString = "";
    Set<String> userAgentDateSet = new HashSet<>();
    for (Map.Entry<Preferences.Key<?>, Object> entry : preferences.asMap().entrySet()) {
      if (entry.getValue() instanceof Set) {
        Set<String> dateSet = (Set<String>) entry.getValue();
        for (String date : dateSet) {
          if (lowestDate == null || lowestDate.compareTo(date) > 0) {
            userAgentDateSet = dateSet;
            lowestDate = date;
            userAgentString = entry.getKey().getName();
          }
        }
      }
    }
    userAgentDateSet = new HashSet<>(userAgentDateSet);
    userAgentDateSet.remove(lowestDate);
    preferences.set(PreferencesKeys.stringSetKey(userAgentString), userAgentDateSet);
    preferences.set(HEART_BEAT_COUNT_TAG, heartBeatCount - 1);

    return heartBeatCount - 1;
  }

  synchronized long getLastGlobalHeartBeat() {
    return firebaseDataStore.getSync(GLOBAL, -1L);
  }

  synchronized void updateGlobalHeartBeat(long millis) {
    firebaseDataStore.editSync(
        (pref) -> {
          pref.set(GLOBAL, millis);
          return null;
        });
  }

  synchronized boolean isSameDateUtc(long base, long target) {
    return getFormattedDate(base).equals(getFormattedDate(target));
  }

  /*
   Indicates whether or not we have to send a sdk heartbeat.
   A sdk heartbeat is sent either when there is no heartbeat sent ever for the sdk or
   when the last heartbeat send for the sdk was later than a day before.
  */
  synchronized boolean shouldSendSdkHeartBeat(Preferences.Key<Long> heartBeatTag, long millis) {
    if (this.isSameDateUtc(firebaseDataStore.getSync(heartBeatTag, -1L), millis)) {
      return false;
    } else {
      firebaseDataStore.putSync(heartBeatTag, millis);
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
