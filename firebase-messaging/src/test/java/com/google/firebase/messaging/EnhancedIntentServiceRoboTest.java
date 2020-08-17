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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Service;
import android.content.Intent;
import android.os.PowerManager.WakeLock;
import androidx.collection.ArrayMap;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.iid.WakeLockHolder;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPowerManager;

/** Tests for EnhancedIntentService */
@RunWith(RobolectricTestRunner.class)
public class EnhancedIntentServiceRoboTest {

  private static final String EXTRA_ORIGINAL_INTENT = "originalIntent";

  private static final int NO_FLAGS = 0;

  private WakeLock wakeLock;

  /**
   * Acquires the wake lock and execute onStartCommand of the provided service and returns the same.
   */
  private static int executeServiceOnStartCommandGrabbingWakeLock(
      TestEnhancedIntentService service, Intent intent, int flags, int startId) {
    // Simulates acquiring since we are not starting service through
    // com.google.firebase.iid.ServiceStarter
    WakeLockHolder.acquireWakeLock(intent, Long.MAX_VALUE);
    return service.onStartCommand(intent, flags, startId);
  }

  private static void verifyHandleIntent(Intent originalIntent, Intent handleIntent) {
    assertNotNull(handleIntent);
    assertEquals(originalIntent, handleIntent.getParcelableExtra(EXTRA_ORIGINAL_INTENT));
  }

  @Before
  public void setUp() {
    WakeLockHolder.reset();
    WakeLockHolder.initWakeLock(ApplicationProvider.getApplicationContext());
    wakeLock = ShadowPowerManager.getLatestWakeLock();
  }

  /**
   * Test that handleIntent is invoked and only after it has finished execution that stopSelf is
   * called, and the wakelock is released.
   */
  @Test
  public void testHandleIntent_blocking() throws Exception {
    TestEnhancedIntentService service = new TestEnhancedIntentService();
    Intent originalIntent = new Intent();
    service.blockOnIntent(originalIntent);

    assertEquals(
        Service.START_REDELIVER_INTENT,
        executeServiceOnStartCommandGrabbingWakeLock(
            service, originalIntent, NO_FLAGS, 1 /* startId */));
    verifyHandleIntent(originalIntent, service.popHandleIntentCall());
    assertNull(service.popStopSelfCall());
    assertThat(wakeLock.isHeld()).isTrue();

    service.unblockIntent(originalIntent);
    assertEquals(Integer.valueOf(1), service.popStopSelfCall());
    assertThat(wakeLock.isHeld()).isFalse();
  }

  /**
   * Test that for two concurrent intents that invocation order, wake lock releasing and stopSelf
   * calls are correct.
   */
  @Test
  public void testHandleIntent_multipleIntents() throws Exception {
    TestEnhancedIntentService service = new TestEnhancedIntentService();
    Intent intent1 = new Intent("1");
    Intent intent2 = new Intent("2");
    service.blockOnIntent(intent1);
    service.blockOnIntent(intent2);

    assertEquals(
        Service.START_REDELIVER_INTENT,
        executeServiceOnStartCommandGrabbingWakeLock(service, intent1, NO_FLAGS, 1 /* startId */));
    assertEquals(
        Service.START_REDELIVER_INTENT,
        executeServiceOnStartCommandGrabbingWakeLock(service, intent2, NO_FLAGS, 2 /* startId */));

    verifyHandleIntent(intent1, service.popHandleIntentCall());
    // intents are handled on a single thread, so intent2 should not be handled
    assertNull(service.popHandleIntentCall());
    assertNull(service.popStopSelfCall());
    assertThat(wakeLock.isHeld()).isTrue();

    service.unblockIntent(intent1);
    verifyHandleIntent(intent2, service.popHandleIntentCall());
    assertThat(wakeLock.isHeld()).isTrue();
    assertNull(service.popStopSelfCall()); // Still blocking on intent2
    assertThat(wakeLock.isHeld()).isTrue();

    service.unblockIntent(intent2);
    assertEquals(Integer.valueOf(2), service.popStopSelfCall());
    assertThat(wakeLock.isHeld()).isFalse();
  }

  /**
   * Verify handling an intent on the main thread means the background handleIntent is not invoked,
   * and that wake lock releasing and stopSelf calls are correct.
   */
  @Test
  public void testHandleIntentOnMainThread() throws Exception {
    final Intent[] handleIntent = {null};
    TestEnhancedIntentService service =
        new TestEnhancedIntentService() {
          @Override
          public boolean handleIntentOnMainThread(Intent intent) {
            assertNull("handleIntentOnMainThread called multiple times", handleIntent[0]);
            handleIntent[0] = intent;
            return true;
          }
        };

    Intent originalIntent = new Intent();
    assertEquals(
        Service.START_NOT_STICKY,
        executeServiceOnStartCommandGrabbingWakeLock(
            service, originalIntent, NO_FLAGS, 1 /* startId */));
    verifyHandleIntent(originalIntent, handleIntent[0]);
    assertEquals(Integer.valueOf(1), service.popStopSelfCall());
    assertThat(wakeLock.isHeld()).isFalse();
    assertNull(service.popHandleIntentCall());
  }

  /**
   * Verify a blocked intent on a background thread doesn't block an intent on the main thread from
   * being executed.
   */
  @Test
  public void testHandleIntents_mainAndBackgroundThreads() throws Exception {
    final Intent backgroundIntent = new Intent();
    final Intent mainThreadIntent = new Intent();
    final Intent[] receivedMainThreadIntent = {null};

    TestEnhancedIntentService service =
        new TestEnhancedIntentService() {
          @Override
          public boolean handleIntentOnMainThread(Intent intent) {
            if (intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT).equals(mainThreadIntent)) {
              receivedMainThreadIntent[0] = intent;
              return true;
            }
            return false;
          }
        };
    service.blockOnIntent(backgroundIntent);

    assertEquals(
        Service.START_REDELIVER_INTENT,
        executeServiceOnStartCommandGrabbingWakeLock(
            service, backgroundIntent, NO_FLAGS, 1 /* startId */));
    assertEquals(
        Service.START_NOT_STICKY,
        executeServiceOnStartCommandGrabbingWakeLock(
            service, mainThreadIntent, NO_FLAGS, 2 /* startId */));

    // handleIntent should be called for both background and main threads
    verifyHandleIntent(mainThreadIntent, receivedMainThreadIntent[0]);
    assertThat(wakeLock.isHeld()).isTrue();
    verifyHandleIntent(backgroundIntent, service.popHandleIntentCall());
    assertThat(wakeLock.isHeld()).isTrue();
    assertNull(service.popStopSelfCall());

    service.unblockIntent(backgroundIntent);
    assertEquals(Integer.valueOf(2), service.popStopSelfCall());
    assertThat(wakeLock.isHeld()).isFalse();
  }

  /**
   * Test that for a null start command intent that the service doesn't call handleIntent and stops
   * itself.
   */
  @Test
  public void testNullStartCommandIntent() throws Exception {
    TestEnhancedIntentService service =
        new TestEnhancedIntentService() {
          @Override
          public Intent getStartCommandIntent(Intent originalIntent) {
            return null;
          }
        };

    assertEquals(
        Service.START_NOT_STICKY,
        executeServiceOnStartCommandGrabbingWakeLock(
            service, new Intent(), NO_FLAGS, 1 /* startId */));
    assertNull(service.popHandleIntentCall());
    assertEquals(Integer.valueOf(1), service.popStopSelfCall());
    assertThat(wakeLock.isHeld()).isFalse();
  }

  static class TestEnhancedIntentService extends EnhancedIntentService {

    private static final int TIMEOUT_SECONDS = 1;

    private final Map<Intent, CountDownLatch> blockLatches = new ArrayMap<>();

    private final BlockingQueue<Intent> handleIntentCalls = new LinkedBlockingQueue<>();
    private final BlockingQueue<Integer> stopSelfCalls = new LinkedBlockingQueue<>();

    @Override
    protected Intent getStartCommandIntent(Intent originalIntent) {
      Intent newIntent = new Intent();
      newIntent.putExtra(EXTRA_ORIGINAL_INTENT, originalIntent);
      return newIntent;
    }

    @Override
    public void handleIntent(Intent intent) {
      handleIntentCalls.offer(intent);
      CountDownLatch latch = blockLatches.get(intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT));
      if (latch != null) {
        try {
          latch.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    boolean stopSelfResultHook(int startId) {
      stopSelfCalls.offer(startId);
      return super.stopSelfResultHook(startId);
    }

    Intent popHandleIntentCall() throws InterruptedException {
      return handleIntentCalls.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    Integer popStopSelfCall() throws InterruptedException {
      return stopSelfCalls.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    void blockOnIntent(Intent intent) {
      blockLatches.put(intent, new CountDownLatch(1));
    }

    void unblockIntent(Intent intent) {
      blockLatches.get(intent).countDown();
    }
  }
}
