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

import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.PersistedEvent;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import java.nio.charset.Charset;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(sdk = {LOLLIPOP})
@RunWith(RobolectricTestRunner.class)
public class UploaderTest {
  private static final SynchronizationGuard guard =
      new SynchronizationGuard() {
        @Override
        public <T> T runCriticalSection(CriticalSection<T> criticalSection) {
          return criticalSection.execute();
        }
      };
  private static final String BACKEND_NAME = "backend1";
  private static final TransportContext TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName(BACKEND_NAME).build();
  private static final EventInternal EVENT =
      EventInternal.builder()
          .setTransportName("42")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setEncodedPayload(
              new EncodedPayload(Encoding.of("proto"), "Hello".getBytes(Charset.defaultCharset())))
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();

  private final EventStore store = spy(new InMemoryEventStore());
  private BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private TransportBackend mockBackend = mock(TransportBackend.class);
  private WorkScheduler mockScheduler = mock(WorkScheduler.class);
  private Runnable mockRunnable = mock(Runnable.class);
  private Uploader uploader =
      spy(
          new Uploader(
              RuntimeEnvironment.application,
              mockRegistry,
              store,
              mockScheduler,
              Runnable::run,
              guard,
              () -> 2));

  @Before
  public void setUp() {
    when(mockRegistry.get(BACKEND_NAME)).thenReturn(mockBackend);
    store.persist(TRANSPORT_CONTEXT, EVENT);
  }

  @Test
  public void upload_noNetwork() {
    when(uploader.isNetworkAvailable()).thenReturn(Boolean.FALSE);
    uploader.upload(TRANSPORT_CONTEXT, 1, mockRunnable);
    // Scheduler must be called with the attempt number incremented.
    verify(mockScheduler, times(1)).schedule(TRANSPORT_CONTEXT, 2);
    verify(mockRunnable, times(1)).run();
  }

  @Test
  public void upload_yesNetwork() {
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));
    when(uploader.isNetworkAvailable()).thenReturn(Boolean.TRUE);
    uploader.upload(TRANSPORT_CONTEXT, 1, mockRunnable);
    verify(uploader, times(1)).logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(mockRunnable, times(1)).run();
  }

  @Test
  public void logAndUpdateStatus_okResponse() {
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(store, times(1)).recordSuccess(any());
    verify(store, times(1)).recordNextCallTime(TRANSPORT_CONTEXT, 1002);
  }

  @Test
  public void logAndUpdateStatus_nontransientResponse() {
    when(mockBackend.send(any())).thenReturn(BackendResponse.fatalError());
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(store, times(1)).recordSuccess(any());
  }

  @Test
  public void logAndUpdateStatus_transientReponse() {
    when(mockBackend.send(any())).thenReturn(BackendResponse.transientError());
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(store, times(1)).recordFailure(any());
    verify(mockScheduler, times(1)).schedule(TRANSPORT_CONTEXT, 2);
  }

  @Test
  public void logAndUpdateStatus_whenMoreEventsAvailableInStore_shouldReschedule() {
    when(mockBackend.send(any()))
        .then(
            (Answer<BackendResponse>)
                invocation -> {
                  // store a new event
                  store.persist(TRANSPORT_CONTEXT, EVENT);
                  return BackendResponse.ok(1000);
                });
    Iterable<PersistedEvent> persistedEvents = store.loadBatch(TRANSPORT_CONTEXT);
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(mockScheduler, times(1)).schedule(TRANSPORT_CONTEXT, 1);
  }
}
