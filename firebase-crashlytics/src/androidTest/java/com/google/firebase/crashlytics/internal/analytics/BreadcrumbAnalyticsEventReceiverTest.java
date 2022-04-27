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

import android.os.Bundle;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class BreadcrumbAnalyticsEventReceiverTest {

  @Mock private BreadcrumbHandler mockBreadcrumbHandler;

  private BreadcrumbAnalyticsEventReceiver receiver;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    receiver = new BreadcrumbAnalyticsEventReceiver();
  }

  @Test
  public void testReceiverDropsEventsWhenHandlerIsNull() {
    receiver.onEvent("event", new Bundle());
    // Should not fail.
  }

  @Test
  public void testReceiverPassesSerializedBreadcrumbToHandler() {
    receiver.registerBreadcrumbHandler(mockBreadcrumbHandler);
    receiver.onEvent("event", new Bundle());
    final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(mockBreadcrumbHandler, Mockito.times(1))
        .handleBreadcrumb(argumentCaptor.capture());
    final String argument = argumentCaptor.getValue();
    assertTrue(argument.startsWith("$A$:"));
    assertTrue(argument.contains("event"));
  }
}
