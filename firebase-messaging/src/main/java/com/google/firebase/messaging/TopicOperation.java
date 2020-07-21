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

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Objects;
import java.util.regex.Pattern;

final class TopicOperation {
  @VisibleForTesting static final String OPERATION_PAIR_DIVIDER = "!";
  private static final String OLD_TOPIC_PREFIX = "/topics/";
  private static final String TOPIC_NAME_PATTERN = "[a-zA-Z0-9-_.~%]{1,900}";
  /**
   * Only topic names that match the pattern "[a-zA-Z0-9-_.~%]{1,900}" are allowed for subscribing
   * and publishing.
   *
   * <p>Needs to be in sync with the server.
   */
  private static final Pattern TOPIC_NAME_REGEXP = Pattern.compile(TOPIC_NAME_PATTERN);

  private final String topic;
  private final String operation;
  private final String serializedString;

  private TopicOperation(@TopicOperations String operation, String topic) {
    this.topic = normalizeTopicOrThrow(topic, operation);
    this.operation = operation;
    this.serializedString = operation + OPERATION_PAIR_DIVIDER + topic;
  }

  @NonNull
  private static String normalizeTopicOrThrow(String topic, String methodName) {
    if (topic != null && topic.startsWith(OLD_TOPIC_PREFIX)) {
      Log.w(
          TAG,
          String.format(
              "Format /topics/topic-name is deprecated. Only 'topic-name' should be used in %s.",
              methodName));

      topic = topic.substring(OLD_TOPIC_PREFIX.length());
    }

    if (topic == null || !TOPIC_NAME_REGEXP.matcher(topic).matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid topic name: %s does not match the allowed format %s.",
              topic, TOPIC_NAME_PATTERN));
    }

    return topic;
  }

  public static TopicOperation subscribe(@NonNull String topic) {
    return new TopicOperation(TopicOperations.OPERATION_SUBSCRIBE, topic);
  }

  public static TopicOperation unsubscribe(@NonNull String topic) {
    return new TopicOperation(TopicOperations.OPERATION_UNSUBSCRIBE, topic);
  }

  @Nullable
  static TopicOperation from(String entry) {
    if (TextUtils.isEmpty(entry)) {
      return null;
    }

    String[] splits = entry.split(OPERATION_PAIR_DIVIDER, -1);
    if (splits.length != 2) {
      return null;
    }

    return new TopicOperation(/*operation=*/ splits[0], /*topic=*/ splits[1]);
  }

  public String getTopic() {
    return topic;
  }

  public String getOperation() {
    return operation;
  }

  public String serialize() {
    return serializedString;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof TopicOperation)) {
      return false;
    }
    TopicOperation that = (TopicOperation) obj;
    return topic.equals(that.topic) && operation.equals(that.operation);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(operation, topic);
  }

  @StringDef({TopicOperations.OPERATION_SUBSCRIBE, TopicOperations.OPERATION_UNSUBSCRIBE})
  @interface TopicOperations {
    String OPERATION_SUBSCRIBE = "S";
    String OPERATION_UNSUBSCRIBE = "U";
  }
}
