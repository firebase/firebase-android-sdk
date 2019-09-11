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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Bundle;
import io.reactivex.FlowableEmitter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class FiamAnalyticsConnectorListenerTest {

  @Mock private FlowableEmitter<String> emitter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void fiamAnalyticsConnectorListener_ok() throws Exception {
    FiamAnalyticsConnectorListener listener = new FiamAnalyticsConnectorListener(emitter);
    Bundle bundle = new Bundle();
    String eventName = "event1";
    bundle.putString("events", eventName);
    listener.onMessageTriggered(
        AnalyticsConstants.FIAM_ANALYTICS_CONNECTOR_LISTENER_EVENT_ID, bundle);
    verify(emitter, times(1)).onNext(eventName);
  }

  @Test
  public void fiamAnalyticsConnectorListener_doesntTriggerOnOtherIds() throws Exception {
    FiamAnalyticsConnectorListener listener = new FiamAnalyticsConnectorListener(emitter);
    Bundle bundle = new Bundle();
    String eventName = "event1";
    bundle.putString(AnalyticsConstants.BUNDLE_EVENT_NAME_KEY, eventName);
    listener.onMessageTriggered(1, bundle);
    verify(emitter, never()).onNext(eventName);
  }
}
