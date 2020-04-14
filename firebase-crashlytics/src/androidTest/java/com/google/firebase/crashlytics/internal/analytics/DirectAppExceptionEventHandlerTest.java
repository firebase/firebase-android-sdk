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

import com.google.android.gms.tasks.Task;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DirectAppExceptionEventHandlerTest {

  @Mock private AppExceptionEventRecorder mockEventRecorder;
  private DirectAppExceptionEventHandler handler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    handler = new DirectAppExceptionEventHandler(mockEventRecorder);
  }

  @Test
  public void testHandlerCallsRecorderWithTimestamp() {
    final long timestamp = System.currentTimeMillis();
    handler.recordAppExceptionEvent(timestamp);
    Mockito.verify(mockEventRecorder, Mockito.times(1)).recordAppExceptionEvent(timestamp);
  }

  @Test
  public void testHandlerResolvesTaskImmediately() {
    final Task<Void> eventTask = handler.recordAppExceptionEvent(System.currentTimeMillis());
    assertTrue(eventTask.isComplete());
    assertTrue(eventTask.isSuccessful());
  }
}
