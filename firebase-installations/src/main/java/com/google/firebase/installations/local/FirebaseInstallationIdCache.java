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

package com.google.firebase.installations.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;

/**
 * A layer that locally caches a few Firebase Installation attributes on top the Firebase
 * Installation backend API.
 */
public class FirebaseInstallationIdCache {
  // Status of each cache entry
  // NOTE: never change the ordinal of the enum values because the enum values are stored in cache
  // as their ordinal numbers.
  public enum CacheStatus {
    // Cache entry is synced to Firebase backend
    REGISTERED,
    // Cache entry is waiting for Firebase backend response or internal network retry
    UNREGISTERED,
    // Cache entry is in error state when syncing with Firebase backend
    REGISTER_ERROR
  }

  private static final String SHARED_PREFS_NAME = "FirebaseInstallationIdCache";

  private static final String FIREBASE_INSTALLATION_ID_KEY = "Fid";
  private static final String CACHE_STATUS_KEY = "Status";

  private final SharedPreferences prefs;
  private final String persistenceKey;

  public FirebaseInstallationIdCache(@NonNull FirebaseApp firebaseApp) {
    // Different FirebaseApp in the same Android application should have the same application
    // context and same dir path
    prefs =
        firebaseApp
            .getApplicationContext()
            .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    persistenceKey = firebaseApp.getPersistenceKey();
  }

  @Nullable
  public synchronized FirebaseInstallationIdCacheEntryValue readCacheEntryValue() {
    String iid = prefs.getString(getSharedPreferencesKey(FIREBASE_INSTALLATION_ID_KEY), null);
    int status = prefs.getInt(getSharedPreferencesKey(CACHE_STATUS_KEY), -1);

    if (iid == null || status == -1) {
      return null;
    }

    return FirebaseInstallationIdCacheEntryValue.create(iid, CacheStatus.values()[status]);
  }

  @NonNull
  public synchronized boolean insertOrUpdateCacheEntry(
      @NonNull FirebaseInstallationIdCacheEntryValue entryValue) {
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(
        getSharedPreferencesKey(FIREBASE_INSTALLATION_ID_KEY),
        entryValue.getFirebaseInstallationId());
    editor.putInt(getSharedPreferencesKey(CACHE_STATUS_KEY), entryValue.getCacheStatus().ordinal());
    return editor.commit();
  }

  @NonNull
  public synchronized boolean clear() {
    SharedPreferences.Editor editor = prefs.edit();
    editor.remove(getSharedPreferencesKey(FIREBASE_INSTALLATION_ID_KEY));
    editor.remove(getSharedPreferencesKey(CACHE_STATUS_KEY));
    return editor.commit();
  }

  private String getSharedPreferencesKey(String key) {
    return String.format("%s|%s", persistenceKey, key);
  }
}
