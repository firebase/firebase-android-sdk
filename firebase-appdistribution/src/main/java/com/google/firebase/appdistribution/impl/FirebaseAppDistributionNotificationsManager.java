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

import static com.google.firebase.appdistribution.impl.FirebaseAppDistributionNotificationsManager.NotificationType.APP_UPDATE;
import static com.google.firebase.appdistribution.impl.FirebaseAppDistributionNotificationsManager.NotificationType.FEEDBACK;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.Activity;
import android.app.Notification;
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
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionLifecycleNotifier.OnActivityPausedListener;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionLifecycleNotifier.OnActivityResumedListener;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class FirebaseAppDistributionNotificationsManager
    implements OnActivityPausedListener, OnActivityResumedListener {

  private static final String TAG = "NotificationsManager";

  private static final String PACKAGE_PREFIX = "com.google.firebase.appdistribution";

  @VisibleForTesting
  static final String CHANNEL_GROUP_ID = prependPackage("notification_channel_group_id");

  @VisibleForTesting
  enum NotificationType {
    APP_UPDATE("notification_channel_id", "app_update_notification_tag"),
    FEEDBACK("feedback_notification_channel_id", "feedback_notification_tag");

    final String channelId;
    final String tag;
    final int id;

    NotificationType(String channelId, String tag) {
      this.channelId = prependPackage(channelId);
      this.tag = prependPackage(tag);
      this.id = ordinal();
    }
  }

  private final Context context;
  private final AppIconSource appIconSource;
  private final NotificationManagerCompat notificationManager;
  @Lightweight private final ScheduledExecutorService scheduledExecutorService;
  @UiThread private final Executor uiThreadExecutor;

  private Notification feedbackNotificationToBeShown;
  private ScheduledFuture<?> feedbackNotificationCancellationFuture;

  @Inject
  FirebaseAppDistributionNotificationsManager(
      Context context,
      AppIconSource appIconSource,
      FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      @Lightweight ScheduledExecutorService scheduledExecutorService,
      @UiThread Executor uiThreadExecutor) {
    this.context = context;
    this.appIconSource = appIconSource;
    this.notificationManager = NotificationManagerCompat.from(context);
    lifecycleNotifier.addOnActivityPausedListener(this);
    lifecycleNotifier.addOnActivityResumedListener(this);
    this.scheduledExecutorService = scheduledExecutorService;
    this.uiThreadExecutor = uiThreadExecutor;
  }

  void showAppUpdateNotification(long totalBytes, long downloadedBytes, int stringResourceId) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LogWrapper.i(TAG, "Creating app update notification channel group");
      createChannel(
          APP_UPDATE,
          R.string.app_update_notification_channel_name,
          R.string.app_update_notification_channel_description,
          InterruptionLevel.DEFAULT);
    }

    if (!notificationManager.areNotificationsEnabled()) {
      LogWrapper.w(
          TAG, "Not showing app update notifications because app notifications are disabled");
      return;
    }

    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(context, APP_UPDATE.channelId)
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
    notificationManager.notify(APP_UPDATE.tag, APP_UPDATE.id, notificationBuilder.build());
  }

  @Nullable
  private PendingIntent createAppLaunchIntent() {
    // Query the package manager for the best launch intent for the app
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (intent == null) {
      LogWrapper.w(TAG, "No activity found to launch app");
      return null;
    }
    return getPendingIntent(intent, PendingIntent.FLAG_ONE_SHOT);
  }

  private PendingIntent getPendingIntent(Intent intent, int extraFlags) {
    // Specify mutability because it is required starting at SDK level 31, but FLAG_IMMUTABLE is
    // only supported starting at SDK level 23
    int commonFlags =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    return PendingIntent.getActivity(
        context, /* requestCode = */ 0, intent, extraFlags | commonFlags);
  }

  public void showFeedbackNotification(
      @NonNull CharSequence additionalFormText, @NonNull InterruptionLevel interruptionLevel) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LogWrapper.i(TAG, "Creating feedback notification channel group");
      createChannel(
          FEEDBACK,
          R.string.feedback_notification_channel_name,
          R.string.feedback_notification_channel_description,
          interruptionLevel);
    }

    if (!notificationManager.areNotificationsEnabled()) {
      LogWrapper.w(TAG, "Not showing notification because app notifications are disabled");
      return;
    }

    uiThreadExecutor.execute(
        () -> {
          // ensure that class state is managed on same thread as lifecycle callbacks
          cancelFeedbackCancellationFuture();
          feedbackNotificationToBeShown =
              buildFeedbackNotification(additionalFormText, interruptionLevel);
          doShowFeedbackNotification();
        });
  }

  // this must be run on the main (UI) thread
  private void doShowFeedbackNotification() {
    LogWrapper.i(TAG, "Showing feedback notification");
    notificationManager.notify(FEEDBACK.tag, FEEDBACK.id, feedbackNotificationToBeShown);
  }

  private Notification buildFeedbackNotification(
      @NonNull CharSequence additionalFormText, @NonNull InterruptionLevel interruptionLevel) {
    Intent intent = new Intent(context, TakeScreenshotAndStartFeedbackActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    intent.putExtra(
        TakeScreenshotAndStartFeedbackActivity.ADDITIONAL_FORM_TEXT_EXTRA_KEY, additionalFormText);
    ApplicationInfo applicationInfo = context.getApplicationInfo();
    PackageManager packageManager = context.getPackageManager();
    CharSequence appLabel = packageManager.getApplicationLabel(applicationInfo);
    return new NotificationCompat.Builder(context, FEEDBACK.channelId)
        .setSmallIcon(R.drawable.ic_rate_review)
        .setContentTitle(context.getString(R.string.feedback_notification_title))
        .setContentText(context.getString(R.string.feedback_notification_text, appLabel))
        .setPriority(interruptionLevel.notificationPriority)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(false)
        .setContentIntent(getPendingIntent(intent, /* extraFlags= */ 0))
        .build();
  }

  public void cancelFeedbackNotification() {
    uiThreadExecutor.execute(
        () -> {
          // ensure that class state is managed on same thread as lifecycle callbacks
          feedbackNotificationToBeShown = null;
          cancelFeedbackCancellationFuture();
          doCancelFeedbackNotification();
        });
  }

  public void doCancelFeedbackNotification() {
    LogWrapper.i(TAG, "Canceling feedback notification");
    NotificationManagerCompat.from(context).cancel(FEEDBACK.tag, FEEDBACK.id);
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private void createChannel(
      NotificationType notification,
      int name,
      int description,
      InterruptionLevel interruptionLevel) {
    notificationManager.createNotificationChannelGroup(
        new NotificationChannelGroup(
            CHANNEL_GROUP_ID, context.getString(R.string.notifications_group_name)));
    NotificationChannel channel =
        new NotificationChannel(
            notification.channelId, context.getString(name), interruptionLevel.channelImportance);
    channel.setDescription(context.getString(description));
    channel.setGroup(CHANNEL_GROUP_ID);
    // Register the channel with the system; you can't change the importance
    // or other notification behaviors after this
    notificationManager.createNotificationChannel(channel);
  }

  // this runs on the main (UI) thread
  @Override
  public void onPaused(Activity activity) {
    LogWrapper.d(TAG, "Activity paused");
    if (feedbackNotificationToBeShown != null) {
      LogWrapper.d(TAG, "Scheduling cancelFeedbackNotification");
      cancelFeedbackCancellationFuture();
      feedbackNotificationCancellationFuture =
          scheduledExecutorService.schedule(this::doCancelFeedbackNotification, 1, SECONDS);
    }
  }

  // this runs on the main (UI) thread
  @Override
  public void onResumed(Activity activity) {
    LogWrapper.d(TAG, "Activity resumed");
    if (feedbackNotificationToBeShown != null) {
      cancelFeedbackCancellationFuture();
      doShowFeedbackNotification();
    }
  }

  // this must be run on the main (UI) thread
  private void cancelFeedbackCancellationFuture() {
    if (feedbackNotificationCancellationFuture != null) {
      LogWrapper.d(TAG, "Canceling feedbackNotificationCancellationFuture");
      feedbackNotificationCancellationFuture.cancel(false);
      feedbackNotificationCancellationFuture = null;
    }
  }

  private static String prependPackage(String id) {
    return String.format("%s.%s", PACKAGE_PREFIX, id);
  }
}
