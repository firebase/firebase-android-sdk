// Copyright 2021 Google LLC
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

package com.google.firebase.app.distribution;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.FirebaseApp;
import com.google.firebase.app.distribution.internal.LogWrapper;

class FirebaseAppDistributionNotificationsManager {
  private static final String TAG = "NotificationsManager:";
  private static final String NOTIFICATION_CHANNEL_ID =
      "com.google.firebase.app.distribution.notification_channel_id";

  @VisibleForTesting
  static final String NOTIFICATION_TAG = "com.google.firebase.app.distribution.tag";

  private final FirebaseApp firebaseApp;

  FirebaseAppDistributionNotificationsManager(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
  }

  void updateNotification(long totalBytes, long downloadedBytes, UpdateStatus status) {
    Context context = firebaseApp.getApplicationContext();
    NotificationManager notificationManager = createNotificationManager(context);
    NotificationCompat.Builder builder = createNotificationBuilder();
    if (isErrorState(status)) {
      builder.setContentTitle(context.getString(R.string.download_failed));
    } else if (status.equals(UpdateStatus.DOWNLOADED)) {
      builder.setContentTitle(context.getString(R.string.download_completed));
    } else {
      builder.setContentTitle(context.getString(R.string.downloading_app_update));
    }
    builder.setProgress(
        100,
        (int) (((float) downloadedBytes / (float) totalBytes) * 100),
        /*indeterminate = */ false);
    notificationManager.notify(NOTIFICATION_TAG, /*id =*/ 0, builder.build());
  }

  // CHECK THIS LATER
  private boolean isErrorState(UpdateStatus status) {
    return status.equals(UpdateStatus.DOWNLOAD_FAILED)
        || status.equals(UpdateStatus.INSTALL_FAILED)
        || status.equals(UpdateStatus.INSTALL_CANCELED)
        || status.equals(UpdateStatus.NEW_RELEASE_CHECK_FAILED)
        || status.equals(UpdateStatus.UPDATE_CANCELED);
  }

  private NotificationManager createNotificationManager(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel =
          new NotificationChannel(
              NOTIFICATION_CHANNEL_ID,
              context.getString(R.string.notifications_channel_name),
              NotificationManager.IMPORTANCE_DEFAULT);
      channel.setDescription(context.getString(R.string.notifications_channel_description));
      // Register the channel with the system; you can't change the importance
      // or other notification behaviors after this
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
      return notificationManager;
    } else {
      return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
  }

  private NotificationCompat.Builder createNotificationBuilder() {
    Context context = firebaseApp.getApplicationContext();
    return new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setOnlyAlertOnce(true)
        .setSmallIcon(getSmallIcon())
        .setContentIntent(createPendingIntent());
  }

  private PendingIntent createPendingIntent() {
    // Query the package manager for the best launch intent for the app
    Context context = firebaseApp.getApplicationContext();
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (intent == null) {
      LogWrapper.getInstance().w(TAG + "No activity found to launch app");
    }
    return PendingIntent.getActivity(
        firebaseApp.getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
  }

  @VisibleForTesting
  int getSmallIcon() {
    Context context = firebaseApp.getApplicationContext();
    int iconId = context.getApplicationInfo().icon;

    if (iconId == 0 || isAdaptiveIcon(iconId)) {
      // fallback to default icon
      return android.R.drawable.sym_def_app_icon;
    }

    return iconId;
  }

  /** Adaptive icons cause a crash loop in the Notifications Manager. See b/69969749. */
  private boolean isAdaptiveIcon(int iconId) {
    try {
      Drawable icon = ContextCompat.getDrawable(firebaseApp.getApplicationContext(), iconId);
      if (VERSION.SDK_INT >= Build.VERSION_CODES.O && icon instanceof AdaptiveIconDrawable) {
        LogWrapper.getInstance()
            .e(TAG + "Adaptive icons cannot be used in notifications. Ignoring icon id: " + iconId);
        return true;
      } else {
        // AdaptiveIcons were introduced in API 26
        return false;
      }
    } catch (Resources.NotFoundException ex) {
      return true;
    }
  }
}
