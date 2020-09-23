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

package com.google.firebase.crashlytics;

import static com.google.firebase.crashlytics.CrashlyticsAnalyticsListener.CRASHLYTICS_ORIGIN;
import static com.google.firebase.crashlytics.CrashlyticsAnalyticsListener.EVENT_NAME_KEY;
import static com.google.firebase.crashlytics.CrashlyticsAnalyticsListener.EVENT_ORIGIN_KEY;
import static com.google.firebase.crashlytics.CrashlyticsAnalyticsListener.EVENT_PARAMS_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.os.Bundle;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventReceiver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CrashlyticsAnalyticsListenerTest {

  @Mock private AnalyticsEventReceiver breadcrumbReceiver;

  @Mock private AnalyticsEventReceiver crashlyticsOriginEventReceiver;

  private CrashlyticsAnalyticsListener listener;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    listener = new CrashlyticsAnalyticsListener();
  }

  @Test
  public void testListenerCallsBreadcrumbReceiverWithEmptyParamsIfParamsIsNull() {
    listener.setCrashlyticsOriginEventReceiver(crashlyticsOriginEventReceiver);
    listener.setBreadcrumbEventReceiver(breadcrumbReceiver);

    final Bundle event = makeEventBundle("event", null);

    listener.onMessageTriggered(1, event);

    final ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);
    Mockito.verify(breadcrumbReceiver, Mockito.times(1))
        .onEvent(eq("event"), bundleArgumentCaptor.capture());
    final Bundle actualParams = bundleArgumentCaptor.getValue();

    assertNotNull(actualParams);
    assertEquals(0, actualParams.size());

    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.never())
        .onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testListenerDoesNotCallEitherReceiverWhenExtrasIsNull() {
    listener.setCrashlyticsOriginEventReceiver(crashlyticsOriginEventReceiver);
    listener.setBreadcrumbEventReceiver(breadcrumbReceiver);
    listener.onMessageTriggered(1, null);
    Mockito.verify(breadcrumbReceiver, Mockito.never()).onEvent(anyString(), any(Bundle.class));
    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.never())
        .onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testListenerDoesNotCallEitherReceiverWhenNameIsNull() {
    listener.setCrashlyticsOriginEventReceiver(crashlyticsOriginEventReceiver);
    listener.setBreadcrumbEventReceiver(breadcrumbReceiver);
    listener.onMessageTriggered(1, new Bundle());
    Mockito.verify(breadcrumbReceiver, Mockito.never()).onEvent(anyString(), any(Bundle.class));
    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.never())
        .onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testCrashlyticsOriginEventGoesToCrashlyticsReceiver() {
    listener.setCrashlyticsOriginEventReceiver(crashlyticsOriginEventReceiver);
    listener.setBreadcrumbEventReceiver(breadcrumbReceiver);
    final Bundle params = makeParams(CRASHLYTICS_ORIGIN);
    final Bundle event = makeEventBundle("event", params);
    listener.onMessageTriggered(0, event);
    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.times(1)).onEvent("event", params);
    Mockito.verify(breadcrumbReceiver, Mockito.never()).onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testNonCrashlyticsOriginEventGoesToBreadcrumbReceiver() {
    listener.setCrashlyticsOriginEventReceiver(crashlyticsOriginEventReceiver);
    listener.setBreadcrumbEventReceiver(breadcrumbReceiver);
    final Bundle params = makeParams("abc");
    final Bundle event = makeEventBundle("event", params);
    listener.onMessageTriggered(0, event);
    Mockito.verify(breadcrumbReceiver, Mockito.times(1)).onEvent("event", params);
    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.never())
        .onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testNullCrashlyticsOriginReceiverDropsEvent() {
    listener.setBreadcrumbEventReceiver(breadcrumbReceiver);
    final Bundle params = makeParams(CRASHLYTICS_ORIGIN);
    final Bundle event = makeEventBundle("event", params);
    listener.onMessageTriggered(0, event);
    Mockito.verify(breadcrumbReceiver, Mockito.never()).onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testNullBreadcrumbReceiverDropsEvent() {
    listener.setCrashlyticsOriginEventReceiver(crashlyticsOriginEventReceiver);
    final Bundle params = makeParams("abc");
    final Bundle event = makeEventBundle("event", params);
    listener.onMessageTriggered(0, event);
    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.never())
        .onEvent(anyString(), any(Bundle.class));
  }

  private static Bundle makeEventBundle(String name, Bundle params) {
    final Bundle extras = new Bundle();
    extras.putString(EVENT_NAME_KEY, name);
    extras.putBundle(EVENT_PARAMS_KEY, params);
    return extras;
  }

  private static Bundle makeParams(String origin) {
    final Bundle params = new Bundle();
    params.putString(EVENT_ORIGIN_KEY, origin);
    return params;
  }
}
