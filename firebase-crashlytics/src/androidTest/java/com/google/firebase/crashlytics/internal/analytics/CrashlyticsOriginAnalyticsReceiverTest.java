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
import static org.mockito.ArgumentMatchers.anyLong;

import android.os.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CrashlyticsOriginAnalyticsReceiverTest {

  @Mock private AppExceptionEventHandler mockAppExceptionEventHandler;

  private CrashlyticsOriginAnalyticsReceiver receiver;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    receiver = new CrashlyticsOriginAnalyticsReceiver(mockAppExceptionEventHandler);
  }

  @Test
  public void testReceiverCallsAppExceptionEventHandlerOnAppExceptionEvents() {
    final Bundle params = new Bundle();
    receiver.onEvent("_ae", params);
    Mockito.verify(mockAppExceptionEventHandler, Mockito.times(1)).onEventRecorded();
    Mockito.verify(mockAppExceptionEventHandler, Mockito.never())
        .recordAppExceptionEvent(anyLong());
  }

  @Test
  public void testReceiverCallsNothingOnOtherEvents() {
    final Bundle params = new Bundle();
    receiver.onEvent("_e", params);
    Mockito.verify(mockAppExceptionEventHandler, Mockito.never()).onEventRecorded();
    Mockito.verify(mockAppExceptionEventHandler, Mockito.never())
        .recordAppExceptionEvent(anyLong());
  }
}
