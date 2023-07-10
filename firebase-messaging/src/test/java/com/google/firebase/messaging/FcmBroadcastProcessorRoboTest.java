// Copyright 2022 Google LLC
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPowerManager;

/** Robolectric test for FcmBroadcastProcessor. */
@RunWith(RobolectricTestRunner.class)
public class FcmBroadcastProcessorRoboTest {

  private static final String ACTION_FCM_MESSAGE = "com.google.android.c2dm.intent.RECEIVE";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private Application context;
  private FcmBroadcastProcessor processor;
  private FakeScheduledExecutorService fakeExecutorService;
  @Mock private ServiceStarter serviceStarter;
  @Mock private WithinAppServiceConnection mockConnection;
  private TaskCompletionSource<Void> sendIntentTask;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    ServiceStarter.setForTesting(serviceStarter);
    fakeExecutorService = new FakeScheduledExecutorService();
    processor = new FcmBroadcastProcessor(context, fakeExecutorService);
    FcmBroadcastProcessor.setServiceConnection(mockConnection);
    sendIntentTask = new TaskCompletionSource<>();
    when(mockConnection.sendIntent(any(Intent.class))).thenReturn(sendIntentTask.getTask());
  }

  @After
  public void resetStaticState() {
    ServiceStarter.setForTesting(null);
    FcmBroadcastProcessor.reset();
    WakeLockHolder.reset();
    ShadowPowerManager.clearWakeLocks();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testStartMessagingService_Background() {
    setSubjectToBackgroundCheck();
    setWakeLockPermission(true);
    Intent intent = new Intent(ACTION_FCM_MESSAGE);

    Task<Integer> startServiceTask = processor.startMessagingService(context, intent);

    // Should send the intent through the connection and not acquire a WakeLock.
    assertThat(startServiceTask.isComplete()).isFalse();
    verify(serviceStarter, never()).startMessagingService(any(), any());
    verify(mockConnection).sendIntent(intent);
    assertThat(ShadowPowerManager.getLatestWakeLock()).isNull();

    // After the message has been handled, the task should be completed successfully.
    sendIntentTask.setResult(null);
    ShadowLooper.idleMainLooper();

    assertThat(startServiceTask.getResult()).isEqualTo(ServiceStarter.SUCCESS);
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testStartMessagingService_ForegroundBindWithWakeLock() {
    setWakeLockPermission(true);
    setStartServiceFails();
    Intent intent = new Intent(ACTION_FCM_MESSAGE);
    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

    Task<Integer> startServiceTask = processor.startMessagingService(context, intent);

    // Should return immediately with SUCCESS, bind to the Service, and acquire a WakeLock.
    fakeExecutorService.runAll();
    assertThat(startServiceTask.isComplete()).isTrue();
    assertThat(startServiceTask.getResult())
        .isEqualTo(ServiceStarter.ERROR_ILLEGAL_STATE_EXCEPTION_FALLBACK_TO_BIND);
    verify(mockConnection).sendIntent(any(Intent.class));
    assertThat(ShadowPowerManager.getLatestWakeLock()).isNotNull();
    assertThat(ShadowPowerManager.getLatestWakeLock().isHeld()).isTrue();

    // After the message has been handled, the WakeLock should be released.
    sendIntentTask.setResult(null);
    ShadowLooper.idleMainLooper();

    assertThat(ShadowPowerManager.getLatestWakeLock().isHeld()).isFalse();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testStartMessagingService_ForegroundBindNoWakeLock() {
    setWakeLockPermission(false);
    setStartServiceFails();
    Intent intent = new Intent(ACTION_FCM_MESSAGE);
    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

    Task<Integer> startServiceTask = processor.startMessagingService(context, intent);

    // Should return immediately with SUCCESS, bind to the Service, and not acquire a WakeLock.
    fakeExecutorService.runAll();
    assertThat(startServiceTask.isComplete()).isTrue();
    assertThat(startServiceTask.getResult())
        .isEqualTo(ServiceStarter.ERROR_ILLEGAL_STATE_EXCEPTION_FALLBACK_TO_BIND);
    verify(mockConnection).sendIntent(any(Intent.class));
    assertThat(ShadowPowerManager.getLatestWakeLock()).isNull();
  }

  private void setSubjectToBackgroundCheck() {
    // Subject to background check when run on Android O and targetSdkVersion set to O.
    context.getApplicationInfo().targetSdkVersion = VERSION_CODES.O;
  }

  private void setWakeLockPermission(boolean permission) {
    when(serviceStarter.hasWakeLockPermission(any(Context.class))).thenReturn(permission);
  }

  private void setStartServiceFails() {
    when(serviceStarter.startMessagingService(any(Context.class), any(Intent.class)))
        .thenReturn(ServiceStarter.ERROR_ILLEGAL_STATE_EXCEPTION);
  }
}
