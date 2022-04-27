// Copyright 2021 Google LLC
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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.inject.Deferred;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

class TestDeferred<T> implements Deferred<T> {
  private final Set<DeferredHandler<T>> handlers = new LinkedHashSet<>();

  @Override
  public void whenAvailable(@NonNull DeferredHandler<T> handler) {
    handlers.add(handler);
  }

  void provide(T value) {
    for (DeferredHandler<T> handler : handlers) {
      handler.handle(() -> value);
    }
    handlers.clear();
  }
}

@RunWith(AndroidJUnit4.class)
public class ProxyAnalyticsConnectorTest {

  private final AnalyticsConnector mockConnector = mock(AnalyticsConnector.class);
  private final AnalyticsConnectorHandle mockHandle = mock(AnalyticsConnectorHandle.class);

  @Before
  public void before() {
    when(mockConnector.registerAnalyticsConnectorListener(anyString(), any()))
        .thenReturn(mockHandle);
  }

  @Test
  public void logEvent_whenConnectorIsAvailable_shouldDelegateToIt() {
    TestDeferred<AnalyticsConnector> testDeferred = new TestDeferred<>();
    ProxyAnalyticsConnector proxy = new ProxyAnalyticsConnector(testDeferred);
    testDeferred.provide(mockConnector);

    String s = "s";
    String s1 = "s1";
    Bundle b = new Bundle();
    proxy.logEvent(s, s1, b);

    verify(mockConnector, times(1)).logEvent(s, s1, b);
  }

  @Test
  public void setUserProperty_whenConnectorIsAvailable_shouldDelegateToIt() {
    TestDeferred<AnalyticsConnector> testDeferred = new TestDeferred<>();
    ProxyAnalyticsConnector proxy = new ProxyAnalyticsConnector(testDeferred);
    testDeferred.provide(mockConnector);

    String s = "s";
    String s1 = "s1";
    Object o = new Object();
    proxy.setUserProperty(s, s1, o);

    verify(mockConnector, times(1)).setUserProperty(s, s1, o);
  }

  @Test
  public void
      registerListener_whenConnectorLoadsAfterRegistration_shouldPropagateRegistrationAndEventNames() {
    TestDeferred<AnalyticsConnector> testDeferred = new TestDeferred<>();
    ProxyAnalyticsConnector proxy = new ProxyAnalyticsConnector(testDeferred);
    AnalyticsConnectorListener listener = (i, bundle) -> {};

    AnalyticsConnectorHandle handle = proxy.registerAnalyticsConnectorListener("fiam", listener);
    handle.registerEventNames(Collections.singleton("hello"));
    testDeferred.provide(mockConnector);

    verify(mockConnector).registerAnalyticsConnectorListener("fiam", listener);
    verify(mockHandle, times(1)).registerEventNames(Collections.singleton("hello"));
  }

  @Test
  public void
      registerListener_whenConnectorLoadsAfterUnRegistration_shouldNotPropagateRegistrationAndEventNames() {
    TestDeferred<AnalyticsConnector> testDeferred = new TestDeferred<>();
    ProxyAnalyticsConnector proxy = new ProxyAnalyticsConnector(testDeferred);
    AnalyticsConnectorListener listener = (i, bundle) -> {};

    AnalyticsConnectorHandle handle = proxy.registerAnalyticsConnectorListener("fiam", listener);
    handle.unregister();
    handle.registerEventNames(Collections.singleton("hello"));

    testDeferred.provide(mockConnector);
    verify(mockConnector, never()).registerAnalyticsConnectorListener("fiam", listener);
    verify(mockHandle, never()).registerEventNames(Collections.singleton("hello"));
  }
}
