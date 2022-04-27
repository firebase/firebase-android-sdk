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
import android.os.Bundle;
import java.util.Arrays;
import java.util.Objects;
import org.mockito.ArgumentMatcher;

/** Helper class for building remote messages. */
public class RemoteMessageBuilder {

  // Constants are copied so that tests will break if these change
  public static final String ACTION_GCM_MESSAGE = "com.google.android.c2dm.intent.RECEIVE";

  // Action for upstream send
  public static final String ACTION_SEND = "com.google.android.gcm.intent.SEND";
  // Permission for upstream send that target receiver should require
  public static final String PERMISSION_SEND =
      "com.google.android.gtalkservice.permission.GTALK_SERVICE";

  public static final String EXTRA_FROM = "from";
  public static final String EXTRA_TO = "google.to";
  public static final String EXTRA_MESSAGE_TYPE = "message_type";
  public static final String EXTRA_RAW_DATA = "rawData";
  public static final String EXTRA_SENT_TIME = "google.sent_time";
  public static final String EXTRA_TTL = "google.ttl";
  public static final String EXTRA_SENDER_ID = "google.c.sender.id";
  public static final String EXTRA_ORIGINAL_PRIORITY = "google.original_priority";
  public static final String EXTRA_DELIVERED_PRIORITY = "google.delivered_priority";
  public static final String EXTRA_PRIORITY_V19 = "google.priority";
  public static final String EXTRA_PRIORITY_REDUCED_V19 = "google.priority_reduced";
  public static final String EXTRA_MSGID = "google.message_id";
  public static final String EXTRA_COLLAPSE_KEY = "collapse_key";
  public static final String EXTRA_ERROR = "error";

  public static final String MESSAGE_TYPE_MESSAGE = "gcm";
  public static final String MESSAGE_TYPE_DELETED = "deleted_messages";
  public static final String MESSAGE_TYPE_SEND_EVENT = "send_event";
  public static final String MESSAGE_TYPE_SEND_ERROR = "send_error";

  private final Bundle bundle = new Bundle();

  public static boolean messagesEqual(RemoteMessage m1, RemoteMessage m2) {
    return Objects.equals(m1.getFrom(), m2.getFrom())
        && Objects.equals(m1.getTo(), m2.getTo())
        && Objects.equals(m1.getData(), m2.getData())
        && Arrays.equals(m1.getRawData(), m2.getRawData())
        && Objects.equals(m1.getCollapseKey(), m2.getCollapseKey())
        && Objects.equals(m1.getMessageId(), m2.getMessageId())
        && Objects.equals(m1.getMessageType(), m2.getMessageType())
        && Objects.equals(m1.getSenderId(), m2.getSenderId())
        && m1.getSentTime() == m2.getSentTime()
        && m1.getTtl() == m2.getTtl()
        && m1.getOriginalPriority() == m2.getOriginalPriority()
        && m1.getPriority() == m2.getPriority();
  }

  /** Build a message intent as sent by GCM within GmsCore. */
  public Intent buildIntent() {
    Intent intent = new Intent(ACTION_GCM_MESSAGE);
    if (bundle.size() > 0) {
      // This is more representative of the real intent code that returns a null bundle if
      // no extras have been added.
      intent.putExtras(bundle);
    }
    return intent;
  }

  /** Build a RemoteMessage instance. */
  public RemoteMessage build() {
    return new RemoteMessage(bundle);
  }

  /** Build an argument matcher for use with mockito verification. */
  public ArgumentMatcher<RemoteMessage> buildMatcher() {
    final RemoteMessage expected = build();
    return new ArgumentMatcher<RemoteMessage>() {
      @Override
      public boolean matches(RemoteMessage argument) {
        if (!(argument instanceof RemoteMessage)) {
          return false;
        }
        return messagesEqual(expected, (RemoteMessage) argument);
      }
    };
  }

  public RemoteMessageBuilder setFrom(String from) {
    bundle.putString(EXTRA_FROM, from);
    return this;
  }

  public RemoteMessageBuilder setTo(String to) {
    bundle.putString(EXTRA_TO, to);
    return this;
  }

  public RemoteMessageBuilder addData(String key, String value) {
    bundle.putString(key, value);
    return this;
  }

  public RemoteMessageBuilder setRawData(byte[] data) {
    bundle.putByteArray(EXTRA_RAW_DATA, data);
    return this;
  }

  public RemoteMessageBuilder setCollapseKey(String collapseKey) {
    bundle.putString(EXTRA_COLLAPSE_KEY, collapseKey);
    return this;
  }

  public RemoteMessageBuilder setMessageId(String messageId) {
    bundle.putString(EXTRA_MSGID, messageId);
    return this;
  }

  public RemoteMessageBuilder setMessageType(String messageType) {
    bundle.putString(EXTRA_MESSAGE_TYPE, messageType);
    return this;
  }

  public RemoteMessageBuilder setSentTime(long sentTime) {
    bundle.putLong(EXTRA_SENT_TIME, sentTime);
    return this;
  }

  public RemoteMessageBuilder setTtl(int ttl) {
    bundle.putInt(EXTRA_TTL, ttl);
    return this;
  }
}
