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

import static com.google.firebase.messaging.FcmExecutors.newTopicsSyncExecutor;
import static com.google.firebase.messaging.FcmExecutors.newTopicsSyncTriggerExecutor;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.Metadata;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.UserAgentPublisher;

/**
 * Top level <a href="https://firebase.google.com/docs/cloud-messaging/">Firebase Cloud
 * Messaging</a> singleton that provides methods for subscribing to topics and sending upstream
 * messages.
 *
 * <p>In order to receive Firebase messages, declare an implementation of {@link
 * FirebaseMessagingService} in the app manifest. To process messages, override base class methods
 * to handle any events required by the application.
 *
 * <p>Client apps can send <a
 * href="https://firebase.google.com/docs/cloud-messaging/android/upstream">upstream messages</a>
 * back to the app server using the XMPP-based Cloud Connection Server. For example:
 *
 * <pre>
 * FirebaseMessaging.getInstance().send(
 *     new RemoteMessage.Builder(SENDER_ID + "&#64;gcm.googleapis.com")
 *     .setMessageId(id)
 *     .addData("key", "value")
 *     .build());</pre>
 */
public class FirebaseMessaging {
  // Usually we would use the constant in GoogleApiAvailability but we don't depend on 'base'
  // so we duplicate the constant to avoid one more dependency.
  private static final String GMS_PACKAGE = "com.google.android.gms";
  private static final String SEND_INTENT_ACTION = "com.google.android.gcm.intent.SEND";
  private static final String EXTRA_DUMMY_P_INTENT = "app";

  /**
   * Specifies scope used in obtaining a registration token when calling {@link
   * FirebaseInstanceId#getToken}
   */
  public static final String INSTANCE_ID_SCOPE = "FCM";

  private final Context context;
  private final FirebaseInstanceId iid;
  private final Task<TopicsSubscriber> topicsSubscriberTask;

  @Nullable
  @SuppressLint(
      "FirebaseUnknownNullness") // Checktest wasn't recognizing @Nullable nor @NonNull annotations.
  @VisibleForTesting
  static TransportFactory transportFactory;

  @NonNull
  public static synchronized FirebaseMessaging getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /** @hide */
  @Keep
  @NonNull
  static synchronized FirebaseMessaging getInstance(@NonNull FirebaseApp firebaseApp) {
    return firebaseApp.get(FirebaseMessaging.class);
  }

  FirebaseMessaging(
      FirebaseApp firebaseApp,
      FirebaseInstanceId iid,
      UserAgentPublisher userAgentPublisher,
      HeartBeatInfo heartBeatInfo,
      FirebaseInstallationsApi firebaseInstallationsApi,
      @Nullable TransportFactory transportFactory) {

    FirebaseMessaging.transportFactory = transportFactory;

    this.iid = iid;
    context = firebaseApp.getApplicationContext();

    topicsSubscriberTask =
        TopicsSubscriber.createInstance(
            firebaseApp,
            iid,
            new Metadata(context),
            userAgentPublisher,
            heartBeatInfo,
            firebaseInstallationsApi,
            context,
            /* syncExecutor= */ newTopicsSyncExecutor());

    // Upon FCM instantiation, a short lived thread, managed by a single-threaded executor, will be
    // spawn to trigger a topic sync. The actual file IO is then offloaded to
    // ScheduledThreadPoolExecutor `syncExecutor`.
    topicsSubscriberTask.addOnSuccessListener(
        newTopicsSyncTriggerExecutor(),
        topicsSubscriber -> {
          // Topics operations relay on IID for token generation, thus the sync is also
          // subject to an auto-init check.
          if (isAutoInitEnabled()) {
            topicsSubscriber.startTopicsSyncIfNecessary();
          }
        });
  }

  /**
   * Determines whether FCM auto-initialization is enabled or disabled.
   *
   * @return true if auto-init is enabled and false if auto-init is disabled
   */
  public boolean isAutoInitEnabled() {
    return iid.isFcmAutoInitEnabled();
  }

  /**
   * Enables or disables auto-initialization of Firebase Cloud Messaging.
   *
   * <p>When enabled, Firebase Cloud Messaging generates a registration token on app startup if
   * there is no valid one and generates a new token when it is deleted (which prevents {@link
   * FirebaseInstanceId#deleteInstanceId} from stopping the periodic sending of data). This setting
   * is persisted across app restarts and overrides the setting specified in your manifest.
   *
   * <p>By default, Firebase Cloud Messaging auto-initialization is enabled. If you need to change
   * the default, (for example, because you want to prompt the user before Firebase Cloud Messaging
   * generates/refreshes a registration token on app startup), add to your application’s manifest:
   *
   * <pre>{@code
   * <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />
   * }</pre>
   *
   * @param enable Whether Firebase Cloud Messaging should auto-initialize.
   */
  public void setAutoInitEnabled(boolean enable) {
    iid.setFcmAutoInitEnabled(enable);
  }

  /**
   * Determines whether Firebase Cloud Messaging exports message delivery metrics to BigQuery.
   *
   * @return true if Firebase Cloud Messaging exports message delivery metrics to BigQuery.
   * @deprecated Use {@link #isDeliveryMetricsExportToBigQueryEnabled()} instead.
   */
  @Deprecated
  public boolean deliveryMetricsExportToBigQueryEnabled() {
    return isDeliveryMetricsExportToBigQueryEnabled();
  }

  /**
   * Determines whether Firebase Cloud Messaging exports message delivery metrics to BigQuery.
   *
   * @return true if Firebase Cloud Messaging exports message delivery metrics to BigQuery.
   */
  public boolean isDeliveryMetricsExportToBigQueryEnabled() {
    return MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled();
  }

  /**
   * Enables or disables Firebase Cloud Messaging message delivery metrics export to BigQuery.
   *
   * <p>By default, message delivery metrics are not exported to BigQuery. Use this method to enable
   * or disable the export at runtime. In addition, you can enable the export by adding to your
   * manifest. Note that the run-time method call will override the manifest value.
   *
   * <pre>{@code
   * <meta-data android:name= "delivery_metrics_exported_to_big_query_enabled"
   * android:value="true"/>
   * }</pre>
   *
   * @param enable Whether Firebase Cloud Messaging should export message delivery metrics to
   *     BigQuery.
   */
  public void setDeliveryMetricsExportToBigQuery(boolean enable) {
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(enable);
  }

  /**
   * Subscribes to {@code topic} in the background.
   *
   * <p>The subscribe operation is persisted and will be retried until successful.
   *
   * <p>This uses a Firebase Instance ID token to identify the app instance and periodically sends
   * data to the Firebase backend. To stop this, see {@link FirebaseInstanceId#deleteInstanceId}.
   *
   * @param topic The name of the topic to subscribe. Must match the following regular expression:
   *     "[a-zA-Z0-9-_.~%]{1,900}".
   * @return A task that will be completed when the topic has been successfully subscribed to.
   */
  @NonNull
  public Task<Void> subscribeToTopic(@NonNull String topic) {
    return topicsSubscriberTask.onSuccessTask(
        topicsSubscriber -> topicsSubscriber.subscribeToTopic(topic));
  }

  /**
   * Unsubscribes from {@code topic} in the background.
   *
   * <p>The unsubscribe operation is persisted and will be retried until successful.
   *
   * <p>This does not stop {@code FirebaseInstanceId}'s periodic sending of data started by {@link
   * #subscribeToTopic}. To stop this, see {@link FirebaseInstanceId#deleteInstanceId}.
   *
   * @param topic The name of the topic to unsubscribe from. Must match the following regular
   *     expression: "[a-zA-Z0-9-_.~%]{1,900}".
   * @return A task that will be completed when the topic has been successfully unsubscribed from.
   */
  @NonNull
  public Task<Void> unsubscribeFromTopic(@NonNull String topic) {
    return topicsSubscriberTask.onSuccessTask(
        topicsSubscriber -> topicsSubscriber.unsubscribeFromTopic(topic));
  }

  /**
   * Sends {@code message} upstream to your app server.
   *
   * <p>When there is an active connection the message will be sent immediately, otherwise the
   * message will be queued up to the time to live (TTL) set in the message.
   */
  public void send(@NonNull RemoteMessage message) {
    if (TextUtils.isEmpty(message.getTo())) {
      throw new IllegalArgumentException("Missing 'to'");
    }

    Intent intent = new Intent(SEND_INTENT_ACTION);

    // dummy pending-intent for package-name verification
    // (fill in the package, to prevent the intent from being used)
    Intent dummyIntent = new Intent();
    dummyIntent.setPackage("com.google.example.invalidpackage");
    intent.putExtra(EXTRA_DUMMY_P_INTENT, PendingIntent.getBroadcast(context, 0, dummyIntent, 0));

    intent.setPackage(GMS_PACKAGE);
    message.populateSendMessageIntent(intent);

    // Signature permission required.
    context.sendOrderedBroadcast(
        intent, "com.google.android.gtalkservice.permission.GTALK_SERVICE");
  }

  /** @hide */
  Task<TopicsSubscriber> getTopicsSubscriberTask() {
    return topicsSubscriberTask;
  }

  /** @hide */
  @Nullable
  public static TransportFactory getTransportFactory() {
    return transportFactory;
  }

  /** @hide */
  static void clearTransportFactoryForTest() {
    transportFactory = null;
  }
}
