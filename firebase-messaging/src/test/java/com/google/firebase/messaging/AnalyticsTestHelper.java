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

/** Helper for firebase messaging analytics tests. */
public class AnalyticsTestHelper {

  public interface Analytics {
    static final String ORIGIN_FCM = "fcm";
    static final String PARAM_SOURCE = "source";
    static final String PARAM_MEDIUM = "medium";
    static final String PARAM_LABEL = "label";
    static final String PARAM_TOPIC = "_nt";
    static final String PARAM_CAMPAIGN = "campaign";
    static final String PARAM_MESSAGE_ID = "_nmid";
    static final String PARAM_MESSAGE_NAME = "_nmn";
    static final String PARAM_MESSAGE_TIME = "_nmt";
    static final String PARAM_MESSAGE_DEVICE_TIME = "_ndt";
    static final String EVENT_FIREBASE_CAMPAIGN = "_cmp";
    static final String EVENT_NOTIFICATION_RECEIVE = "_nr";
    static final String EVENT_NOTIFICATION_OPEN = "_no";
    static final String EVENT_NOTIFICATION_DISMISS = "_nd";
    static final String EVENT_NOTIFICATION_FOREGROUND = "_nf";
    static final String USER_PROPERTY_FIREBASE_LAST_NOTIFICATION = "_ln";
  }

  // Copy from GcmListenerService so the tests break if the constants are changed
  public static final String ANALYTICS_PREFIX = "google.c.a.";
  public static final String ANALYTICS_ENABLED = ANALYTICS_PREFIX + "e";
  public static final String ANALYTICS_COMPOSER_ID = ANALYTICS_PREFIX + "c_id";
  public static final String ANALYTICS_COMPOSER_LABEL = ANALYTICS_PREFIX + "c_l";
  public static final String ANALYTICS_MESSAGE_TIMESTAMP = ANALYTICS_PREFIX + "ts";
  public static final String ANALYTICS_MESSAGE_USE_DEVICE_TIME = ANALYTICS_PREFIX + "udt";

  public static final String DEFAULT_COMPOSER_ID = "composer_key";
  public static final String DEFAULT_COMPOSER_LABEL = "composer_label";
  public static final int DEFAULT_COMPOSER_TIMESTAMP = 1234567890;

  public static void addAnalyticsExtras(RemoteMessageBuilder builder) {
    builder.addData(ANALYTICS_ENABLED, "1");
    builder.addData(ANALYTICS_COMPOSER_ID, DEFAULT_COMPOSER_ID);
    builder.addData(ANALYTICS_COMPOSER_LABEL, DEFAULT_COMPOSER_LABEL);
    builder.addData(ANALYTICS_MESSAGE_TIMESTAMP, String.valueOf(DEFAULT_COMPOSER_TIMESTAMP));
  }
}
