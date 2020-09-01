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

import android.content.Intent;
import androidx.annotation.NonNull;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.messaging.Constants.FirelogAnalytics;
import java.io.IOException;

/**
 * Helper class that wraps the messaging intent and the event type (i.e. message received, open,
 * etc). {@link FirelogAnalyticsEventEncoder#encode(FirelogAnalyticsEvent, ObjectEncoderContext)}
 * consumes an instance of this class as one of the inputs to produce a JSON encoded byte array as
 * the payload to be transported to the Firelog backend.
 *
 * <p>The JSON object it attempts to create looks like: {"event": "messageDelivered", "topic":
 * "bayAreaWeather", "instanceId": "yourInstanceId", "messageId": "yourMessageId", ...: ... }
 *
 * @hide
 */
@KeepForSdk
final class FirelogAnalyticsEvent {
  private final String eventType;
  private final Intent intent;

  FirelogAnalyticsEvent(@NonNull String eventType, @NonNull Intent intent) {
    this.eventType = Preconditions.checkNotEmpty(eventType, "evenType must be non-null");
    this.intent = Preconditions.checkNotNull(intent, "intent must be non-null");
  }

  @NonNull
  Intent getIntent() {
    return this.intent;
  }

  @NonNull
  String getEventType() {
    return this.eventType;
  }

  static class FirelogAnalyticsEventEncoder implements ObjectEncoder<FirelogAnalyticsEvent> {
    @Override
    public void encode(
        FirelogAnalyticsEvent firelogAnalyticsEvent, ObjectEncoderContext encoderContext)
        throws EncodingException, IOException {
      Intent intent = firelogAnalyticsEvent.getIntent();

      // non-null parameters, no need to "check and package"
      encoderContext.add(FirelogAnalytics.PARAM_TTL, MessagingAnalytics.getTtl(intent));
      encoderContext.add(FirelogAnalytics.PARAM_EVENT, firelogAnalyticsEvent.getEventType());
      encoderContext.add(FirelogAnalytics.PARAM_INSTANCE_ID, MessagingAnalytics.getInstanceId());
      encoderContext.add(FirelogAnalytics.PARAM_PRIORITY, MessagingAnalytics.getPriority(intent));
      encoderContext.add(FirelogAnalytics.PARAM_PACKAGE_NAME, MessagingAnalytics.getPackageName());
      encoderContext.add(
          FirelogAnalytics.PARAM_SDK_PLATFORM, FirelogAnalytics.SDK_PLATFORM_ANDROID);
      encoderContext.add(
          FirelogAnalytics.PARAM_MESSAGE_TYPE, MessagingAnalytics.getMessageTypeForFirelog(intent));

      // nullable parameters
      String messageId = MessagingAnalytics.getMessageId(intent);
      if (messageId != null) { // shouldn't happen in prod
        encoderContext.add(FirelogAnalytics.PARAM_MESSAGE_ID, messageId);
      }

      String topic = MessagingAnalytics.getTopic(intent);
      if (topic != null) {
        encoderContext.add(FirelogAnalytics.PARAM_TOPIC, topic);
      }

      String collapseKey = MessagingAnalytics.getCollapseKey(intent);
      if (collapseKey != null) {
        encoderContext.add(FirelogAnalytics.PARAM_COLLAPSE_KEY, collapseKey);
      }

      String messageLabel = MessagingAnalytics.getMessageLabel(intent);
      if (messageLabel != null) {
        encoderContext.add(
            FirelogAnalytics.PARAM_ANALYTICS_LABEL, MessagingAnalytics.getMessageLabel(intent));
      }

      String composerLabel = MessagingAnalytics.getComposerLabel(intent);
      if (composerLabel != null) {
        encoderContext.add(
            FirelogAnalytics.PARAM_COMPOSER_LABEL, MessagingAnalytics.getComposerLabel(intent));
      }

      String projectNumber = MessagingAnalytics.getProjectNumber();
      if (projectNumber != null) {
        encoderContext.add(FirelogAnalytics.PARAM_PROJECT_NUMBER, projectNumber);
      }
    }
  }

  /**
   * Wrapper class that wraps the FirelogAnalyticsEvent payload. The payload will be keyed with the
   * extension name.
   *
   * <p>The JSON object it attempts to create looks like: {"messaging_client_event": {"event":
   * "messageDelivered", "topic": "bayAreaWeather", "instanceId": "yourInstanceId", "messageId":
   * "yourMessageId", ...: ... } }
   */
  static final class FirelogAnalyticsEventWrapper {

    private final FirelogAnalyticsEvent firelogAnalyticsEvent;

    FirelogAnalyticsEventWrapper(@NonNull FirelogAnalyticsEvent firelogAnalyticsEvent) {
      this.firelogAnalyticsEvent = Preconditions.checkNotNull(firelogAnalyticsEvent);
    }

    @NonNull
    FirelogAnalyticsEvent getFirelogAnalyticsEvent() {
      return this.firelogAnalyticsEvent;
    }
  }

  static final class FirelogAnalyticsEventWrapperEncoder
      implements ObjectEncoder<FirelogAnalyticsEventWrapper> {
    private static final String FCM_PAYLOAD_IDENTIFIER = "messaging_client_event";

    @Override
    public void encode(
        FirelogAnalyticsEventWrapper firelogAnalyticsEventWrapper,
        ObjectEncoderContext encoderContext)
        throws EncodingException, IOException {
      encoderContext.add(
          FCM_PAYLOAD_IDENTIFIER, firelogAnalyticsEventWrapper.getFirelogAnalyticsEvent());
    }
  }
}
