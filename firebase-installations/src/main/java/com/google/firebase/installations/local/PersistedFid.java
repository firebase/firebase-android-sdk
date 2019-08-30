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
import java.util.Arrays;
import java.util.List;

/**
 * A layer that locally persists a few Firebase Installation attributes on top the Firebase
 * Installation API.
 */
public class PersistedFid {
  // Registration Status of each persisted fid entry
  // NOTE: never change the ordinal of the enum values because the enum values are stored in shared
  // prefs as their ordinal numbers.
  public enum RegistrationStatus {
    /** {@link PersistedFidEntry} is synced to FIS servers */
    REGISTERED,
    /** {@link PersistedFidEntry} is not synced with FIS server */
    UNREGISTERED,
    /** {@link PersistedFidEntry} is in error state when syncing with FIS server */
    REGISTER_ERROR,
    /** {@link PersistedFidEntry} is in pending state when waiting for FIS server response */
    PENDING
  }

  private static final String SHARED_PREFS_NAME = "PersistedFid";

  private static final String FIREBASE_INSTALLATION_ID_KEY = "Fid";
  private static final String AUTH_TOKEN_KEY = "AuthToken";
  private static final String REFRESH_TOKEN_KEY = "RefreshToken";
  private static final String TOKEN_CREATION_TIME_IN_SECONDS_KEY = "TokenCreationEpochInSecs";
  private static final String EXPIRES_IN_SECONDS_KEY = "ExpiresInSecs";
  private static final String PERSISTED_STATUS_KEY = "Status";

  private static final List<String> FID_PREF_KEYS =
      Arrays.asList(
          FIREBASE_INSTALLATION_ID_KEY,
          AUTH_TOKEN_KEY,
          REFRESH_TOKEN_KEY,
          TOKEN_CREATION_TIME_IN_SECONDS_KEY,
          EXPIRES_IN_SECONDS_KEY,
          PERSISTED_STATUS_KEY);

  private final SharedPreferences prefs;
  private final String persistenceKey;

  public PersistedFid(@NonNull FirebaseApp firebaseApp) {
    // Different FirebaseApp in the same Android application should have the same application
    // context and same dir path
    prefs =
        firebaseApp
            .getApplicationContext()
            .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    persistenceKey = firebaseApp.getPersistenceKey();
  }

  @Nullable
  public synchronized PersistedFidEntry readPersistedFidEntryValue() {
    String fid = prefs.getString(getSharedPreferencesKey(FIREBASE_INSTALLATION_ID_KEY), null);
    int status = prefs.getInt(getSharedPreferencesKey(PERSISTED_STATUS_KEY), -1);
    String authToken = prefs.getString(getSharedPreferencesKey(AUTH_TOKEN_KEY), null);
    String refreshToken = prefs.getString(getSharedPreferencesKey(REFRESH_TOKEN_KEY), null);
    long tokenCreationTime =
        prefs.getLong(getSharedPreferencesKey(TOKEN_CREATION_TIME_IN_SECONDS_KEY), 0);
    long expiresIn = prefs.getLong(getSharedPreferencesKey(EXPIRES_IN_SECONDS_KEY), 0);

    if (fid == null
        || status == -1
        || !(status >= 0 && status < RegistrationStatus.values().length)) {
      return null;
    }

    return PersistedFidEntry.builder()
        .setFirebaseInstallationId(fid)
        .setRegistrationStatus(RegistrationStatus.values()[status])
        .setAuthToken(authToken)
        .setRefreshToken(refreshToken)
        .setTokenCreationEpochInSecs(tokenCreationTime)
        .setExpiresInSecs(expiresIn)
        .build();
  }

  @NonNull
  public synchronized boolean insertOrUpdatePersistedFidEntry(
      @NonNull PersistedFidEntry entryValue) {
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(
        getSharedPreferencesKey(FIREBASE_INSTALLATION_ID_KEY),
        entryValue.getFirebaseInstallationId());
    editor.putInt(
        getSharedPreferencesKey(PERSISTED_STATUS_KEY),
        entryValue.getRegistrationStatus().ordinal());
    editor.putString(getSharedPreferencesKey(AUTH_TOKEN_KEY), entryValue.getAuthToken());
    editor.putString(getSharedPreferencesKey(REFRESH_TOKEN_KEY), entryValue.getRefreshToken());
    editor.putLong(
        getSharedPreferencesKey(TOKEN_CREATION_TIME_IN_SECONDS_KEY),
        entryValue.getTokenCreationEpochInSecs());
    editor.putLong(getSharedPreferencesKey(EXPIRES_IN_SECONDS_KEY), entryValue.getExpiresInSecs());
    return editor.commit();
  }

  @NonNull
  public synchronized boolean clear() {
    SharedPreferences.Editor editor = prefs.edit();
    for (String k : FID_PREF_KEYS) {
      editor.remove(getSharedPreferencesKey(k));
    }
    editor.commit();
    return editor.commit();
  }

  private String getSharedPreferencesKey(String key) {
    return String.format("%s|%s", persistenceKey, key);
  }
}
