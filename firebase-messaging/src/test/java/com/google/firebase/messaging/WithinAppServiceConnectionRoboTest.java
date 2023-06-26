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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.WithinAppServiceConnection.BindRequest;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(RobolectricTestRunner.class)
public class WithinAppServiceConnectionRoboTest {

  private static final String TEST_BIND_ACTION = "testBindAction";

  // The amount of time that a broadcast receiver takes to time out
  private static final long RECEIVER_TIMEOUT_S = 20;

  private Application context;
  private FakeScheduledExecutorService fakeExecutor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    fakeExecutor = new FakeScheduledExecutorService();
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void testReceiverTimesOut() {
    WithinAppServiceConnection connection =
        new WithinAppServiceConnection(context, TEST_BIND_ACTION, fakeExecutor);
    setMockBinder(TEST_BIND_ACTION);

    // Send an intent, verify the pending result isn't finished
    Task<Void> pendingResult = connection.sendIntent(new Intent());
    assertThat(pendingResult.isComplete()).isFalse();

    // Check the runnable doesn't run early, and that after it should have run the pending
    // result is finished.
    fakeExecutor.simulateNormalOperationFor(RECEIVER_TIMEOUT_S - 1, TimeUnit.SECONDS);
    assertThat(pendingResult.isComplete()).isFalse();
    fakeExecutor.simulateNormalOperationFor(1, TimeUnit.SECONDS);
    assertThat(pendingResult.isComplete()).isTrue();
  }

  @Test
  public void testTimeoutGetsCancelled() {
    WithinAppServiceConnection connection =
        new WithinAppServiceConnection(context, TEST_BIND_ACTION, fakeExecutor);
    WithinAppServiceBinder binder = setMockBinder(TEST_BIND_ACTION);

    // Send an intent, verify the pending result isn't finished
    Task<Void> pendingResult = connection.sendIntent(new Intent());
    assertThat(pendingResult.isComplete()).isFalse();

    // Grab the bind request and finish it, then verify the pending result is finished and the
    // timeout runnable was cancelled.
    getBindRequest(binder).finish();
    fakeExecutor.simulateNormalOperationFor(0, TimeUnit.SECONDS);

    assertThat(pendingResult.isComplete()).isTrue();
    assertThat(fakeExecutor.isEmpty()).isTrue();
  }

  @Test
  public void testBindingTwiceToUnbindableServiceFailsQuickly() {
    WithinAppServiceConnection connection =
        new WithinAppServiceConnection(context, TEST_BIND_ACTION, fakeExecutor);

    // Make bindService return false
    shadowOf(context).declareActionUnbindable(TEST_BIND_ACTION);

    // Send an intent, verify the pending result is finished quickly
    Task<Void> pendingResult = connection.sendIntent(new Intent());
    assertThat(pendingResult.isComplete()).isTrue();

    // And then a second time
    pendingResult = connection.sendIntent(new Intent());
    assertThat(pendingResult.isComplete()).isTrue();
  }

  @Test
  public void testBindingTwiceToMissingServiceFailsQuickly() {
    WithinAppServiceConnection connection =
        new WithinAppServiceConnection(context, TEST_BIND_ACTION, fakeExecutor);

    // Don't set a binder, so bindService invokes onServiceConnected with a null binder

    // Send an intent, verify the pending result is finished quickly
    Task<Void> pendingResult = connection.sendIntent(new Intent());
    assertThat(pendingResult.isComplete()).isTrue();

    // And then a second time
    pendingResult = connection.sendIntent(new Intent());
    assertThat(pendingResult.isComplete()).isTrue();
  }

  @Test
  public void testHandlesUnexpectedBinderImplementation() {
    Intent bindIntent = new Intent(TEST_BIND_ACTION);
    bindIntent.setPackage(context.getPackageName());
    IBinder binder = new Binder();
    shadowOf(context)
        .setComponentNameAndServiceForBindServiceForIntent(
            bindIntent, new ComponentName("robo", "service"), binder);

    WithinAppServiceConnection connection =
        new WithinAppServiceConnection(context, TEST_BIND_ACTION, fakeExecutor);
    assertThat(connection.sendIntent(new Intent()).isComplete()).isTrue();
  }

  private WithinAppServiceBinder setMockBinder(String action) {
    Intent bindIntent = new Intent(action);
    bindIntent.setPackage(context.getPackageName());
    WithinAppServiceBinder mockBinder = mock(WithinAppServiceBinder.class);
    doReturn(true).when(mockBinder).isBinderAlive();
    shadowOf(context)
        .setComponentNameAndServiceForBindServiceForIntent(
            bindIntent, new ComponentName("robo", "service"), mockBinder);
    return mockBinder;
  }

  private BindRequest getBindRequest(WithinAppServiceBinder binder) {
    ArgumentCaptor<BindRequest> bindRequestCaptor = ArgumentCaptor.forClass(BindRequest.class);
    verify(binder).send(bindRequestCaptor.capture());
    return bindRequestCaptor.getValue();
  }
}
