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

import static com.google.firebase.messaging.Constants.TAG;
import static com.google.firebase.messaging.FirebaseMessaging.GMS_PACKAGE;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.util.Log;
import androidx.annotation.WorkerThread;
import com.google.android.gms.common.util.PlatformVersion;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.Executor;

/**
 * Class which handles proxy notification auto initialisation.
 *
 * @hide
 */
final class ProxyNotificationInitializer {

  private static final String MANIFEST_METADATA_NOTIFICATION_DELEGATION_ENABLED =
      "firebase_messaging_notification_delegation_enabled";

  private ProxyNotificationInitializer() {
    // Private constructor. Do not instantiate.
  }

  /**
   * Enables or disables proxy notification
   *
   * <p>Enabling proxy notification on Android versions lower than 'Q' is not supported.
   *
   * @param executor executor which will be used for disk I/O
   * @param context application context
   * @param enabled whether to enable or disable proxy notifications
   */
  @TargetApi(Build.VERSION_CODES.Q)
  static Task<Void> setEnableProxyNotification(
      Executor executor, Context context, boolean enabled) {
    if (!PlatformVersion.isAtLeastQ()) {
      /**
       * Notification proxying is supported only on Android Q and above versions since it requires
       * the notification delegate to be set as GMS core and availability of proxy notification
       * functions such as {@link NotificationManager#notifyAsPackage(String, String, int,
       * Notification)}
       */
      return Tasks.forResult(null);
    }

    TaskCompletionSource<Void> completionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          try {
            if (!allowedToUse(context)) {
              // we've been initialized with someone else's context and we're about to run into a
              // security exception. Bail now instead of pretending initialization went ok.
              Log.e(
                  TAG,
                  "error configuring notification delegate for package "
                      + context.getPackageName());
              return;
            }

            // Record initialization in shared preferences so we don't waste accidentally
            // reinitialize in the future if the developer has opted out.
            ProxyNotificationPreferences.setProxyNotificationsInitialized(context, true);

            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (enabled) {
              // In order to support proxy notifications, the application needs to allow GmsCore to
              // create and post notifications on its behalf (a so called "delegate").
              nm.setNotificationDelegate(GMS_PACKAGE);
            } else if (GMS_PACKAGE.equals(nm.getNotificationDelegate())) {
              // If we're being asked to disable this feature and if the current delegate is GmsCore
              // then we'll clear it, otherwise we may accidentally clear a totally different use of
              // this feature and introduce subtle bugs.
              nm.setNotificationDelegate(null);
            }
          } finally {
            completionSource.trySetResult(null);
          }
        });
    return completionSource.getTask();
  }

  /**
   * Gets whether proxy notification support is enabled for the app.
   *
   * <p>Checks to see if the app has programmatically set the proxy notification support and returns
   * it if exists. If not set programmatically, the manifest value for metadata
   * "firebase_messaging_notification_delegation_enabled" will be returned. If both are absent, the
   * default value of true will be returned.
   *
   * @param context application context.
   * @return true if proxy notification support is enabled, false otherwise.
   */
  static boolean isProxyNotificationEnabled(Context context) {
    if (!PlatformVersion.isAtLeastQ()) {
      // Proxy notification is not supported below Android Q
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Platform doesn't support proxying.");
      }
      return false;
    }

    if (!allowedToUse(context)) {
      Log.e(TAG, "error retrieving notification delegate for package " + context.getPackageName());
      return false;
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    String delegatePkg = notificationManager.getNotificationDelegate();
    if (GMS_PACKAGE.equals(delegatePkg)) {
      // If the notification delegate is already set as GMS core, there is no need to check further.
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "GMS core is set for proxying");
      }
      return true;
    }

    return false;
  }

  /**
   * Checks whether user has opted for enabling proxy notifications in manifest. If no value found
   * in manifest, default is taken as true
   */
  private static boolean shouldEnableProxyNotification(Context context) {
    // Check if there's metadata in the manifest setting the proxy notification state.
    try {
      Context applicationContext = context.getApplicationContext();
      PackageManager packageManager = applicationContext.getPackageManager();
      if (packageManager != null) {
        ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                applicationContext.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo != null
            && applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(
                MANIFEST_METADATA_NOTIFICATION_DELEGATION_ENABLED)) {
          return applicationInfo.metaData.getBoolean(
              MANIFEST_METADATA_NOTIFICATION_DELEGATION_ENABLED);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // This shouldn't happen since it's this app's package, but fall through to default if so.
    }

    // Default value is proxy notifications enabled.
    return true;
  }

  /**
   * Initializes proxy notification support for the app
   *
   * @param context application context
   */
  @WorkerThread
  static void initialize(Context context) {
    if (ProxyNotificationPreferences.isProxyNotificationInitialized(context)) {
      // The proxy notifications has already been initialized in the past and we don't want to re do
      // it again and overwrite if any value being set by the 3p developer.
      return;
    }

    setEnableProxyNotification(Runnable::run, context, shouldEnableProxyNotification(context));
  }

  private static boolean allowedToUse(Context context) {
    return Binder.getCallingUid() == context.getApplicationInfo().uid;
  }
}
