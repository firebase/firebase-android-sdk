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

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.cloudmessaging.CloudMessageListenerService;
import com.google.android.gms.cloudmessaging.CompletionCallback;
import com.google.android.gms.cloudmessaging.NotificationCallback;
import com.google.firebase.messaging.Constants.MessageNotificationKeys;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * The {@link CloudMessageListenerService} Google Play services binds to. Delegates message handling
 * to the developer's {@link FirebaseMessagingService} and reports every outcome back through the
 * {@link NotificationCallback} and {@link CompletionCallback} wrappers.
 *
 * @hide
 */
public class FirebaseMessagingListenerService extends CloudMessageListenerService {
  private static final String TAG = "FirebaseMsgListener";

  /** Created here rather than in {@code onCreate()} so that it is never null. */
  private final MessagingServiceConnector connector = new MessagingServiceConnector(this);

  /** Moves the handover off the main thread, which is where the connection is reported. */
  private static final Executor notificationDeliveryExecutor =
      FcmExecutors.newNotificationDeliveryExecutor();

  private static final Executor backgroundSyncDeliveryExecutor =
      FcmExecutors.newBackgroundSyncDeliveryExecutor();

  @Override
  protected void handleRemoteNotification(
      @NonNull Bundle messagePayload, @NonNull NotificationCallback callback) {
    RemoteMessage message = new RemoteMessage(messagePayload);

    connector
        .getService()
        .addOnSuccessListener(
            notificationDeliveryExecutor,
            service -> deliverRemoteNotification(service, message, messagePayload, callback))
        .addOnFailureListener(
            notificationDeliveryExecutor,
            e -> {
              Log.e(TAG, "Failed to bind to FirebaseMessagingService", e);
              callback.onError(0);
            });
  }

  /**
   * Hands a RemoteNotification to the developer's service and reports the outcome to Google Play
   * services.
   */
  private void deliverRemoteNotification(
      FirebaseMessagingService service,
      RemoteMessage message,
      Bundle messagePayload,
      NotificationCallback callback) {
    FcmNotificationManager manager = new FcmNotificationManager(callback, this, messagePayload);
    try {
      NotificationResponse response = service.dispatchRemoteNotification(message, manager);
      if (processNotificationResponse(response, callback)) {
        callback.onSuccess();
      } else {
        callback.onError(0);
      }

    } catch (UnimplementedException e) {
      Log.w(
          TAG,
          "onNotificationReceived is not implemented, falling back to the broadcast path for this"
              + " app. If it was overridden, make sure the override does not call super.");
      callback.onError(NotificationCallback.ERROR_UNIMPLEMENTED);
    } catch (Exception e) {
      Log.e(TAG, "Error processing notification response", e);
      callback.onError(0);
    }
  }

  /**
   * Carries out the actions the developer asked for and reports each one to Google Play services.
   *
   * @return whether every action was applied. An empty response is trivially applied; anything else
   *     needs a {@link NotificationManager}, and without one the actions are reported as failed
   *     rather than confirmed.
   */
  private boolean processNotificationResponse(
      NotificationResponse response, NotificationCallback callback) {
    if (response.getActions().isEmpty()) {
      return true;
    }
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (nm == null) {
      Log.e(TAG, "No NotificationManager available, notification actions were not applied");
      return false;
    }
    for (NotificationResponse.NotificationAction action : response.getActions()) {
      if (action.type == NotificationResponse.ActionType.POST) {
        nm.notify(action.tag, action.id, action.notification);
        if (FcmNotificationManager.hasValidChannel(this, nm, action.notification)) {
          callback.onNotificationPosted(action.tag, action.id, SystemClock.elapsedRealtime());
        }
      } else if (action.type == NotificationResponse.ActionType.CANCEL) {
        nm.cancel(action.tag, action.id);
        callback.onNotificationCancelled(action.tag, action.id, SystemClock.elapsedRealtime());
      }
    }
    return true;
  }

  @Override
  protected void handleBackgroundSync(
      @NonNull Bundle messagePayload, @NonNull CompletionCallback callback) {
    RemoteMessage message = new RemoteMessage(messagePayload);

    connector
        .getService()
        .addOnSuccessListener(
            backgroundSyncDeliveryExecutor,
            service -> deliverBackgroundSync(service, message, callback))
        .addOnFailureListener(
            backgroundSyncDeliveryExecutor,
            e -> {
              Log.e(TAG, "Failed to bind to FirebaseMessagingService", e);
              callback.onError(0);
            });
  }

  /** Hands a BackgroundSync to the developer's service. See {@link #deliverRemoteNotification}. */
  private void deliverBackgroundSync(
      FirebaseMessagingService service, RemoteMessage message, CompletionCallback callback) {
    try {
      service.onBackgroundSyncReceived(message);
      callback.onSuccess();
    } catch (UnimplementedException e) {
      Log.w(
          TAG,
          "onBackgroundSyncReceived is not implemented, falling back to the broadcast path for this"
              + " app. If it was overridden, make sure the override does not call super.");
      callback.onError(NotificationCallback.ERROR_UNIMPLEMENTED);
    } catch (Exception e) {
      Log.e(TAG, "Error executing background sync", e);
      callback.onError(0);
    }
  }

  @Override
  protected void handleDisplayNotification(
      @NonNull Bundle messagePayload, @NonNull CompletionCallback callback) {
    notificationDeliveryExecutor.execute(
        () -> deliverDisplayNotification(messagePayload, callback));
  }

  private void deliverDisplayNotification(
      @NonNull Bundle messagePayload, @NonNull CompletionCallback callback) {
    try {
      NotificationParams params = new NotificationParams(messagePayload);
      CommonNotificationBuilder.DisplayNotificationInfo info =
          CommonNotificationBuilder.createNotificationInfo(this, params);
      NotificationManager nm = getSystemService(NotificationManager.class);
      if (nm == null) {
        // Without a NotificationManager the notification cannot be shown, so report the failure
        // instead of confirming a notification that was never posted.
        Log.e(TAG, "No NotificationManager available, notification was not displayed");
        callback.onError(0);
        return;
      }
      ImageDownload imageDownload =
          ImageDownload.create(params.getString(MessageNotificationKeys.IMAGE_URL));
      if (imageDownload != null) {
        ExecutorService networkIoExecutor = FcmExecutors.newNetworkIOExecutor();
        try {
          imageDownload.start(networkIoExecutor);
          DisplayNotification.waitForAndApplyImageDownload(info.notificationBuilder, imageDownload);
        } finally {
          networkIoExecutor.shutdown();
        }
      }
      nm.notify(info.tag, info.id, info.notificationBuilder.build());
      callback.onSuccess();
    } catch (Exception e) {
      Log.e(TAG, "Error displaying notification", e);
      callback.onError(0);
    }
  }

  /**
   * Releases the binding once Google Play services disconnects.
   *
   * <p>No message can arrive after the last client unbinds, and {@code BIND_AUTO_CREATE} keeps the
   * developer's service running until this one unbinds, so releasing here stops it sooner than
   * waiting for {@link #onDestroy()} would. {@code onDestroy()} still tears down for the case where
   * this callback does not run; doing it twice is harmless.
   */
  @Override
  public boolean onUnbind(Intent intent) {
    connector.shutdown();
    return false;
  }

  @Override
  public void onDestroy() {
    connector.shutdown();
    super.onDestroy();
  }

  @VisibleForTesting
  void resetConnectionForTesting() {
    connector.resetForTesting();
  }
}
