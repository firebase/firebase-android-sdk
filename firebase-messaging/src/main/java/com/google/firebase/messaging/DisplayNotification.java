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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.BigPictureStyle;
import com.google.android.gms.common.util.PlatformVersion;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.Constants.MessageNotificationKeys;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for handling firebase display notifications.
 *
 * <p>These are a special type of message that we provide a means of handling on behalf of a client
 * app to show a notification.
 */
class DisplayNotification {

  private static final int IMAGE_DOWNLOAD_TIMEOUT_SECONDS = 5;

  private final Executor networkIoExecutor;
  private final Context context;
  private final NotificationParams params;

  public DisplayNotification(
      Context context, NotificationParams params, Executor networkIoExecutor) {
    this.networkIoExecutor = networkIoExecutor;
    this.context = context;
    this.params = params;
  }

  private boolean isAppForeground() {
    KeyguardManager keyguardManager =
        (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    if (keyguardManager.inKeyguardRestrictedInputMode()) {
      return false; // Screen is off or lock screen is showing
    }
    // Screen is on and unlocked, now check if the process is in the foreground

    if (!PlatformVersion.isAtLeastLollipop()) {
      // Before L the process has IMPORTANCE_FOREGROUND while it executes BroadcastReceivers.
      // As soon as the service is started the BroadcastReceiver should stop.
      // UNFORTUNATELY the system might not have had the time to downgrade the process
      // (this is happening consistently in JellyBean).
      // With SystemClock.sleep(10) we tell the system to give a little bit more of CPU
      // to the main thread (this code is executing on a secondary thread) allowing the
      // BroadcastReceiver to exit the onReceive() method and downgrade the process priority.
      SystemClock.sleep(10);
    }
    int pid = Process.myPid();
    ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    List<RunningAppProcessInfo> appProcesses = am.getRunningAppProcesses();
    if (appProcesses != null) {
      for (RunningAppProcessInfo process : appProcesses) {
        if (process.pid == pid) {
          return process.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        }
      }
    }
    return false;
  }

  /**
   * Handle a notification message by displaying it if appropriate, and returning whether the
   * message is consumed.
   * <li>If this is a no UI notification just used for analytics, doesn't show a notification and
   *     returns true.
   * <li>If the app is in the foreground, doesn't show a notification, and returns false.
   * <li>If the app is in the background, shows a notification and returns true.
   */
  boolean handleNotification() {
    if (params.getBoolean(MessageNotificationKeys.NO_UI)) {
      return true; // Fake notification, nothing else to do
    }

    if (isAppForeground()) {
      return false; // Needs to be passed to onMessageReceived
    }
    ImageDownload imageDownload = startImageDownloadInBackground();
    CommonNotificationBuilder.DisplayNotificationInfo notificationInfo =
        CommonNotificationBuilder.createNotificationInfo(context, params);
    waitForAndApplyImageDownload(notificationInfo.notificationBuilder, imageDownload);
    showNotification(notificationInfo);
    return true;
  }

  @Nullable
  private ImageDownload startImageDownloadInBackground() {
    String imageUrl = params.getString(MessageNotificationKeys.IMAGE_URL);
    ImageDownload imageDownload = ImageDownload.create(imageUrl);
    if (imageDownload != null) {
      imageDownload.start(networkIoExecutor);
    }
    return imageDownload;
  }

  private void waitForAndApplyImageDownload(
      NotificationCompat.Builder n, @Nullable ImageDownload imageDownload) {
    if (imageDownload == null) {
      return;
    }
    /*
     * This blocks to wait for the image to finish downloading as this background thread is being
     * used to keep the app (via service or receiver) alive. It can't all be done on one thread
     * as the URLConnection API used to download the image is blocking, so another thread is needed
     * to enforce the timeout.
     */
    try {
      Bitmap bitmap =
          Tasks.await(imageDownload.getTask(), IMAGE_DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      /*
       * Set large icon for non-expanded mode (shows up on the right), and the big picture for
       * expanded mode. Clear the large icon in expanded mode as having the small image on the right
       * and the large expanded image doesn't look great. This is what the Android screenshot
       * notification does as well.
       */
      n.setLargeIcon(bitmap);
      n.setStyle(new BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null));

    } catch (ExecutionException e) {
      // For all exceptions, fall through to show the notification without the image

      Log.w(TAG, "Failed to download image: " + e.getCause());
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted while downloading image, showing notification without it");
      imageDownload.close();
      Thread.currentThread().interrupt(); // Restore the interrupted status
    } catch (TimeoutException e) {
      Log.w(TAG, "Failed to download image in time, showing notification without it");
      imageDownload.close();
      /*
       * Instead of cancelling the task, could let the download continue, and update the
       * notification if it was still showing. For this we would need to cancel the download if the
       * user opens or dismisses the notification, and make sure the notification doesn't buzz again
       * when it is updated.
       */
    }
  }

  private void showNotification(CommonNotificationBuilder.DisplayNotificationInfo info) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Showing notification");
    }

    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

    notificationManager.notify(info.tag, info.id, info.notificationBuilder.build());
  }
}
