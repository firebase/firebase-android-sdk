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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.ProductData;
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.messaging.Constants.AnalyticsKeys;
import com.google.firebase.messaging.Constants.FirelogAnalytics;
import com.google.firebase.messaging.Constants.FirelogAnalytics.MessagePriority;
import com.google.firebase.messaging.Constants.MessagePayloadKeys;
import com.google.firebase.messaging.Constants.ScionAnalytics;
import com.google.firebase.messaging.reporting.MessagingClientEvent;
import com.google.firebase.messaging.reporting.MessagingClientEventExtension;
import java.util.concurrent.ExecutionException;

/**
 * Provides integration between FCM and Scion.
 *
 * <p>All Scion dependencies should be kept in this class, and missing dependencies should be
 * handled gracefully. Instead of crashing the app if Scion has not been included in the APK we
 * instead log a message and don't send any events.
 *
 * <p>key/values expected by GcmAnalytics: (constants are defined in {@link Constants})
 *
 * <ul>
 *   <li>google.c.a.e = 1 # Enable Analytics
 *   <li>google.c.a.c_id = 123 # Composer Id
 *   <li>google.c.a.c_l = Campaign1 # Composer Label
 *   <li>google.c.a.ts = 1234 # Timestamp of message
 *   <li>google.c.a.udt = 1 # Whether the ts should be used only for DateTime, without timezone
 *   <li>google.c.a.m_l = dev-provided-label # message label provided by the developer
 * </ul>
 *
 * @hide
 */
public class MessagingAnalytics {
  private static final String REENGAGEMENT_SOURCE = "Firebase";
  private static final String REENGAGEMENT_MEDIUM = "notification";

  private static final String FCM_PREFERENCES = "com.google.firebase.messaging";
  private static final String DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF = "export_to_big_query";
  private static final String MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED =
      "delivery_metrics_exported_to_big_query_enabled";

  private static final int DEFAULT_PRODUCT_ID = 111881503;

  /** Log that a notification was received by the client app. */
  public static void logNotificationReceived(Intent intent) {
    if (shouldUploadScionMetrics(intent)) {
      logToScion(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE, intent.getExtras());
    }

    if (shouldUploadFirelogAnalytics(intent)) {
      logToFirelog(
          MessagingClientEvent.Event.MESSAGE_DELIVERED,
          intent,
          FirebaseMessaging.getTransportFactory());
    }
  }

  /** Log that a notification was opened. */
  public static void logNotificationOpen(Bundle extras) {
    setUserPropertyIfRequired(extras);
    logToScion(ScionAnalytics.EVENT_NOTIFICATION_OPEN, extras);
  }

  /** Log that a notification was dismissed. */
  public static void logNotificationDismiss(Intent intent) {
    logToScion(ScionAnalytics.EVENT_NOTIFICATION_DISMISS, intent.getExtras());
  }

  /**
   * Log that a notification was received while the app was in the foreground.
   *
   * <p>In this case, onMessageReceived() is invoked instead of showing a notification to the user.
   */
  public static void logNotificationForeground(Intent intent) {
    logToScion(ScionAnalytics.EVENT_NOTIFICATION_FOREGROUND, intent.getExtras());
  }

  /** check whether we should upload metrics data to scion. */
  public static boolean shouldUploadScionMetrics(Intent intent) {
    if (intent == null || isDirectBootMessage(intent)) {
      return false;
    }

    return shouldUploadScionMetrics(intent.getExtras());
  }

  /** check whether we should upload metrics data to scion. */
  public static boolean shouldUploadScionMetrics(Bundle extras) {
    if (extras == null) {
      return false;
    }

    return "1".equals(extras.getString(Constants.AnalyticsKeys.ENABLED));
  }

  /** check whether we should upload metrics data to firelog. */
  public static boolean shouldUploadFirelogAnalytics(Intent intent) {
    if (intent == null || isDirectBootMessage(intent)) {
      return false;
    }
    return deliveryMetricsExportToBigQueryEnabled();
  }

  private static boolean isDirectBootMessage(Intent intent) {
    // Analytics causes a crash in direct boot mode since FirebaseApp is not initialized
    // (and probably shouldn't be initialized since it may not be direct boot
    // compatible).
    return FirebaseMessagingService.ACTION_DIRECT_BOOT_REMOTE_INTENT.equals(intent.getAction());
  }

  /**
   * check whether we should upload metrics data to firelog by looking at the user setting in the
   * manifest and device storage.
   */
  static boolean deliveryMetricsExportToBigQueryEnabled() {
    try {
      FirebaseApp.getInstance();
    } catch (IllegalStateException e) {
      Log.i(
          TAG,
          "FirebaseApp has not being initialized. Device might be in direct boot mode."
              + " Skip exporting delivery metrics to Big Query");
      return false;
    }

    Context context = FirebaseApp.getInstance().getApplicationContext();
    SharedPreferences preferences =
        context.getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE);

    // Flag value set at runtime overrides manifest setting
    if (preferences.contains(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF)) {
      return preferences.getBoolean(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, false);
    }

    // After checking device db, check manifest
    try {
      PackageManager packageManager = context.getPackageManager();
      if (packageManager != null) {
        ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                context.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo != null
            && applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(
                MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED)) {
          return applicationInfo.metaData.getBoolean(
              MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // Shouldn't happen. If happened, fall though to default.
    }

    // If there is no specification on disk nor in manifest, default to not logging
    return false;
  }

  /** Set the FIREBASE_LAST_NOTIFICATION user-property in Scion for conversion tracking. */
  private static void setUserPropertyIfRequired(Bundle extras) {
    if (extras == null) {
      return;
    }

    // If the user requested to track conversions, set the user property.
    String shouldTrackConversions = extras.getString(Constants.AnalyticsKeys.TRACK_CONVERSIONS);
    if ("1".equals(shouldTrackConversions)) {
      // TODO(b/78465387) Use components dependency framework to get analyticsConnector obj
      @SuppressWarnings("FirebaseUseExplicitDependencies")
      AnalyticsConnector analytics = FirebaseApp.getInstance().get(AnalyticsConnector.class);
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(
            TAG,
            "Received event with track-conversion=true."
                + " Setting user property and reengagement event");
      }
      if (analytics != null) {
        String composerId = extras.getString(Constants.AnalyticsKeys.COMPOSER_ID);
        analytics.setUserProperty(
            ScionAnalytics.ORIGIN_FCM,
            ScionAnalytics.USER_PROPERTY_FIREBASE_LAST_NOTIFICATION,
            composerId);

        // Also set the reengagement attribution
        Bundle params = new Bundle();
        params.putString(ScionAnalytics.PARAM_SOURCE, REENGAGEMENT_SOURCE);
        params.putString(ScionAnalytics.PARAM_MEDIUM, REENGAGEMENT_MEDIUM);
        params.putString(ScionAnalytics.PARAM_CAMPAIGN, composerId);
        analytics.logEvent(
            ScionAnalytics.ORIGIN_FCM, ScionAnalytics.EVENT_FIREBASE_CAMPAIGN, params);
      } else {
        // Client did not include the measurement library
        Log.w(
            TAG,
            "Unable to set user property for conversion tracking: "
                + " analytics library is missing");
      }
    } else {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Received event with track-conversion=false. Do not set user property");
      }
    }
  }

  /**
   * Asynchronously log an event to Scion.
   *
   * <p>Scion schedules tasks to run on worker threads within the client app to send the event.
   */
  @VisibleForTesting
  static void logToScion(String event, Bundle extras) {
    try {
      FirebaseApp.getInstance();
    } catch (IllegalStateException e) {
      Log.e(TAG, "Default FirebaseApp has not been initialized. Skip logging event to GA.");
      return;
    }

    if (extras == null) {
      extras = new Bundle();
    }

    Bundle scionPayload = new Bundle();

    String composerId = getComposerId(extras);
    if (composerId != null) {
      scionPayload.putString(ScionAnalytics.PARAM_COMPOSER_ID, composerId);
    }

    String composerLabel = getComposerLabel(extras);
    if (composerLabel != null) {
      scionPayload.putString(ScionAnalytics.PARAM_MESSAGE_NAME, composerLabel);
    }

    String messageLabel = getMessageLabel(extras);
    if (!TextUtils.isEmpty(messageLabel)) {
      scionPayload.putString(ScionAnalytics.PARAM_LABEL, messageLabel);
    }

    String messageChannel = getMessageChannel(extras);
    if (!TextUtils.isEmpty(messageChannel)) {
      scionPayload.putString(ScionAnalytics.PARAM_MESSAGE_CHANNEL, messageChannel);
    }

    String topic = getTopic(extras);
    if (topic != null) {
      scionPayload.putString(ScionAnalytics.PARAM_TOPIC, topic);
    }

    String messageTime = getMessageTime(extras);
    if (messageTime != null) {
      try {
        scionPayload.putInt(ScionAnalytics.PARAM_MESSAGE_TIME, Integer.parseInt(messageTime));
      } catch (NumberFormatException e) {
        Log.w(TAG, "Error while parsing timestamp in GCM event", e);
      }
    }

    String useDeviceTime = getUseDeviceTime(extras);
    if (useDeviceTime != null) {
      try {
        scionPayload.putInt(
            ScionAnalytics.PARAM_MESSAGE_DEVICE_TIME, Integer.parseInt(useDeviceTime));
      } catch (NumberFormatException e) {
        Log.w(TAG, "Error while parsing use_device_time in GCM event", e);
      }
    }

    String messageType = getMessageTypeForScion(extras);
    if (ScionAnalytics.EVENT_NOTIFICATION_RECEIVE.equals(event)
        || ScionAnalytics.EVENT_NOTIFICATION_FOREGROUND.equals(event)) {
      scionPayload.putString(ScionAnalytics.PARAM_MESSAGE_TYPE, messageType);
    }

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Logging to scion event=" + event + " scionPayload=" + scionPayload);
    }

    // TODO(b/78465387) Use components dependency framework to get analyticsConnector obj
    @SuppressWarnings("FirebaseUseExplicitDependencies")
    AnalyticsConnector analytics = FirebaseApp.getInstance().get(AnalyticsConnector.class);
    if (analytics != null) {
      analytics.logEvent(ScionAnalytics.ORIGIN_FCM, event, scionPayload);
    } else {
      // Client did not include the measurement library
      Log.w(TAG, "Unable to log event: analytics library is missing");
    }
  }

  /**
   * Asynchronously log an event to Firelog.
   *
   * <p>Firelog batch-send usually delivery metrics within 1 hour (at most 24 hours) when the device
   * is idle and have network connection in a background thread.
   */
  private static void logToFirelog(
      MessagingClientEvent.Event event,
      Intent intent,
      @Nullable TransportFactory transportFactory) {
    if (transportFactory == null) {
      Log.e(TAG, "TransportFactory is null. Skip exporting message delivery metrics to Big Query");
      return;
    }
    MessagingClientEvent clientEvent = eventToProto(event, intent);
    if (clientEvent == null) {
      return;
    }

    try {
      // TODO(b/145299499): offload encoding to Firelog Thread
      ProductData productData =
          ProductData.withProductId(
              intent.getIntExtra(MessagePayloadKeys.PRODUCT_ID, DEFAULT_PRODUCT_ID));
      transportFactory
          .getTransport(
              FirelogAnalytics.FCM_LOG_SOURCE,
              MessagingClientEventExtension.class,
              Encoding.of("proto"),
              MessagingClientEventExtension::toByteArray)
          .send(
              Event.ofData(
                  MessagingClientEventExtension.newBuilder()
                      .setMessagingClientEvent(clientEvent)
                      .build(),
                  productData));
    } catch (RuntimeException e) {
      Log.w(TAG, "Failed to send big query analytics payload.", e);
    }
  }

  static void setDeliveryMetricsExportToBigQuery(boolean enable) {
    SharedPreferences.Editor editor =
        FirebaseApp.getInstance()
            .getApplicationContext()
            .getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE)
            .edit();
    editor.putBoolean(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, enable).apply();
  }

  @NonNull
  static int getTtl(Bundle extras) {
    Object ttl = extras.get(MessagePayloadKeys.TTL);
    if (ttl instanceof Integer) {
      return (int) ttl;
    } else if (ttl instanceof String) {
      try {
        return Integer.parseInt((String) ttl);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Invalid TTL: " + ttl);
        // Fall through to default case
      }
    }
    return 0; // Default if unset or an error
  }

  @Nullable
  static String getCollapseKey(Bundle extras) {
    return extras.getString(MessagePayloadKeys.COLLAPSE_KEY);
  }

  @Nullable
  static String getComposerId(Bundle extras) {
    return extras.getString(AnalyticsKeys.COMPOSER_ID);
  }

  @Nullable
  static String getComposerLabel(Bundle extras) {
    return extras.getString(AnalyticsKeys.COMPOSER_LABEL);
  }

  @Nullable
  static String getMessageLabel(Bundle extras) {
    return extras.getString(AnalyticsKeys.MESSAGE_LABEL);
  }

  @Nullable
  static String getMessageChannel(Bundle extras) {
    return extras.getString(AnalyticsKeys.MESSAGE_CHANNEL);
  }

  @Nullable
  static String getMessageTime(Bundle extras) {
    return extras.getString(AnalyticsKeys.MESSAGE_TIMESTAMP);
  }

  @Nullable
  static String getMessageId(Bundle extras) {
    String messageId = extras.getString(MessagePayloadKeys.MSGID);
    if (messageId == null) {
      messageId = extras.getString(MessagePayloadKeys.MSGID_SERVER);
    }
    return messageId;
  }

  @NonNull
  static String getPackageName() {
    return FirebaseApp.getInstance().getApplicationContext().getPackageName();
  }

  @NonNull
  static String getInstanceId(Bundle extras) {
    String to = extras.getString(MessagePayloadKeys.TO);
    if (!TextUtils.isEmpty(to)) {
      return to;
    }
    try {
      return Tasks.await(FirebaseInstallations.getInstance(FirebaseApp.getInstance()).getId());
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @NonNull
  static String getMessageTypeForScion(Bundle extras) {
    return extras != null && NotificationParams.isNotification(extras)
        ? ScionAnalytics.MessageType.DISPLAY_NOTIFICATION
        : ScionAnalytics.MessageType.DATA_MESSAGE;
  }

  @NonNull
  static MessagingClientEvent.MessageType getMessageTypeForFirelog(Bundle extras) {
    return extras != null && NotificationParams.isNotification(extras)
        ? MessagingClientEvent.MessageType.DISPLAY_NOTIFICATION
        : MessagingClientEvent.MessageType.DATA_MESSAGE;
  }

  @Nullable
  static String getTopic(Bundle extras) {
    String from = extras.getString(MessagePayloadKeys.FROM);
    return ((from != null && from.startsWith("/topics/")) ? from : null);
  }

  @Nullable
  static String getUseDeviceTime(Bundle extras) {
    if (extras.containsKey(AnalyticsKeys.MESSAGE_USE_DEVICE_TIME)) {
      return extras.getString(AnalyticsKeys.MESSAGE_USE_DEVICE_TIME);
    }
    return null;
  }

  @NonNull
  static int getPriority(Bundle extras) {
    String priority = extras.getString(MessagePayloadKeys.DELIVERED_PRIORITY);
    if (priority == null) {
      if ("1".equals(extras.getString(MessagePayloadKeys.PRIORITY_REDUCED_V19))) {
        return RemoteMessage.PRIORITY_NORMAL;
      }
      priority = extras.getString(MessagePayloadKeys.PRIORITY_V19);
    }
    return getMessagePriority(priority);
  }

  @NonNull
  private static int getMessagePriority(String priority) {
    if ("high".equals(priority)) {
      return RemoteMessage.PRIORITY_HIGH;
    } else if ("normal".equals(priority)) {
      return RemoteMessage.PRIORITY_NORMAL;
    } else {
      return RemoteMessage.PRIORITY_UNKNOWN;
    }
  }

  @MessagePriority
  static int getMessagePriorityForFirelog(Bundle extras) {
    // translate from the RemoteMessage-flavored priority integers into the ones we use for backend
    // logging
    int priority = getPriority(extras);
    if (priority == RemoteMessage.PRIORITY_NORMAL) {
      return MessagePriority.NORMAL;
    } else if (priority == RemoteMessage.PRIORITY_HIGH) {
      return MessagePriority.HIGH;
    } else {
      return MessagePriority.UNKNOWN;
    }
  }

  @Nullable
  static long getProjectNumber(Bundle extras) {
    if (extras.containsKey(MessagePayloadKeys.SENDER_ID)) {
      // Sender ID was sent with the message, return that.
      try {
        return Long.parseLong(extras.getString(MessagePayloadKeys.SENDER_ID));
      } catch (NumberFormatException ex) {
        Log.w(TAG, "error parsing project number", ex);
      }
    }

    // Sender ID was not included with the message, get it from the FirebaseApp.
    FirebaseApp app = FirebaseApp.getInstance();
    // Check for an explicit sender id
    String senderId = app.getOptions().getGcmSenderId();
    if (senderId != null) {
      try {
        return Long.parseLong(senderId);
      } catch (NumberFormatException ex) {
        Log.w(TAG, "error parsing sender ID", ex);
      }
    }

    String appId = app.getOptions().getApplicationId();
    if (!appId.startsWith("1:")) {
      // Not v1, server should be updated to accept the full app ID now
      try {
        return Long.parseLong(appId);
      } catch (NumberFormatException ex) {
        Log.w(TAG, "error parsing app ID", ex);
      }
    } else {
      // For v1 app IDs, fall back to parsing the project ID out
      String[] parts = appId.split(":");
      if (parts.length < 2) {
        return 0L; // Invalid format
      }
      String projectId = parts[1];
      if (projectId.isEmpty()) {
        return 0L; // No project ID
      }

      try {
        return Long.parseLong(projectId);
      } catch (NumberFormatException ex) {
        Log.w(TAG, "error parsing app ID", ex);
      }
    }

    return 0L;
  }

  static MessagingClientEvent eventToProto(MessagingClientEvent.Event event, Intent intent) {
    if (intent == null) {
      return null;
    }
    Bundle extras = intent.getExtras();
    if (extras == null) {
      // even if no params were passed, we still grab some default values so don't bail just yet
      extras = Bundle.EMPTY;
    }

    MessagingClientEvent.Builder builder =
        MessagingClientEvent.newBuilder()
            .setTtl(getTtl(extras))
            .setEvent(event)
            .setInstanceId(getInstanceId(extras))
            .setPackageName(getPackageName())
            .setSdkPlatform(MessagingClientEvent.SDKPlatform.ANDROID)
            .setMessageType(getMessageTypeForFirelog(extras));

    // nullable parameters
    String messageId = getMessageId(extras);
    if (messageId != null) { // shouldn't happen in prod
      builder.setMessageId(messageId);
    }

    String topic = getTopic(extras);
    if (topic != null) {
      builder.setTopic(topic);
    }

    String collapseKey = getCollapseKey(extras);
    if (collapseKey != null) {
      builder.setCollapseKey(collapseKey);
    }

    String messageLabel = getMessageLabel(extras);
    if (messageLabel != null) {
      builder.setAnalyticsLabel(messageLabel);
    }

    String composerLabel = getComposerLabel(extras);
    if (composerLabel != null) {
      builder.setComposerLabel(composerLabel);
    }

    long projectNumber = getProjectNumber(extras);
    if (projectNumber > 0) {
      builder.setProjectNumber(projectNumber);
    }

    return builder.build();
  }
}
