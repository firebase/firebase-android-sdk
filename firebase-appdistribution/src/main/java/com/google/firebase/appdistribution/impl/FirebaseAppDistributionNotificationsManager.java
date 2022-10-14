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

package com.google.firebase.appdistribution.impl;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.appdistribution.InterruptionLevel;

class FirebaseAppDistributionNotificationsManager {
  private static final String TAG = "FirebaseAppDistributionNotificationsManager";

  private static final String PACKAGE_PREFIX = "com.google.firebase.appdistribution";

  @VisibleForTesting
  static final String CHANNEL_GROUP_ID = prependPackage("notification_channel_group_id");

  @VisibleForTesting
  enum Notification {
    APP_UPDATE("notification_channel_id", "app_update_notification_tag"),
    FEEDBACK("feedback_notification_channel_id", "feedback_notification_tag");

    final String channelId;
    final String tag;
    final int id;

    Notification(String channelId, String tag) {
      this.channelId = prependPackage(channelId);
      this.tag = prependPackage(tag);
      this.id = ordinal();
    }
  }

  private final Context context;
  private final AppIconSource appIconSource;
  private final NotificationManagerCompat notificationManager;

  FirebaseAppDistributionNotificationsManager(Context context) {
    this(context, new AppIconSource());
  }

  @VisibleForTesting
  FirebaseAppDistributionNotificationsManager(Context context, AppIconSource appIconSource) {
    this.context = context;
    this.appIconSource = appIconSource;
    this.notificationManager = NotificationManagerCompat.from(context);
  }

  void showAppUpdateNotification(long totalBytes, long downloadedBytes, int stringResourceId) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LogWrapper.getInstance().i(TAG, "Creating app update notification channel group");
      createChannel(
          Notification.APP_UPDATE,
          R.string.app_update_notification_channel_name,
          R.string.app_update_notification_channel_description,
          InterruptionLevel.DEFAULT);
    }

    if (!notificationManager.areNotificationsEnabled()) {
      LogWrapper.getInstance()
          .w("Not showing app update notifications because app notifications are disabled");
      return;
    }

    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(context, Notification.APP_UPDATE.channelId)
            .setOnlyAlertOnce(true)
            .setSmallIcon(appIconSource.getNonAdaptiveIconOrDefault(context))
            .setContentTitle(context.getString(stringResourceId))
            .setProgress(
                /* max= */ 100,
                /* progress= */ (int) (((float) downloadedBytes / (float) totalBytes) * 100),
                /* indeterminate= */ false);
    PendingIntent appLaunchIntent = createAppLaunchIntent();
    if (appLaunchIntent != null) {
      notificationBuilder.setContentIntent(appLaunchIntent);
    }
    notificationManager.notify(
        Notification.APP_UPDATE.tag, Notification.APP_UPDATE.id, notificationBuilder.build());
  }

  @Nullable
  private PendingIntent createAppLaunchIntent() {
    // Query the package manager for the best launch intent for the app
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (intent == null) {
      LogWrapper.getInstance().w(TAG, "No activity found to launch app");
      return null;
    }
    return PendingIntent.getActivity(
        context, 0, intent, getPendingIntentFlags(PendingIntent.FLAG_ONE_SHOT));
  }

  /**
   * Adds {@link PendingIntent#FLAG_IMMUTABLE} to a PendingIntent's flags since any PendingIntents
   * used here don't need to be modified.
   *
   * <p>Specifying mutability is required starting at SDK level 31.
   */
  private static int getPendingIntentFlags(int baseFlags) {
    // Only add on platform levels that support FLAG_IMMUTABLE.
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ? baseFlags | PendingIntent.FLAG_IMMUTABLE
        : baseFlags;
  }

  public void showFeedbackNotification(
      @NonNull CharSequence infoText, InterruptionLevel interruptionLevel) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LogWrapper.getInstance().i(TAG, "Creating feedback notification channel group");
      createChannel(
          Notification.FEEDBACK,
          R.string.feedback_notification_channel_name,
          R.string.feedback_notification_channel_description,
          interruptionLevel);
    }

    if (!notificationManager.areNotificationsEnabled()) {
      LogWrapper.getInstance()
          .w(TAG, "Not showing notification because app notifications are disabled");
      return;
    }

    Intent intent = new Intent(context, TakeScreenshotAndStartFeedbackActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    intent.putExtra(TakeScreenshotAndStartFeedbackActivity.INFO_TEXT_EXTRA_KEY, infoText);
    PendingIntent pendingIntent =
        PendingIntent.getActivity(
            context, /* requestCode = */ 0, intent, PendingIntent.FLAG_IMMUTABLE);
    ApplicationInfo applicationInfo = context.getApplicationInfo();
    PackageManager packageManager = context.getPackageManager();
    CharSequence appLabel = packageManager.getApplicationLabel(applicationInfo);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, Notification.FEEDBACK.channelId)
            .setSmallIcon(appIconSource.getNonAdaptiveIconOrDefault(context))
            .setContentTitle(context.getString(R.string.feedback_notification_title))
            .setContentText(context.getString(R.string.feedback_notification_text, appLabel))
            .setPriority(getNotificationPriority(interruptionLevel))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent);
    LogWrapper.getInstance().i(TAG, "Showing feedback notification");
    notificationManager.notify(
        Notification.FEEDBACK.tag, Notification.FEEDBACK.id, builder.build());
  }

  public void cancelFeedbackNotification() {
    LogWrapper.getInstance().i(TAG, "Cancelling feedback notification");
    NotificationManagerCompat.from(context)
        .cancel(Notification.FEEDBACK.tag, Notification.FEEDBACK.id);
  }

  private int getNotificationPriority(InterruptionLevel interruptionLevel) {
    switch (interruptionLevel) {
      case MIN:
        return NotificationCompat.PRIORITY_MIN;
      case LOW:
        return NotificationCompat.PRIORITY_LOW;
      case HIGH:
        return NotificationCompat.PRIORITY_HIGH;
      case MAX:
        return NotificationCompat.PRIORITY_MAX;
      case DEFAULT:
      default:
        return NotificationCompat.PRIORITY_DEFAULT;
    }
  }

  private int getChannelImportance(InterruptionLevel interruptionLevel) {
    switch (interruptionLevel) {
      case MIN:
        return NotificationManagerCompat.IMPORTANCE_MIN;
      case LOW:
        return NotificationManagerCompat.IMPORTANCE_LOW;
      case HIGH:
      case MAX: // IMPORTANCE_MAX exists but is so far unused
        return NotificationManagerCompat.IMPORTANCE_HIGH;
      case DEFAULT:
      default:
        return NotificationManagerCompat.IMPORTANCE_DEFAULT;
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private void createChannel(
      Notification notification, int name, int description, InterruptionLevel interruptionLevel) {
    notificationManager.createNotificationChannelGroup(
        new NotificationChannelGroup(
            CHANNEL_GROUP_ID, context.getString(R.string.notifications_group_name)));
    int importance = getChannelImportance(interruptionLevel);
    NotificationChannel channel =
        new NotificationChannel(notification.channelId, context.getString(name), importance);
    channel.setDescription(context.getString(description));
    channel.setGroup(CHANNEL_GROUP_ID);
    // Register the channel with the system; you can't change the importance
    // or other notification behaviors after this
    notificationManager.createNotificationChannel(channel);
  }

  private static String prependPackage(String id) {
    return String.format("%s.%s", PACKAGE_PREFIX, id);
  }
}
