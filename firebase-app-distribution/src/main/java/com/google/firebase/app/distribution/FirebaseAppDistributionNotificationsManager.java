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
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import com.google.firebase.app.distribution.internal.LogWrapper;

class FirebaseAppDistributionNotificationsManager {
  private static final String TAG = "NotificationsManager:";
  private static final String NOTIFICATION_CHANNEL_ID =
      "com.google.firebase.app.distribution.notification_channel_id";

  @VisibleForTesting
  static final String NOTIFICATION_TAG = "com.google.firebase.app.distribution.tag";

  private final Context context;
  private final AppIconSource appIconSource;

  FirebaseAppDistributionNotificationsManager(Context context) {
    this(context, new AppIconSource());
  }

  @VisibleForTesting
  FirebaseAppDistributionNotificationsManager(Context context, AppIconSource appIconSource) {
    this.context = context;
    this.appIconSource = appIconSource;
  }

  void updateNotification(long totalBytes, long downloadedBytes, UpdateStatus status) {
    NotificationManager notificationManager = createNotificationManager(context);
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setSmallIcon(appIconSource.getNonAdaptiveIconOrDefault(context))
            .setContentTitle(context.getString(getNotificationContentTitleId(status)))
            .setProgress(
                100,
                (int) (((float) downloadedBytes / (float) totalBytes) * 100),
                /*indeterminate = */ false);
    PendingIntent appLaunchIntent = createAppLaunchIntent();
    if (appLaunchIntent != null) {
      notificationBuilder.setContentIntent(appLaunchIntent);
    }
    notificationManager.notify(NOTIFICATION_TAG, /*id =*/ 0, notificationBuilder.build());
  }

  int getNotificationContentTitleId(UpdateStatus status) {
    if (isErrorState(status)) {
      return R.string.download_failed;
    } else if (status.equals(UpdateStatus.DOWNLOADED)) {
      return R.string.download_completed;
    } else {
      return R.string.downloading_app_update;
    }
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
      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.createNotificationChannel(channel);
      return notificationManager;
    } else {
      return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
  }

  @Nullable
  private PendingIntent createAppLaunchIntent() {
    // Query the package manager for the best launch intent for the app
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (intent == null) {
      LogWrapper.getInstance().w(TAG + "No activity found to launch app");
      return null;
    }
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
  }
}
