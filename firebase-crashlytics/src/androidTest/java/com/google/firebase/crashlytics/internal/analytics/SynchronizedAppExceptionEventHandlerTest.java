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

public class SynchronizedAppExceptionEventHandlerTest {

  @Mock private AppExceptionEventRecorder mockEventRecorder;
  private SynchronizedAppExceptionEventHandler handler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    handler = new SynchronizedAppExceptionEventHandler(mockEventRecorder);
  }

  @Test
  public void testHandlerCallsRecorderWithTimestamp() {
    final long timestamp = System.currentTimeMillis();
    handler.recordAppExceptionEvent(timestamp);
    Mockito.verify(mockEventRecorder, Mockito.times(1)).recordAppExceptionEvent(timestamp);
  }

  @Test
  public void testHandlerCallbackResolvesTask() {
    final Task<Void> eventTask = handler.recordAppExceptionEvent(System.currentTimeMillis());
    assertFalse(eventTask.isComplete());
    handler.onEventRecorded();
    assertTrue(eventTask.isComplete());
    assertTrue(eventTask.isSuccessful());
  }

  @Test
  public void testHandlerReturnsSameTaskBeforeCallback() {
    final Task<Void> eventTask = handler.recordAppExceptionEvent(System.currentTimeMillis());
    final Task<Void> eventTask2 = handler.recordAppExceptionEvent(System.currentTimeMillis());
    assertEquals(eventTask, eventTask2);
    handler.onEventRecorded();
    final Task<Void> eventTask3 = handler.recordAppExceptionEvent(System.currentTimeMillis());
    assertNotEquals(eventTask, eventTask3);
  }
}
