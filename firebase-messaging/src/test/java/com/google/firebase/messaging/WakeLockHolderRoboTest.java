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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.os.PowerManager.WakeLock;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.TaskCompletionSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPowerManager;

@RunWith(RobolectricTestRunner.class)
public class WakeLockHolderRoboTest {

  private Application context;

  @Before
  public void setUp() {
    WakeLockHolder.reset();
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void testStartWakefulService_InitsWakeLock() throws Exception {
    WakeLockHolder.startWakefulService(context, newIntent("Any_Action"));
    assertThat(ShadowPowerManager.getLatestWakeLock()).isNotNull();
  }

  @Test
  public void testStartWakefulService_AcquiresWakeLock() throws Exception {
    Intent intent = newIntent("Any_Action");

    WakeLockHolder.startWakefulService(context, intent);

    assertThat(ShadowPowerManager.getLatestWakeLock().isHeld()).isTrue();
  }

  @Test
  public void testSendWakefulServiceIntent_AcquiresWakeLock() {
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    WithinAppServiceConnection mockConnection = mock(WithinAppServiceConnection.class);
    when(mockConnection.sendIntent(any(Intent.class))).thenReturn(taskCompletionSource.getTask());
    WakeLockHolder.sendWakefulServiceIntent(context, mockConnection, new Intent());

    assertThat(ShadowPowerManager.getLatestWakeLock()).isNotNull();
    assertThat(ShadowPowerManager.getLatestWakeLock().isHeld()).isTrue();
  }

  @Test
  public void testSendWakefulServiceIntent_ReleasesWakeLock() {
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    WithinAppServiceConnection mockConnection = mock(WithinAppServiceConnection.class);
    when(mockConnection.sendIntent(any(Intent.class))).thenReturn(taskCompletionSource.getTask());
    WakeLockHolder.sendWakefulServiceIntent(context, mockConnection, new Intent());

    // Verify that the WakeLock is released once the Intent has been handled by the Service.
    WakeLock wakeLock = ShadowPowerManager.getLatestWakeLock();
    taskCompletionSource.setResult(null);

    ShadowLooper.idleMainLooper();
    assertThat(wakeLock.isHeld()).isFalse();
  }

  @Test
  public void testCompleteWakefulIntent_ReleasesWakeLockIfPresent() throws Exception {
    Intent intent = newIntent("Any_Action");

    WakeLockHolder.startWakefulService(context, intent);
    WakeLock wl = ShadowPowerManager.getLatestWakeLock();
    WakeLockHolder.completeWakefulIntent(intent);

    assertThat(wl.isHeld()).isFalse();
  }

  @Test
  public void testCompleteWakefulIntent_ReleasesWakeLockMultipleTimesTest() throws Exception {
    Intent intent1 = newIntent("1");
    WakeLockHolder.startWakefulService(context, intent1);
    WakeLock wl = ShadowPowerManager.getLatestWakeLock();
    assertThat(wl.isHeld()).isTrue(); // WL Count = 1

    Intent intent2 = newIntent("2");
    WakeLockHolder.startWakefulService(context, intent2);
    assertThat(wl.isHeld()).isTrue(); // WL Count = 2

    Intent intent3 = newIntent("3");
    WakeLockHolder.startWakefulService(context, intent3);
    assertThat(wl.isHeld()).isTrue(); // WL Count = 3

    WakeLockHolder.completeWakefulIntent(intent1);
    assertThat(wl.isHeld()).isTrue(); // WL Count = 2

    WakeLockHolder.completeWakefulIntent(intent2);
    assertThat(wl.isHeld()).isTrue(); // WL Count = 1

    Intent intent4 = newIntent("4");
    WakeLockHolder.startWakefulService(context, intent4);
    assertThat(wl.isHeld()).isTrue(); // WL Count = 2

    WakeLockHolder.completeWakefulIntent(intent3);
    assertThat(wl.isHeld()).isTrue(); // WL Count = 1

    WakeLockHolder.completeWakefulIntent(intent4);
    assertThat(wl.isHeld()).isFalse(); // WL Count = 0
  }

  @Test
  public void testCompleteWakefulIntent_doesNotCrashOnDuplicateCalls() throws Exception {
    Intent intent = newIntent("1");
    WakeLockHolder.startWakefulService(context, intent);
    WakeLock wl = ShadowPowerManager.getLatestWakeLock();
    WakeLockHolder.completeWakefulIntent(intent); // Normal case
    assertThat(wl.isHeld()).isFalse();
    WakeLockHolder.completeWakefulIntent(intent); // Should not crash when No wake lock present
  }

  @Test
  public void testStartWakefulService_createsWakefulIntent() throws Exception {
    Intent intent = newIntent(null);

    WakeLockHolder.startWakefulService(context, intent);

    assertThat(WakeLockHolder.isWakefulIntent(intent)).isTrue();
  }

  @Test
  public void testCompleteWakefulIntent_removesWakefulMarker() throws Exception {
    Intent intent = newIntent(null);

    WakeLockHolder.startWakefulService(context, intent);
    WakeLockHolder.completeWakefulIntent(intent);

    assertThat(WakeLockHolder.isWakefulIntent(intent)).isFalse();
  }

  @Test
  public void testOnlyWakefulIntentsCauseWakeLockToBeReleased() throws Exception {
    Intent intent = newIntent("1");
    WakeLockHolder.startWakefulService(context, intent);
    WakeLock wl = ShadowPowerManager.getLatestWakeLock();
    assertThat(wl.isHeld()).isTrue();

    WakeLockHolder.completeWakefulIntent(newIntent("2"));
    assertThat(wl.isHeld()).isTrue();

    WakeLockHolder.completeWakefulIntent(intent);
    assertThat(wl.isHeld()).isFalse();
  }

  @Test
  public void testStartWakefulService_multipleCallsOnlyAcquiresWakeLockOnce() throws Exception {
    Intent intent = newIntent("1");
    WakeLockHolder.startWakefulService(context, intent); // Called once
    WakeLock wl = ShadowPowerManager.getLatestWakeLock();
    assertThat(wl.isHeld()).isTrue();

    WakeLockHolder.startWakefulService(context, intent); // Called twice
    assertThat(wl.isHeld()).isTrue();

    WakeLockHolder.completeWakefulIntent(intent);
    // Should be false since the same intent should not acquire wakelock more than once.
    assertThat(wl.isHeld()).isFalse();
  }

  private Intent newIntent(String action) {
    return new Intent(action).setPackage(context.getPackageName());
  }
}
