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

import android.os.Bundle;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import io.reactivex.FlowableEmitter;

/**
 * Fiam specific implementation of the AnalyticsConnectorListener.
 *
 * @hide
 */
final class FiamAnalyticsConnectorListener implements AnalyticsConnectorListener {
  private FlowableEmitter<String> emitter;

  FiamAnalyticsConnectorListener(FlowableEmitter<String> emitter) {
    this.emitter = emitter;
  }

  @Override
  public void onMessageTriggered(int id, Bundle extras) {
    if (id == AnalyticsConstants.FIAM_ANALYTICS_CONNECTOR_LISTENER_EVENT_ID) {
      emitter.onNext(extras.getString("events"));
    }
  }
}
