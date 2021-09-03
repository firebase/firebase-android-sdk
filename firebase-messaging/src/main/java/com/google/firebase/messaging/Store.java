// Copyright 2020 Google LLC
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
package com.google.firebase.messaging;

import static com.google.firebase.messaging.FirebaseMessaging.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Internal class used to store the tokens, certificate and bookkeeping.
 *
 * <p>The basic implementation will use a preference file, to keep code simple and reduce
 * dependencies.
 *
 * <p>Schema: the keys use '|' separator. Subtype specific settings start with [subtype]|S| and
 * tokens start with T|subtype|.
 */
class Store {
  // Prefixes for different 'tables'.
  private static final String STORE_KEY_TOKEN = "|T|";

  private static final String SCOPE_ALL = "*";

  // Old GCM library used the same convention
  static final String PREFERENCES = "com.google.android.gms.appid";

  static final String NO_BACKUP_FILE = PREFERENCES + "-no-backup";

  final SharedPreferences store;

  public Store(Context context) {
    this.store = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    checkForRestore(context, NO_BACKUP_FILE);
  }

  private void checkForRestore(Context context, String fileName) {
    File dir = ContextCompat.getNoBackupFilesDir(context);
    File file = new File(dir, fileName);
    if (file.exists()) {
      // File exists, so not a restore, nothing to do
      return;
    }
    try {
      if (file.createNewFile()) {
        // File didn't exist, and was created successfully. This means either the app is a
        // fresh install, or it was restored.
        if (!isEmpty()) {
          // If the prefs are not empty, it must have been restored. Clear the prefs to
          // ensure the backed up values are not used.
          Log.i(TAG, "App restored, clearing state");
          deleteAll();
        }
      }
    } catch (IOException e) {
      // Failed to create file in no backup directory, don't clear prefs
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Error creating file in no backup dir: " + e.getMessage());
      }
    }
  }

  public synchronized boolean isEmpty() {
    return store.getAll().isEmpty();
  }

  private String createTokenKey(String subtype, String audience) {
    return subtype + STORE_KEY_TOKEN + audience + "|" + SCOPE_ALL;
  }

  /** Delete all entries from the store */
  public synchronized void deleteAll() {
    store.edit().clear().commit();
  }

  public synchronized Token getToken(String subtype, String audience) {
    return Token.parse(store.getString(createTokenKey(subtype, audience), null));
  }

  public synchronized void saveToken(
      String subtype, String audience, String token, String appVersion) {
    String encodedToken = Token.encode(token, appVersion, System.currentTimeMillis());
    if (encodedToken == null) {
      // Encoding failed, don't store
      return;
    }
    SharedPreferences.Editor edit = store.edit();
    edit.putString(createTokenKey(subtype, audience), encodedToken);
    edit.commit();
  }

  public synchronized void deleteToken(String subtype, String audience) {
    String key = createTokenKey(subtype, audience);
    SharedPreferences.Editor edit = store.edit();
    edit.remove(key);
    edit.commit();
  }

  /**
   * Token representation including metadata.
   *
   * <p>Tokens are stored in the shared preferences as JSON containing the token, app version and
   * timestamp of when the token was retrieved.
   *
   * <p>Note that on old versions only the token was stored.
   */
  static class Token {

    private static final String KEY_TOKEN = "token";
    private static final String KEY_APP_VERSION = "appVersion";
    private static final String KEY_TIMESTAMP = "timestamp";

    private static final long REFRESH_PERIOD_MILLIS = TimeUnit.DAYS.toMillis(7); // 1 week

    final String token;
    final String appVersion;
    // Time token was retrieved in millis since Epoch (System.currentTimeMillis)
    final long timestamp;

    private Token(String token, String appVersion, long timestamp) {
      this.token = token;
      this.appVersion = appVersion;
      this.timestamp = timestamp;
    }

    static Token parse(String s) {
      if (TextUtils.isEmpty(s)) {
        return null;
      }
      if (s.startsWith("{")) {
        // Encoded as JSON
        try {
          JSONObject json = new JSONObject(s);
          return new Token(
              json.getString(KEY_TOKEN),
              json.getString(KEY_APP_VERSION),
              json.getLong(KEY_TIMESTAMP));
        } catch (JSONException e) {
          Log.w(TAG, "Failed to parse token: " + e);
          return null;
        }
      } else {
        // Legacy value, token is whole string
        return new Token(s, null, 0);
      }
    }

    static String encode(String token, String appVersion, long timestamp) {
      try {
        JSONObject json = new JSONObject();
        json.put(KEY_TOKEN, token);
        json.put(KEY_APP_VERSION, appVersion);
        json.put(KEY_TIMESTAMP, timestamp);
        return json.toString();
      } catch (JSONException e) {
        Log.w(TAG, "Failed to encode token: " + e);
        return null;
      }
    }

    /**
     * Check if this token needs to be refreshed.
     *
     * <p>A token needs to be refreshed if the app has been updated or the refresh period has been
     * exceeded.
     */
    // TODO(morepork) Avoid needing to pass the version in here, perhaps through adding a
    // common class
    boolean needsRefresh(String appVersion) {
      return System.currentTimeMillis() > timestamp + REFRESH_PERIOD_MILLIS
          || !appVersion.equals(this.appVersion);
    }
  }
}
