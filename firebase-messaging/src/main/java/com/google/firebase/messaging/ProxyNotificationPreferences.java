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

import static com.google.firebase.messaging.ProxyNotificationPreferences.PreferenceKeys.KEY_PROXY_NOTIFICATION_INITIALIZED;
import static com.google.firebase.messaging.ProxyNotificationPreferences.PreferenceKeys.KEY_PROXY_NOTIFICATION_RETENTION;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import com.google.android.gms.common.util.PlatformVersion;

/**
 * Firebase helper class to store and retrieve firebase related preference values.
 *
 * @hide
 */
final class ProxyNotificationPreferences {

  private static final String FCM_PREFERENCES = "com.google.firebase.messaging";

  private ProxyNotificationPreferences() {
    // Private constructor. Do not instantiate.
  }

  private static SharedPreferences getPreference(Context context) {
    Context appContext = context.getApplicationContext();
    if (appContext == null) {
      appContext = context;
    }

    return appContext.getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE);
  }

  @WorkerThread
  static void setProxyNotificationsInitialized(Context context, boolean isInitialized) {
    SharedPreferences.Editor preferencesEditor = getPreference(context).edit();
    preferencesEditor.putBoolean(KEY_PROXY_NOTIFICATION_INITIALIZED, isInitialized);
    preferencesEditor.apply();
  }

  @WorkerThread
  static void setProxyRetention(Context context, GmsRpc gmsRpc, boolean retention) {
    if (!PlatformVersion.isAtLeastQ()) {
      // Proxy supported on Q+.
      return;
    }
    SharedPreferences preferences = getPreference(context);
    if (!isProxyNotificationRetentionSet(preferences, retention)) {
      gmsRpc
          .setRetainProxiedNotifications(retention)
          .addOnSuccessListener(
              Runnable::run, unused -> setProxyRetentionPreferences(context, retention));
    }
  }

  @WorkerThread
  static boolean isProxyNotificationInitialized(Context context) {
    return getPreference(context).getBoolean(KEY_PROXY_NOTIFICATION_INITIALIZED, false);
  }

  @WorkerThread
  static boolean isProxyNotificationRetentionSet(SharedPreferences preferences, boolean retention) {
    return preferences.contains(KEY_PROXY_NOTIFICATION_RETENTION)
        && preferences.getBoolean(KEY_PROXY_NOTIFICATION_RETENTION, false) == retention;
  }

  @WorkerThread
  static void setProxyRetentionPreferences(Context context, boolean retention) {
    SharedPreferences.Editor preferencesEditor = getPreference(context).edit();
    preferencesEditor.putBoolean(KEY_PROXY_NOTIFICATION_RETENTION, retention);
    preferencesEditor.apply();
  }

  @StringDef({KEY_PROXY_NOTIFICATION_INITIALIZED, KEY_PROXY_NOTIFICATION_RETENTION})
  @interface PreferenceKeys {
    String KEY_PROXY_NOTIFICATION_INITIALIZED = "proxy_notification_initialized";
    String KEY_PROXY_NOTIFICATION_RETENTION = "proxy_retention";
  }
}
