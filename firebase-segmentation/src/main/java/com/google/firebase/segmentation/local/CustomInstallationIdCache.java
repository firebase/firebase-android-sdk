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

package com.google.firebase.segmentation.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A layer that locally caches a few Firebase Segmentation attributes on top the Segmentation
 * backend API.
 */
public class CustomInstallationIdCache {

  // Status of each cache entry
  // NOTE: never change the ordinal of the enum values because the enum values are stored in cache
  // as their ordinal numbers.
  public enum CacheStatus {
    // Cache entry is synced to Firebase backend
    SYNCED,
    // Cache entry is waiting for Firebase backend response or internal network retry (for update
    // operation).
    PENDING_UPDATE,
    // Cache entry is waiting for Firebase backend response or internal network retry (for clear
    // operation).
    PENDING_CLEAR
  }

  private static final String SHARED_PREFS_NAME = "CustomInstallationIdCache";

  private static final String CUSTOM_INSTALLATION_ID_KEY = "Cid";
  private static final String INSTANCE_ID_KEY = "Iid";
  private static final String CACHE_STATUS_KEY = "Status";

  private final Executor ioExecuter;
  private final SharedPreferences prefs;
  private final String persistenceKey;

  public CustomInstallationIdCache(FirebaseApp firebaseApp) {
    // Different FirebaseApp in the same Android application should have the same application
    // context and same dir path
    prefs =
        firebaseApp
            .getApplicationContext()
            .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    persistenceKey = firebaseApp.getPersistenceKey();
    ioExecuter = Executors.newFixedThreadPool(2);
  }

  @Nullable
  public synchronized CustomInstallationIdCacheEntryValue readCacheEntryValue() {
    String cid = prefs.getString(getSharedPreferencesKey(CUSTOM_INSTALLATION_ID_KEY), null);
    String iid = prefs.getString(getSharedPreferencesKey(INSTANCE_ID_KEY), null);
    int status = prefs.getInt(getSharedPreferencesKey(CACHE_STATUS_KEY), -1);

    if (cid == null || iid == null || status == -1) {
      return null;
    }

    return CustomInstallationIdCacheEntryValue.create(cid, iid, CacheStatus.values()[status]);
  }

  public synchronized Task<Boolean> insertOrUpdateCacheEntry(
      CustomInstallationIdCacheEntryValue entryValue) {
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(
        getSharedPreferencesKey(CUSTOM_INSTALLATION_ID_KEY), entryValue.getCustomInstallationId());
    editor.putString(getSharedPreferencesKey(INSTANCE_ID_KEY), entryValue.getFirebaseInstanceId());
    editor.putInt(getSharedPreferencesKey(CACHE_STATUS_KEY), entryValue.getCacheStatus().ordinal());
    return commitSharedPreferencesEditAsync(editor);
  }

  public synchronized Task<Boolean> clear() {
    SharedPreferences.Editor editor = prefs.edit();
    editor.remove(getSharedPreferencesKey(CUSTOM_INSTALLATION_ID_KEY));
    editor.remove(getSharedPreferencesKey(INSTANCE_ID_KEY));
    editor.remove(getSharedPreferencesKey(CACHE_STATUS_KEY));
    return commitSharedPreferencesEditAsync(editor);
  }

  private String getSharedPreferencesKey(String key) {
    return String.format("%s|%s", persistenceKey, key);
  }

  private Task<Boolean> commitSharedPreferencesEditAsync(SharedPreferences.Editor editor) {
    TaskCompletionSource<Boolean> result = new TaskCompletionSource<>();
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
