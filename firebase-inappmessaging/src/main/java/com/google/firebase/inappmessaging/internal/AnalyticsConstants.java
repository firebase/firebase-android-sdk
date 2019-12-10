// Copyright 2018 Google LLC
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

package com.google.firebase.inappmessaging.internal;

import com.google.common.annotations.VisibleForTesting;

/**
 * Constants used for measurement/analytics integration
 *
 * @hide
 */
final class AnalyticsConstants {
  // FIAM event names.
  @VisibleForTesting static final String ANALYTICS_IMPRESSION_EVENT = "fiam_impression";

  @VisibleForTesting static final String ANALYTICS_ACTION_EVENT = "fiam_action";

  @VisibleForTesting static final String ANALYTICS_DISMISS_EVENT = "fiam_dismiss";

  static final String ORIGIN_FIAM = "fiam";

  static final String PARAM_LABEL = "label";
  static final String PARAM_CAMPAIGN = "campaign";
  static final String PARAM_MESSAGE_ID = "_nmid";
  static final String PARAM_MESSAGE_NAME = "_nmn";
  static final String PARAM_MESSAGE_DEVICE_TIME = "_ndt";
  static final String USER_PROPERTY_FIREBASE_LAST_NOTIFICATION = "_ln";

  static final int MAX_REGISTERED_EVENTS = 50;
  static final String BUNDLE_EVENT_NAME_KEY = "events";
  static final int FIAM_ANALYTICS_CONNECTOR_LISTENER_EVENT_ID = 2;
}
