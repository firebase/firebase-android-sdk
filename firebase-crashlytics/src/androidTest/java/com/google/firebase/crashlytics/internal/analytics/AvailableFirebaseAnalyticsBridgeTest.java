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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AvailableFirebaseAnalyticsBridgeTest {

  @Mock private BreadcrumbSource mockBreadcrumbSource;

  @Mock private AppExceptionEventHandler mockAppExceptionEventHandler;

  private AvailableFirebaseAnalyticsBridge bridge;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    bridge =
        new AvailableFirebaseAnalyticsBridge(
            mockBreadcrumbSource, mockAppExceptionEventHandler, Runnable::run);
  }

  @Test
  public void testRegisterHandlerCallsBreadcrumbSourceRegister() {
    final BreadcrumbHandler mockBreadcrumbHandler = Mockito.mock(BreadcrumbHandler.class);

    bridge.registerBreadcrumbHandler(mockBreadcrumbHandler);

    Mockito.verify(mockBreadcrumbSource, Mockito.times(1))
        .registerBreadcrumbHandler(mockBreadcrumbHandler);
  }

  @Test
  public void testGetAnalyticsTaskChain_returnsCompleteTaskWhenRecordHasNotBeenCalled() {
    final Task<Void> analyticsTaskChain = bridge.getAnalyticsTaskChain();
    assertTrue(analyticsTaskChain.isComplete());
    assertTrue(analyticsTaskChain.isSuccessful());
  }

  @Test
  public void testGetAnalyticsTaskChain_returnIncompleteTaskWhenRecordHasNotFinished()
      throws Exception {
    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    final Task<Void> recordTask = source.getTask();
    Mockito.when(mockAppExceptionEventHandler.recordAppExceptionEvent(anyLong()))
        .thenReturn(recordTask);
    bridge.recordFatalFirebaseEvent(System.currentTimeMillis());
    final Task<Void> analyticsTaskChain = bridge.getAnalyticsTaskChain();
    assertFalse(analyticsTaskChain.isComplete());
    assertFalse(analyticsTaskChain.isSuccessful());
  }

  @Test
  public void testGetAnalyticsTaskChain_completesWhenRecordCompletes() throws Exception {
    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    final Task<Void> recordTask = source.getTask();
    Mockito.when(mockAppExceptionEventHandler.recordAppExceptionEvent(anyLong()))
        .thenReturn(recordTask);
    bridge.recordFatalFirebaseEvent(System.currentTimeMillis());
    final Task<Void> analyticsTaskChain = bridge.getAnalyticsTaskChain();
    assertFalse(analyticsTaskChain.isComplete());
    assertFalse(analyticsTaskChain.isSuccessful());
    source.trySetResult(null);
    Tasks.await(analyticsTaskChain);
    assertTrue(analyticsTaskChain.isComplete());
    assertTrue(analyticsTaskChain.isSuccessful());
  }

  @Test
  public void testGetAnalyticsTaskChain_completesWhenRecordCompletes_multipleCalls()
      throws Exception {
    final long timestamp1 = System.currentTimeMillis();
    final long timestamp2 = timestamp1 + 10;
    final TaskCompletionSource<Void> source1 = new TaskCompletionSource<>();
    final Task<Void> recordTask1 = source1.getTask();
    final TaskCompletionSource<Void> source2 = new TaskCompletionSource<>();
    final Task<Void> recordTask2 = source2.getTask();
    Mockito.when(mockAppExceptionEventHandler.recordAppExceptionEvent(anyLong()))
        .thenReturn(recordTask1)
        .thenReturn(recordTask2);
    bridge.recordFatalFirebaseEvent(timestamp1);
    bridge.recordFatalFirebaseEvent(timestamp2);
    final Task<Void> analyticsTaskChain = bridge.getAnalyticsTaskChain();
    assertFalse(analyticsTaskChain.isComplete());
    assertFalse(analyticsTaskChain.isSuccessful());
    source1.trySetResult(null);
    assertFalse(analyticsTaskChain.isComplete());
    assertFalse(analyticsTaskChain.isSuccessful());
    source2.trySetResult(null);
    Tasks.await(analyticsTaskChain);
    assertTrue(analyticsTaskChain.isComplete());
    assertTrue(analyticsTaskChain.isSuccessful());
  }

  @Test
  public void testAppExceptionHandlerCallsAreChained() throws Exception {
    final long timestamp1 = System.currentTimeMillis();
    final long timestamp2 = timestamp1 + 10;
    final TaskCompletionSource<Void> source1 = new TaskCompletionSource<>();
    final Task<Void> recordTask1 = source1.getTask();
    final TaskCompletionSource<Void> source2 = new TaskCompletionSource<>();
    final Task<Void> recordTask2 = source2.getTask();
    Mockito.when(mockAppExceptionEventHandler.recordAppExceptionEvent(anyLong()))
        .thenReturn(recordTask1)
        .thenReturn(recordTask2);

    // Call in succession
    bridge.recordFatalFirebaseEvent(timestamp1);
    bridge.recordFatalFirebaseEvent(timestamp2);

    // Verify that the event handler is called only for the first timestamp.
    Mockito.verify(mockAppExceptionEventHandler, Mockito.times(1))
        .recordAppExceptionEvent(timestamp1);
    Mockito.verify(mockAppExceptionEventHandler, Mockito.never())
        .recordAppExceptionEvent(timestamp2);

    // Resolve the first call
    source1.trySetResult(null);

    // Verify that the event handler is now called for the second timestamp.
    Mockito.verify(mockAppExceptionEventHandler, Mockito.times(1))
        .recordAppExceptionEvent(timestamp2);

    source2.trySetResult(null);

    Mockito.verifyNoMoreInteractions(mockAppExceptionEventHandler);

    assertTrue(bridge.getAnalyticsTaskChain().isSuccessful());
  }
}
