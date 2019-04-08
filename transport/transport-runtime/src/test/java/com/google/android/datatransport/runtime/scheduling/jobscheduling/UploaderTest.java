// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(sdk = {LOLLIPOP})
@RunWith(RobolectricTestRunner.class)
public class UploaderTest {
  private final SynchronizationGuard guard =
      new SynchronizationGuard() {
        @Override
        public <T> T runCriticalSection(long lockTimeoutMs, CriticalSection<T> criticalSection) {
          return criticalSection.execute();
        }
      };
  private final String BACKEND_NAME = "backend1";
  private final TransportContext transportContext =
      TransportContext.builder().setBackendName(BACKEND_NAME).build();
  private final Context context = RuntimeEnvironment.application;
  private final EventStore store = spy(new InMemoryEventStore());
  private BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private TransportBackend mockBackend = mock(TransportBackend.class);
  private WorkScheduler mockScheduler = mock(WorkScheduler.class);
  private Runnable mockRunnable = mock(Runnable.class);
  private Uploader uploader =
      spy(new Uploader(context, mockRegistry, store, mockScheduler, Runnable::run, guard, () -> 2));
  private static final EventInternal EVENT =
      EventInternal.builder()
          .setTransportName("42")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setPayload("Hello".getBytes())
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();

  @Before
  public void setUp() {
    when(mockRegistry.get(BACKEND_NAME)).thenReturn(mockBackend);
    store.persist(transportContext, EVENT);
  }

  @Test
  public void upload_noNetwork() {
    when(uploader.isNetworkAvailable()).thenReturn(Boolean.FALSE);
    uploader.upload(BACKEND_NAME, 1, mockRunnable);
    // Scheduler must be called with the attempt number incremented.
    verify(mockScheduler, times(1)).schedule(transportContext, 2);
    verify(mockRunnable, times(1)).run();
  }

  @Test
  public void upload_yesNetwork() {
    when(mockBackend.send(any()))
        .thenReturn(BackendResponse.create(BackendResponse.Status.OK, 1000));
    when(uploader.isNetworkAvailable()).thenReturn(Boolean.TRUE);
    uploader.upload(BACKEND_NAME, 1, mockRunnable);
    verify(uploader, times(1)).logAndUpdateState(transportContext, 1);
    verify(mockRunnable, times(1)).run();
  }

  @Test
  public void logAndUpdateStatus_okResponse() {
    when(mockBackend.send(any()))
        .thenReturn(BackendResponse.create(BackendResponse.Status.OK, 1000));
    uploader.logAndUpdateState(transportContext, 1);
    verify(store, times(1)).recordSuccess(any());
    verify(store, times(1)).recordNextCallTime(transportContext, 1002);
  }

  @Test
  public void logAndUpdateStatus_nontransientResponse() {
    when(mockBackend.send(any()))
        .thenReturn(BackendResponse.create(BackendResponse.Status.NONTRANSIENT_ERROR, -1));
    uploader.logAndUpdateState(transportContext, 1);
    verify(store, times(1)).recordSuccess(any());
  }

  @Test
  public void logAndUpdateStatus_transientReponse() {
    when(mockBackend.send(any()))
        .thenReturn(BackendResponse.create(BackendResponse.Status.TRANSIENT_ERROR, -1));
    uploader.logAndUpdateState(transportContext, 1);
    verify(store, times(1)).recordFailure(any());
    verify(mockScheduler, times(1)).schedule(transportContext, 2);
  }
}
