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

package com.google.firebase.crashlytics.internal.analytics;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import android.os.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class BaseAnalyticsReceiverTest {

  @Mock private AnalyticsReceiver breadcrumbReceiver;

  @Mock private AnalyticsReceiver crashlyticsOriginEventReceiver;

  private BaseAnalyticsReceiver receiver;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    receiver = new BaseAnalyticsReceiver(breadcrumbReceiver, crashlyticsOriginEventReceiver);
  }

  @Test
  public void testCrashlyticsOriginEventGoesToCrashlyticsReceiver() {
    final Bundle params = makeParams("clx");
    receiver.onEvent("event", params);
    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.times(1)).onEvent("event", params);
    Mockito.verify(breadcrumbReceiver, Mockito.never()).onEvent(anyString(), any(Bundle.class));
  }

  @Test
  public void testNonCrashlyticsOriginEventGoesToBreadcrumbReceiver() {
    final Bundle params = makeParams("abc");
    receiver.onEvent("event", params);
    Mockito.verify(breadcrumbReceiver, Mockito.times(1)).onEvent("event", params);
    Mockito.verify(crashlyticsOriginEventReceiver, Mockito.never())
        .onEvent(anyString(), any(Bundle.class));
  }

  private static Bundle makeParams(String origin) {
    final Bundle params = new Bundle();
    params.putString("_o", origin);
    return params;
  }
}
