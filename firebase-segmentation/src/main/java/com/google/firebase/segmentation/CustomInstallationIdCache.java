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

package com.google.firebase.segmentation;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.android.gms.common.util.Strings;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class CustomInstallationIdCache {

  // Status of each cache entry
  // NOTE: never change the ordinal of the enum values because the enum values are stored in cache
  // as their ordinal numbers.
  enum CacheStatus {
    // Cache entry is synced to Firebase backend
    SYNCED,
    // Cache entry is waiting for Firebase backend response or pending internal retry for retryable
    // errors.
    PENDING,
    // Cache entry is not accepted by Firebase backend.
    ERROR,
  }

  private static final String SHARED_PREFS_NAME = "CustomInstallationIdCache";

  private static final String CUSTOM_INSTALLATION_ID_KEY = "Cid";
  private static final String INSTANCE_ID_KEY = "Iid";
  private static final String CACHE_STATUS_KEY = "Status";

  private static CustomInstallationIdCache singleton = null;
  private final Executor ioExecuter;
  private final SharedPreferences prefs;

  static synchronized CustomInstallationIdCache getInstance() {
    if (singleton == null) {
      singleton = new CustomInstallationIdCache();
    }
    return singleton;
  }

  private CustomInstallationIdCache() {
    // Since different FirebaseApp in the same Android application should have the same application
    // context and same dir path, so that use the context of the default FirebaseApp to create the
    // shared preferences.
    prefs =
        FirebaseApp.getInstance()
            .getApplicationContext()
            .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);

    ioExecuter = Executors.newFixedThreadPool(2);
  }

  @Nullable
  synchronized CustomInstallationIdCacheEntryValue readCacheEntryValue(FirebaseApp firebaseApp) {
    String cid =
        prefs.getString(getSharedPreferencesKey(firebaseApp, CUSTOM_INSTALLATION_ID_KEY), null);
    String iid = prefs.getString(getSharedPreferencesKey(firebaseApp, INSTANCE_ID_KEY), null);
    int status = prefs.getInt(getSharedPreferencesKey(firebaseApp, CACHE_STATUS_KEY), -1);

    if (Strings.isEmptyOrWhitespace(cid) || Strings.isEmptyOrWhitespace(iid) || status == -1) {
      return null;
    }

    return CustomInstallationIdCacheEntryValue.create(cid, iid, CacheStatus.values()[status]);
  }

  synchronized Task<Boolean> insertOrUpdateCacheEntry(
      FirebaseApp firebaseApp, CustomInstallationIdCacheEntryValue entryValue) {
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(
        getSharedPreferencesKey(firebaseApp, CUSTOM_INSTALLATION_ID_KEY),
        entryValue.getCustomInstallationId());
    editor.putString(
        getSharedPreferencesKey(firebaseApp, INSTANCE_ID_KEY), entryValue.getFirebaseInstanceId());
    editor.putInt(
        getSharedPreferencesKey(firebaseApp, CACHE_STATUS_KEY),
        entryValue.getCacheStatus().ordinal());
    return commitSharedPreferencesEditAsync(editor);
  }

  synchronized Task<Boolean> clear(FirebaseApp firebaseApp) {
    SharedPreferences.Editor editor = prefs.edit();
    editor.remove(getSharedPreferencesKey(firebaseApp, CUSTOM_INSTALLATION_ID_KEY));
    editor.remove(getSharedPreferencesKey(firebaseApp, INSTANCE_ID_KEY));
    editor.remove(getSharedPreferencesKey(firebaseApp, CACHE_STATUS_KEY));
    return commitSharedPreferencesEditAsync(editor);
  }

  @RestrictTo(RestrictTo.Scope.TESTS)
  synchronized Task<Boolean> clearAll() {
    SharedPreferences.Editor editor = prefs.edit();
    editor.clear();
    return commitSharedPreferencesEditAsync(editor);
  }

  private static String getSharedPreferencesKey(FirebaseApp firebaseApp, String key) {
    return String.format("%s|%s", firebaseApp.getPersistenceKey(), key);
  }

  private Task<Boolean> commitSharedPreferencesEditAsync(SharedPreferences.Editor editor) {
    TaskCompletionSource<Boolean> result = new TaskCompletionSource<Boolean>();
    ioExecuter.execute(
        new Runnable() {
          @Override
          public void run() {
            result.setResult(editor.commit());
          }
        });
    return result.getTask();
  }
}
