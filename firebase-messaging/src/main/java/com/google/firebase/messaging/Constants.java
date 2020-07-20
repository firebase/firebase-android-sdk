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

import static java.util.concurrent.TimeUnit.MINUTES;

import android.os.Bundle;
import androidx.annotation.StringDef;
import androidx.collection.ArrayMap;

/**
 * List of constants, separated from their logic to be shared more easily with Google Play services.
 *
 * @hide
 */
public final class Constants {
  public static final String TAG = "FirebaseMessaging";
  public static final String FCM_WAKE_LOCK = "wake:com.google.firebase.messaging";
  // Give the worker thread plenty of time to do the topics IO tasks.
  public static final long WAKE_LOCK_ACQUIRE_TIMEOUT_MILLIS = MINUTES.toMillis(3);

  /** Only sent by GmsCore for send error events */
  public static final String IPC_BUNDLE_KEY_SEND_ERROR = "error";

  /** Values for the MessagePayloadKeys.MESSAGE_TYPE key */
  public static final class MessageTypes {
    /** Standard message */
    public static final String MESSAGE = "gcm";

    /** Dirty ping */
    public static final String DELETED = "deleted_messages";

    /** Message acked by MCS */
    public static final String SEND_EVENT = "send_event";

    /** Message failed to send */
    public static final String SEND_ERROR = "send_error";

    // don't instantiate me.
    private MessageTypes() {}
  }

  /** Actions used by analytics broadcasts. */
  public static final class IntentActionKeys {
    public static final String NOTIFICATION_OPEN =
        "com.google.firebase.messaging.NOTIFICATION_OPEN";
    public static final String NOTIFICATION_DISMISS =
        "com.google.firebase.messaging.NOTIFICATION_DISMISS";

    public static final String MESSAGING_EVENT = "com.google.firebase.MESSAGING_EVENT";

    // don't instantiate me.
    private IntentActionKeys() {}
  }

  /** Keys used by the client library to pass Intents within Bundles. */
  public static final class IntentKeys {
    public static final String PENDING_INTENT = "pending_intent";
    public static final String WRAPPED_INTENT = "wrapped_intent";

    // don't instantiate me.
    private IntentKeys() {}
  }

  /** Keys used by Google Play services in the bundle representing a Remote Message. */
  public static final class MessagePayloadKeys {
    /**
     * Special extras interspersed with the client's payload. Any extra prefixed with "google."
     * (case insensitive) that is in the proto message from the server will be stripped before
     * sending to the app except "google.c." which are intended for the client library.
     */
    public static final String RESERVED_PREFIX = "google.";

    public static final String FROM = "from";

    public static final String RAW_DATA = "rawData";

    public static final String MESSAGE_TYPE = "message_type";

    public static final String COLLAPSE_KEY = "collapse_key";

    /**
     * Only sent by the server, GmsCore sends MSGID below. Server can't send that as it would get
     * stripped by the client.
     */
    public static final String MSGID_SERVER = "message_id";

    /** Used by upstream, but would be stripped by GmsCore if sent from server */
    public static final String TO = RESERVED_PREFIX + "to";

    /** Used by upstream, and set by GmsCore. */
    public static final String MSGID = RESERVED_PREFIX + "message_id";

    /** Used by upstream, and set by GmsCore. */
    public static final String TTL = RESERVED_PREFIX + "ttl";

    /** Set by GmsCore. */
    public static final String SENT_TIME = RESERVED_PREFIX + "sent_time";

    /** Original priority of message. Set by GmsCore. */
    public static final String ORIGINAL_PRIORITY = RESERVED_PREFIX + "original_priority";

    /** Priority of message. May be reduced due to exceeding its quota. */
    public static final String DELIVERED_PRIORITY = RESERVED_PREFIX + "delivered_priority";

    /** Original priority of message. */
    public static final String PRIORITY_V19 = RESERVED_PREFIX + "priority";

    /**
     * Extra set in the broadcast intent if the message had its priority reduced due to exceeding
     * its quota. Set by GmsCore.
     */
    public static final String PRIORITY_REDUCED_V19 = RESERVED_PREFIX + "priority_reduced";

    /**
     * Prefix "google.c" for keys that are reserved for the client library. While all the other
     * "google." are removed at GmsCore level, google.c keys are preserved in the intent extras, and
     * should be removed by the Gcm Client Library, before passing the user data to the app.
     */
    public static final String RESERVED_CLIENT_LIB_PREFIX = RESERVED_PREFIX + "c.";

    /** Sender ID of message. */
    public static final String SENDER_ID = RESERVED_CLIENT_LIB_PREFIX + "sender.id";

    public static ArrayMap<String, String> extractDeveloperDefinedPayload(Bundle bundle) {
      ArrayMap<String, String> data = new ArrayMap<>();
      for (String key : bundle.keySet()) {
        Object value = bundle.get(key);
        if (value instanceof String) {
          String stringValue = (String) value;
          // Add all bundle members except those with a reserved prefix and non
          // data values typically exposed through other messages.
          if (!key.startsWith(MessagePayloadKeys.RESERVED_PREFIX)
              && !key.startsWith(MessageNotificationKeys.RESERVED_PREFIX)
              && !key.equals(MessagePayloadKeys.FROM)
              && !key.equals(MessagePayloadKeys.MESSAGE_TYPE)
              && !key.equals(MessagePayloadKeys.COLLAPSE_KEY)) {
            data.put(key, stringValue);
          }
        }
      }
      return data;
    }

    // don't instantiate me.
    private MessagePayloadKeys() {}
  }

  /**
   * Keys used by Google Play services in bundle representing a Remote Message, to describe a
   * Notification that should be rendered by the client.
   */
  public static final class MessageNotificationKeys {

    public static final String RESERVED_PREFIX = "gcm.";

    public static final String NOTIFICATION_PREFIX = RESERVED_PREFIX + "n.";

    // TODO(morepork) Remove this once the server is updated to only use the new prefix
    public static final String NOTIFICATION_PREFIX_OLD = RESERVED_PREFIX + "notification.";

    /** Parameter to "enable" the display notification */
    public static final String ENABLE_NOTIFICATION = NOTIFICATION_PREFIX + "e";

    /**
     * Parameter to disable Android Q's "proxying" feature. Notifications with this set will never
     * be proxied.
     */
    public static final String DO_NOT_PROXY = NOTIFICATION_PREFIX + "dnp";

    /**
     * Parameter to make this into a fake notification that is only used for enabling analytics for
     * a control group. No notification is shown, nor any service callbacks. notification nor enable
     * any service callbacks.
     */
    public static final String NO_UI = NOTIFICATION_PREFIX + "noui";

    public static final String TITLE = NOTIFICATION_PREFIX + "title";
    public static final String BODY = NOTIFICATION_PREFIX + "body";
    public static final String ICON = NOTIFICATION_PREFIX + "icon";
    public static final String IMAGE_URL = NOTIFICATION_PREFIX + "image";
    public static final String TAG = NOTIFICATION_PREFIX + "tag";
    public static final String COLOR = NOTIFICATION_PREFIX + "color";
    public static final String TICKER = NOTIFICATION_PREFIX + "ticker";
    public static final String LOCAL_ONLY = NOTIFICATION_PREFIX + "local_only";
    public static final String STICKY = NOTIFICATION_PREFIX + "sticky";
    public static final String NOTIFICATION_PRIORITY =
        NOTIFICATION_PREFIX + "notification_priority";
    public static final String DEFAULT_SOUND = NOTIFICATION_PREFIX + "default_sound";
    public static final String DEFAULT_VIBRATE_TIMINGS =
        NOTIFICATION_PREFIX + "default_vibrate_timings";
    public static final String DEFAULT_LIGHT_SETTINGS =
        NOTIFICATION_PREFIX + "default_light_settings";
    public static final String NOTIFICATION_COUNT = NOTIFICATION_PREFIX + "notification_count";
    public static final String VISIBILITY = NOTIFICATION_PREFIX + "visibility";
    public static final String VIBRATE_TIMINGS = NOTIFICATION_PREFIX + "vibrate_timings";
    public static final String LIGHT_SETTINGS = NOTIFICATION_PREFIX + "light_settings";
    public static final String EVENT_TIME = NOTIFICATION_PREFIX + "event_time";

    /**
     * KEY_SOUND_2: can be null, "default" or the NAME of the R.raw.NAME resource to play. This key
     * has been added in Urda. Before Urda we used "sound" = null / "default"
     */
    public static final String SOUND_2 = NOTIFICATION_PREFIX + "sound2";

    // TODO(dgiorgini): clean SOUND/SOUND_2. Remove old key and rename current one.

    // FOR THE SERVER:
    //  - if sound is not provided : don't send anything
    //  - if sound is provided : send "sound2" = provided-string
    //                           AND send "sound" = "default" for backward compatibility < Urda

    /** DEPRECATED: use SOUND_2. this is used for backward compatibility < Urda */
    public static final String SOUND = NOTIFICATION_PREFIX + "sound";

    public static final String CLICK_ACTION = NOTIFICATION_PREFIX + "click_action";

    /** Deep link into the app that will be opened on click */
    public static final String LINK = NOTIFICATION_PREFIX + "link";

    /** Android override for the deep link */
    public static final String LINK_ANDROID = NOTIFICATION_PREFIX + "link_android";

    /** Android notification channel id */
    public static final String CHANNEL = NOTIFICATION_PREFIX + "android_channel_id";

    /**
     * For l10n of text parameters (e.g. title & body) a string resource can be specified instead of
     * a raw string. The name of that resource would be passed in the bundle under the key named:
     * <parameter> + suffix (e.g: _loc_key)
     */
    public static final String TEXT_RESOURCE_SUFFIX = "_loc_key";

    /**
     * For l10n of text parameters (e.g. title & body) a string containing the localization
     * parameters can be specified. This would be present in the bundle under the key named:
     * <parameter> + suffix (e.g: _loc_args)
     */
    public static final String TEXT_ARGS_SUFFIX = "_loc_args";

    // don't instantiate me.
    private MessageNotificationKeys() {}
  }

  /** Keys used for Analytics events. */
  public static final class AnalyticsKeys {
    public static final String PREFIX = MessagePayloadKeys.RESERVED_CLIENT_LIB_PREFIX + "a.";
    public static final String ENABLED = PREFIX + "e";
    public static final String COMPOSER_ID = PREFIX + "c_id";
    public static final String COMPOSER_LABEL = PREFIX + "c_l";
    public static final String MESSAGE_TIMESTAMP = PREFIX + "ts";
    public static final String MESSAGE_USE_DEVICE_TIME = PREFIX + "udt";
    public static final String TRACK_CONVERSIONS = PREFIX + "tc";
    public static final String ABT_EXPERIMENT = PREFIX + "abt";
    public static final String MESSAGE_LABEL = PREFIX + "m_l";
    public static final String MESSAGE_CHANNEL = PREFIX + "m_c";

    // don't instantiate me.
    private AnalyticsKeys() {}
  }

  /**
   * Constants used for Analytics events that are logged to Firelog, which eventually shows up in
   * the FCM Big Query result.
   *
   * @hide
   */
  public static final class FirelogAnalytics {
    public static final String PARAM_EVENT = "event";
    public static final String PARAM_MESSAGE_TYPE = "messageType";

    /**
     * Message Delivery Event. Need to be capitalized snake cases for backend to parse successfully
     */
    @StringDef({EventType.MESSAGE_DELIVERED})
    public @interface EventType {
      String MESSAGE_DELIVERED = "MESSAGE_DELIVERED";
    }

    /** Message type. Need to be capitalized snake cases for backend to parse successfully. */
    @StringDef({MessageType.DATA_MESSAGE, MessageType.DISPLAY_NOTIFICATION})
    public @interface MessageType {
      String DATA_MESSAGE = "DATA_MESSAGE";
      String DISPLAY_NOTIFICATION = "DISPLAY_NOTIFICATION";
    }

    public static final String PARAM_SDK_PLATFORM = "sdkPlatform";
    public static final String PARAM_PRIORITY = "priority";
    public static final String PARAM_MESSAGE_ID = "messageId";
    public static final String PARAM_ANALYTICS_LABEL = "analyticsLabel";
    public static final String PARAM_COMPOSER_LABEL = "composerLabel";
    public static final String PARAM_CAMPAIGN_ID = "campaignId";
    public static final String PARAM_TOPIC = "topic";
    public static final String PARAM_TTL = "ttl";
    public static final String PARAM_COLLAPSE_KEY = "collapseKey";
    public static final String PARAM_PACKAGE_NAME = "packageName";
    public static final String PARAM_INSTANCE_ID = "instanceId";
    public static final String PARAM_PROJECT_NUMBER = "projectNumber";

    // FCM log source name registered at Firelog. It uniquely identifies FCM's logging
    // configuration.
    public static final String FCM_LOG_SOURCE = "FCM_CLIENT_EVENT_LOGGING";
    public static final String SDK_PLATFORM_ANDROID = "ANDROID";

    // don't instantiate me.
    private FirelogAnalytics() {}
  }

  public static final class ScionAnalytics {

    public static final String ORIGIN_FCM = "fcm";
    public static final String PARAM_SOURCE = "source";
    public static final String PARAM_MEDIUM = "medium";
    public static final String PARAM_LABEL = "label";
    public static final String PARAM_TOPIC = "_nt";
    public static final String PARAM_CAMPAIGN = "campaign";
    public static final String PARAM_MESSAGE_NAME = "_nmn";
    public static final String PARAM_MESSAGE_TIME = "_nmt";
    public static final String PARAM_MESSAGE_DEVICE_TIME = "_ndt";
    public static final String PARAM_MESSAGE_CHANNEL = "message_channel";
    public static final String PARAM_MESSAGE_TYPE = "_nmc";
    public static final String EVENT_FIREBASE_CAMPAIGN = "_cmp";
    public static final String EVENT_NOTIFICATION_RECEIVE = "_nr";
    public static final String EVENT_NOTIFICATION_OPEN = "_no";
    public static final String EVENT_NOTIFICATION_DISMISS = "_nd";
    public static final String EVENT_NOTIFICATION_FOREGROUND = "_nf";
    public static final String USER_PROPERTY_FIREBASE_LAST_NOTIFICATION = "_ln";

    /** Message type names for Scion namespace */
    @StringDef({MessageType.DATA_MESSAGE, MessageType.DISPLAY_NOTIFICATION})
    public @interface MessageType {
      String DATA_MESSAGE = "data";
      String DISPLAY_NOTIFICATION = "display";
    }

    // FCM's "composerId" is known as "CampaignId" in the FN namespace. It is automatically
    // generated by the DMBE when a notification is sent through FN. It is known as "messageId" in
    // the GA namespace.
    static final String PARAM_COMPOSER_ID = "_nmid";

    // don't instantiate me.
    private ScionAnalytics() {}
  }

  // don't instantiate me.
  private Constants() {}
}
