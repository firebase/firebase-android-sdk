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

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.iid.MessengerIpcClient;
import com.google.firebase.iid.ServiceStarter;
import com.google.firebase.messaging.Constants.IntentActionKeys;
import com.google.firebase.messaging.Constants.IntentKeys;
import com.google.firebase.messaging.Constants.MessagePayloadKeys;
import com.google.firebase.messaging.Constants.MessageTypes;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class for receiving messages from Firebase Cloud Messaging.
 *
 * <p>Extending this class is required to be able to handle downstream messages. It also provides
 * functionality to automatically display notifications, and has methods that are invoked to give
 * the status of upstream messages.
 *
 * <p>Override base class methods to handle any events required by the application. All methods are
 * invoked on a background thread, and <em>may be called when the app is in the background or not
 * open</em>.
 *
 * <p>Include the following in the manifest:
 *
 * <pre>
 * {@literal
 * <service
 *     android:name=".YourFirebaseMessagingService"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *     </intent-filter>
 * </service>}</pre>
 *
 * <p>To support receiving messages in <a
 * href="https://developer.android.com/training/articles/direct-boot">direct boot mode</a>, add
 * <code>android:directBootAware="true"</code> to the <code>service</code> declaration. See <a
 * href="https://firebase.google.com/docs/cloud-messaging/android/receive#receive_fcm_messages_in_direct_boot_mode">Receive
 * FCM messages in direct boot mode</a> for more information.
 */
public class FirebaseMessagingService extends EnhancedIntentService {

  static final String ACTION_REMOTE_INTENT = "com.google.android.c2dm.intent.RECEIVE";

  /**
   * Action for Firebase Cloud Messaging direct boot message intents.
   *
   * @hide
   */
  public static final String ACTION_DIRECT_BOOT_REMOTE_INTENT =
      "com.google.firebase.messaging.RECEIVE_DIRECT_BOOT";

  private static final String ACTION_NEW_TOKEN = "com.google.firebase.messaging.NEW_TOKEN";
  private static final String EXTRA_TOKEN = "token";

  private static final int RECENTLY_RECEIVED_MESSAGE_IDS_MAX_SIZE = 10;

  /**
   * Last N message IDs that have been received by this app to prevent duplicate messages.
   *
   * <p>This is small enough that it can be kept static to survive service restarts.
   */
  private static final Queue<String> recentlyReceivedMessageIds =
      new ArrayDeque<>(RECENTLY_RECEIVED_MESSAGE_IDS_MAX_SIZE);

  /**
   * Called when a message is received.
   *
   * <p>This is also called when a notification message is received while the app is in the
   * foreground. The notification parameters can be retrieved with {@link
   * RemoteMessage#getNotification()}.
   *
   * @param message Remote message that has been received.
   */
  @WorkerThread
  public void onMessageReceived(@NonNull RemoteMessage message) {}

  /**
   * Called when the Firebase Cloud Messaging server deletes pending messages. This may be due to:
   *
   * <p>
   *
   * <ol>
   *   <li>Too many messages stored on the Firebase Cloud Messaging server. This can occur when the
   *       app's servers send a bunch of non-collapsible messages to Firebase Cloud Messaging
   *       servers while the device is offline.
   *   <li>The device hasn't connected in a long time and the app server has recently (within the
   *       last 4 weeks) sent a message to the app on that device.
   * </ol>
   *
   * <p>It is recommended that the app do a full sync with the app server after receiving this call.
   * See <a
   * href="https://firebase.google.com/docs/cloud-messaging/android/receive#override-ondeletedmessages">here</a>
   * for more information.
   */
  @WorkerThread
  public void onDeletedMessages() {}

  /**
   * Called when an upstream message has been successfully sent to the GCM connection server.
   *
   * @param msgId of the upstream message sent using {@link FirebaseMessaging#send}.
   */
  @WorkerThread
  public void onMessageSent(@NonNull String msgId) {}

  /**
   * Called when there was an error sending an upstream message.
   *
   * @param msgId of the upstream message sent using {@link FirebaseMessaging#send}.
   * @param exception description of the error, typically a {@link SendException}.
   */
  @WorkerThread
  public void onSendError(@NonNull String msgId, @NonNull Exception exception) {}

  /**
   * Called when a new token for the default Firebase project is generated.
   *
   * <p>This is invoked after app install when a token is first generated, and again if the token
   * changes.
   *
   * @param token The token used for sending messages to this application instance. This token is
   *     the same as the one retrieved by {@link
   *     com.google.firebase.iid.FirebaseInstanceId#getInstanceId()}.
   */
  @WorkerThread
  public void onNewToken(@NonNull String token) {};

  /** @hide */
  @Override
  protected Intent getStartCommandIntent(Intent originalIntent) {
    return ServiceStarter.getInstance().getMessagingEvent();
  }

  /** @hide */
  @Override
  public boolean handleIntentOnMainThread(Intent intent) {
    if (IntentActionKeys.NOTIFICATION_OPEN.equals(intent.getAction())) {
      // This is in response to the user opening the notification, process on the main thread
      handleNotificationOpen(intent);
      return true;
    }
    return false;
  }

  private void handleNotificationOpen(Intent intent) {
    PendingIntent pi = intent.getParcelableExtra(IntentKeys.PENDING_INTENT);
    if (pi != null) {
      // Pending intent will asynchronously start the app's Activity
      try {
        pi.send();
      } catch (CanceledException e) {
        Log.e(TAG, "Notification pending intent canceled"); // Shouldn't happen
      }
    }
    if (MessagingAnalytics.shouldUploadScionMetrics(intent)) {
      MessagingAnalytics.logNotificationOpen(intent);
    }
  }

  /** @hide */
  @Override
  public void handleIntent(Intent intent) {
    String action = intent.getAction();

    // Using if/else here instead of a switch to reduce code size
    if (ACTION_REMOTE_INTENT.equals(action) || ACTION_DIRECT_BOOT_REMOTE_INTENT.equals(action)) {
      handleMessageIntent(intent);
    } else if (IntentActionKeys.NOTIFICATION_DISMISS.equals(action)) {
      // Service started just to send analytics on dismiss
      if (MessagingAnalytics.shouldUploadScionMetrics(intent)) {
        MessagingAnalytics.logNotificationDismiss(intent);
      }
    } else if (ACTION_NEW_TOKEN.equals(action)) {
      onNewToken(intent.getStringExtra(EXTRA_TOKEN));
    } else {
      Log.d(TAG, "Unknown intent action: " + intent.getAction());
    }
  }

  private void handleMessageIntent(Intent intent) {
    String messageId = intent.getStringExtra(MessagePayloadKeys.MSGID);
    /*
     * Acknowledge that the message was received asynchronously. This is done before the app has a
     * chance to actually handle the message so there is a small window for the app to get killed
     * even though GmsCore got a successful ack (e.g. reboot, app gets oom killed), but given apps
     * frequently do non-idempotent work on receiving a message (e.g. displaying a notification) it
     * is safer to not wait for them to finish.
     *
     * We ack even if it was a duplicate message as GmsCore would only send a duplicate if it missed
     * the earlier ack.
     */
    Task<Void> ackTask = ackMessage(messageId);

    if (!alreadyReceivedMessage(messageId)) {
      passMessageIntentToSdk(intent);
    }

    // The ack has probably already been sent, but to ensure it has a chance before we finish the
    // service or broadcast receiver block for a bit. Sending the ack should be very fast as all it
    // does is bind to GmsCore and send a single message over the binder.
    try {
      Tasks.await(ackTask, getAckTimeoutMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      // Just ignore any error as there isn't much we can do
      Log.w(TAG, "Message ack failed: " + e);
    }
  }

  private void passMessageIntentToSdk(Intent intent) {
    String messageType = intent.getStringExtra(MessagePayloadKeys.MESSAGE_TYPE);
    if (messageType == null) {
      messageType = MessageTypes.MESSAGE;
    }
    switch (messageType) {
      case MessageTypes.MESSAGE:
        if (MessagingAnalytics.shouldUploadScionMetrics(intent)) {
          MessagingAnalytics.logNotificationReceived(intent);
        }

        dispatchMessage(intent);
        break;
      case MessageTypes.DELETED:
        onDeletedMessages();
        break;
      case MessageTypes.SEND_EVENT:
        onMessageSent(intent.getStringExtra(MessagePayloadKeys.MSGID));
        break;
      case MessageTypes.SEND_ERROR:
        onSendError(
            getMessageId(intent),
            new SendException(intent.getStringExtra(Constants.IPC_BUNDLE_KEY_SEND_ERROR)));
        break;
      default:
        Log.w(TAG, "Received message with unknown type: " + messageType);
        break;
    }
  }

  /** Dispatch a message to the app's onMessageReceived method, or show a notification */
  private void dispatchMessage(Intent intent) {
    Bundle data = intent.getExtras();
    if (data == null) {
      // The intent should always have at least one extra so this shouldn't be null, but
      // this is the easiest way to handle the case where it does happen.
      data = new Bundle();
    }
    // First remove any parameters that shouldn't be passed to the app
    // * The wakelock ID set by the WakefulBroadcastReceiver
    data.remove("androidx.content.wakelockid");
    if (NotificationParams.isNotification(data)) {
      NotificationParams params = new NotificationParams(data);

      ExecutorService executor = FcmExecutors.newNetworkIOExecutor();
      DisplayNotification displayNotification = new DisplayNotification(this, params, executor);
      try {
        if (displayNotification.handleNotification()) {
          // Notification was shown or it was a fake notification, finish
          return;
        }
      } finally {
        // Ensures any executor threads are cleaned up
        executor.shutdown();
      }

      // App is in the foreground, log and pass through to onMessageReceived below
      if (MessagingAnalytics.shouldUploadScionMetrics(intent)) {
        MessagingAnalytics.logNotificationForeground(intent);
      }
    }
    onMessageReceived(new RemoteMessage(data));
  }

  private boolean alreadyReceivedMessage(String messageId) {
    if (TextUtils.isEmpty(messageId)) {
      return false;
    }
    if (recentlyReceivedMessageIds.contains(messageId)) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Received duplicate message: " + messageId);
      }
      return true;
    }
    // Add this message ID to the queue
    if (recentlyReceivedMessageIds.size() >= RECENTLY_RECEIVED_MESSAGE_IDS_MAX_SIZE) {
      recentlyReceivedMessageIds.remove();
    }
    recentlyReceivedMessageIds.add(messageId);
    return false;
  }

  private Task<Void> ackMessage(String messageId) {
    if (TextUtils.isEmpty(messageId)) {
      return Tasks.forResult(null /* void */);
    }
    Bundle data = new Bundle();
    data.putString(MessagePayloadKeys.MSGID, messageId);
    return MessengerIpcClient.getInstance(this)
        .sendOneWayRequest(MessengerIpcClient.What.FCM_ACK, data);
  }

  private String getMessageId(Intent intent) {
    String messageId = intent.getStringExtra(MessagePayloadKeys.MSGID);
    if (messageId == null) {
      messageId = intent.getStringExtra(MessagePayloadKeys.MSGID_SERVER);
    }
    return messageId;
  }

  @VisibleForTesting
  static void resetForTesting() {
    recentlyReceivedMessageIds.clear();
  }

  /** Provided so tests can override if necessary. */
  @VisibleForTesting
  long getAckTimeoutMillis() {
    return TimeUnit.SECONDS.toMillis(1);
  }
}
