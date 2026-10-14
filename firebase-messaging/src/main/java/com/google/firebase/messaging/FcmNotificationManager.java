// Copyright 2026 Google LLC
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.cloudmessaging.NotificationCallback;
import com.google.firebase.messaging.Constants.MessagePayloadKeys;

/**
 * Posts and cancels notifications during {@link
 * FirebaseMessagingService#onNotificationReceived(RemoteMessage)}.
 *
 * <p>Valid only for the duration of that call. Later use throws {@link IllegalStateException}.
 */
public class FcmNotificationManager {

  private volatile boolean active = true;
  private final NotificationCallback callback;
  private final Context context;
  private final long deadlineTimestampMs;

  FcmNotificationManager(NotificationCallback callback, Context context, @Nullable Bundle payload) {
    this.callback = callback;
    this.context = context;
    if (payload != null
        && payload.containsKey(MessagePayloadKeys.TIMEOUT_TIMESTAMP_MS)) {
      this.deadlineTimestampMs =
          payload.getLong(MessagePayloadKeys.TIMEOUT_TIMESTAMP_MS);
    } else {
      this.deadlineTimestampMs = Long.MAX_VALUE;
    }
  }

  void invalidate() {
    active = false;
  }

  private void checkActive() {
    if (!active) {
      throw new IllegalStateException(
          "FcmNotificationManager cannot be used after onNotificationReceived returns");
    }
    if (SystemClock.elapsedRealtime() > deadlineTimestampMs) {
      invalidate();
      throw new IllegalStateException(
          "FcmNotificationManager cannot be used after the notification handling timeout has"
              + " elapsed");
    }
  }

  /**
   * Posts a notification immediately using the local {@link NotificationManager}. The event is
   * reported to Google Play services when the notification targets a valid channel.
   *
   * @param tag The notification tag, or null.
   * @param id The notification ID.
   * @param notification The notification to post.
   * @throws IllegalStateException if called after onNotificationReceived returns or times out.
   */
  public void postNow(@Nullable String tag, int id, @NonNull Notification notification) {
    checkActive();
    NotificationManager nm = context.getSystemService(NotificationManager.class);
    if (nm != null) {
      nm.notify(tag, id, notification);
      if (hasValidChannel(context, nm, notification)) {
        callback.onNotificationPosted(tag, id, SystemClock.elapsedRealtime());
      }
    }
  }

  static boolean hasValidChannel(
      Context context, NotificationManager nm, Notification notification) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return true;
    }
    try {
      if (context
              .getPackageManager()
              .getApplicationInfo(context.getPackageName(), /* flags= */ 0)
              .targetSdkVersion
          < Build.VERSION_CODES.O) {
        return true;
      }
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
    String channelId = notification.getChannelId();
    if (TextUtils.isEmpty(channelId)) {
      return false;
    }
    NotificationChannel channel = nm.getNotificationChannel(channelId);
    return channel != null && channel.getImportance() > NotificationManager.IMPORTANCE_NONE;
  }

  /**
   * Cancels a notification immediately using the local {@link NotificationManager}. The
   * cancellation event is reported to Google Play services.
   *
   * @param tag The notification tag, or null.
   * @param id The notification ID.
   * @throws IllegalStateException if called after onNotificationReceived returns or times out.
   */
  public void cancelNow(@Nullable String tag, int id) {
    checkActive();
    NotificationManager nm = context.getSystemService(NotificationManager.class);
    if (nm != null) {
      nm.cancel(tag, id);
    }
    callback.onNotificationCancelled(tag, id, SystemClock.elapsedRealtime());
  }

  /**
   * Invalidates this manager and returns an empty response, for a callback that has already posted
   * everything it wanted to.
   *
   * @return An empty NotificationResponse.
   */
  @NonNull
  public NotificationResponse finished() {
    invalidate();
    return NotificationResponse.suppress();
  }
}
