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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.os.Bundle;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsReceiver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CrashlyticsAnalyticsListenerTest {

  @Mock private AnalyticsReceiver mockReceiver;

  private CrashlyticsAnalyticsListener listener;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    listener = new CrashlyticsAnalyticsListener();
  }

  @Test
  public void testListenerCallsReceiverWithNameAndParams() {
    final String name = "testName";
    listener.setAnalyticsReceiver(mockReceiver);

    final Bundle extras = new Bundle();
    extras.putString("name", name);

    final Bundle params = new Bundle();
    params.putString("param1", "param");

    extras.putBundle("params", params);

    listener.onMessageTriggered(1, extras);

    final ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);
    Mockito.verify(mockReceiver, Mockito.times(1))
        .onEvent(eq(name), bundleArgumentCaptor.capture());
    final Bundle actualParams = bundleArgumentCaptor.getValue();

    assertEquals(actualParams.size(), params.size());
    for (String key : params.keySet()) {
      assertEquals(params.get(key), actualParams.get(key));
    }
  }

  @Test
  public void testListenerCallsReceiverWithEmptyParamsIfParamsIsNull() {
    final String name = "testName";
    listener.setAnalyticsReceiver(mockReceiver);

    final Bundle extras = new Bundle();
    extras.putString("name", name);

    listener.onMessageTriggered(1, extras);

    final ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);
    Mockito.verify(mockReceiver, Mockito.times(1))
        .onEvent(eq(name), bundleArgumentCaptor.capture());
    final Bundle actualParams = bundleArgumentCaptor.getValue();

    assertNotNull(actualParams);
    assertEquals(0, actualParams.size());
  }

  @Test
  public void testListenerDoesNotCallReceiverWhenExtrasIsNull() {
    listener.setAnalyticsReceiver(mockReceiver);
    listener.onMessageTriggered(1, null);
    Mockito.verify(mockReceiver, Mockito.never()).onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testListenerDoesNotCallReceiverWhenNameIsNull() {
    listener.setAnalyticsReceiver(mockReceiver);
    listener.onMessageTriggered(1, new Bundle());
    Mockito.verify(mockReceiver, Mockito.never()).onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testListenerDoesNotCallReceiverWhenReceiverIsNull() {
    listener.onMessageTriggered(1, new Bundle());
    // Should not fail.
  }
}
