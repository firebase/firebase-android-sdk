package com.google.firebase.appdistribution;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.Constants.ErrorMessages;

class FirebaseAppDistributionNotificationsManager {
  private static final String TAG = "FADNotificationsManager";
  private static final String NOTIFICATION_CHANNEL_ID =
      "com.google.firebase.app.distribution.notification_channel_id";

  @VisibleForTesting
  static final String NOTIFICATION_TAG = "com.google.firebase.app.distribution.tag";

  private final FirebaseApp firebaseApp;
  private final NotificationManager notificationManager;

  FirebaseAppDistributionNotificationsManager(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
    this.notificationManager = createNotificationManager();
  }

  void updateNotification(long totalBytes, long downloadedBytes, UpdateStatus status) {
    NotificationCompat.Builder builder = createNotificationBuilder();
    if (isErrorState(status)) {
      builder.setContentTitle(getErrorMessage(status));
    }
    builder.setProgress(
        100,
        (int) (((float) downloadedBytes / (float) totalBytes) * 100),
        /*indeterminate = */ false);
    notificationManager.notify(NOTIFICATION_TAG, /*id =*/ 0, builder.build());
  }

  private boolean isErrorState(UpdateStatus status) {
    if (status.equals(UpdateStatus.DOWNLOAD_FAILED)
        || status.equals(UpdateStatus.INSTALL_FAILED)
        || status.equals(UpdateStatus.INSTALL_CANCELED)) {
      return true;
    }
    return false;
  }

  private String getErrorMessage(UpdateStatus status) {
    switch (status) {
      case INSTALL_CANCELED:
        return ErrorMessages.INSTALLATION_CANCELED;
      case INSTALL_FAILED:
        return ErrorMessages.INSTALLATION_ERROR;
      case DOWNLOAD_FAILED:
      default:
        return ErrorMessages.DOWNLOAD_ERROR;
    }
  }

  private NotificationManager createNotificationManager() {
    Context context = firebaseApp.getApplicationContext();
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
      return (NotificationManager)
          firebaseApp.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }
  }

  private NotificationCompat.Builder createNotificationBuilder() {
    Context context = firebaseApp.getApplicationContext();
    return new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setOnlyAlertOnce(true)
        // TODO: what icon should this be?
        .setSmallIcon(android.R.drawable.sym_def_app_icon)
        .setContentIntent(createPendingIntent())
        .setContentTitle(context.getString(R.string.downloading_app_update));
  }

  private PendingIntent createPendingIntent() {
    // Query the package manager for the best launch intent for the app
    Context context = firebaseApp.getApplicationContext();
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (intent == null) {
      Log.w(TAG, "No activity found to launch app");
    }
    return PendingIntent.getActivity(
        firebaseApp.getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
  }
}
