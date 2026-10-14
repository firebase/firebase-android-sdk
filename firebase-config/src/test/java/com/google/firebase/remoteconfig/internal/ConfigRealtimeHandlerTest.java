// Copyright 2026 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link ConfigRealtimeHandler}.
 *
 * <p>Verifies that a config update listener can remove itself from inside its own {@code onError}
 * callback without throwing a {@link java.util.ConcurrentModificationException}, and that the
 * other registered listeners are still notified (see GitHub issue #5439).
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ConfigRealtimeHandlerTest {

  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor();
  private ConfigRealtimeHandler configRealtimeHandler;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    ConfigSharedPrefsClient sharedPrefsClient =
        new ConfigSharedPrefsClient(
            context.getSharedPreferences("realtime_handler_test", Context.MODE_PRIVATE));
    // 7 previous failed streams -> only 1 connection attempt left; no active backoff.
    sharedPrefsClient.setRealtimeBackoffMetadata(7, new Date(0L));

    // Simulate being offline: every stream connection attempt fails.
    FirebaseInstallationsApi firebaseInstallations = mock(FirebaseInstallationsApi.class);
    when(firebaseInstallations.getToken(false))
        .thenReturn(Tasks.forException(new IOException("offline")));
    when(firebaseInstallations.getId()).thenReturn(Tasks.forException(new IOException("offline")));

    configRealtimeHandler =
        new ConfigRealtimeHandler(
            mock(FirebaseApp.class),
            firebaseInstallations,
            mock(ConfigFetchHandler.class),
            mock(ConfigCacheClient.class),
            context,
            "firebase",
            sharedPrefsClient,
            scheduledExecutorService);
  }

  @After
  public void tearDown() {
    scheduledExecutorService.shutdownNow();
  }

  @Test
  public void listenerRemovesItselfInOnError_otherListenersStillNotified() throws Exception {
    AtomicBoolean firstListenerCalled = new AtomicBoolean(false);
    AtomicBoolean secondListenerCalled = new AtomicBoolean(false);
    AtomicReference<ConfigUpdateListenerRegistration> firstRegistration = new AtomicReference<>();

    // Register both listeners while backgrounded so no connection starts in between.
    configRealtimeHandler.setBackgroundState(true);
    firstRegistration.set(
        configRealtimeHandler.addRealtimeConfigUpdateListener(
            new ConfigUpdateListener() {
              @Override
              public void onUpdate(@NonNull ConfigUpdate configUpdate) {}

              @Override
              public void onError(@NonNull FirebaseRemoteConfigException error) {
                firstListenerCalled.set(true);
                // Common app pattern: stop listening on error.
                firstRegistration.get().remove();
              }
            }));
    ConfigUpdateListenerRegistration unusedSecondRegistration =
        configRealtimeHandler.addRealtimeConfigUpdateListener(
            new ConfigUpdateListener() {
              @Override
              public void onUpdate(@NonNull ConfigUpdate configUpdate) {}

              @Override
              public void onError(@NonNull FirebaseRemoteConfigException error) {
                secondListenerCalled.set(true);
              }
            });

    // App comes to the foreground: last connection attempt fails -> errors propagated.
    configRealtimeHandler.setBackgroundState(false);
    for (int i = 0; i < 100 && !firstListenerCalled.get(); i++) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(50);
    }
    for (int i = 0; i < 10 && !secondListenerCalled.get(); i++) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(50);
    }

    assertWithMessage("First listener was never notified").that(firstListenerCalled.get()).isTrue();
    assertWithMessage("Second listener was not notified after first listener removed itself")
        .that(secondListenerCalled.get())
        .isTrue();
  }
}
