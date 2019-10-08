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

package com.google.android.datatransport.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.SchedulerConfig;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import com.google.android.datatransport.runtime.scheduling.locking.Locker;
import java.nio.charset.Charset;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class SchedulerIntegrationTest {
  private static final String TEST_KEY = "test";
  private static final String TEST_VALUE = "test-value";
  private static final String testTransport = "testTransport";
  private static final Encoding PROTOBUF_ENCODING = Encoding.of("proto");

  private final TransportBackend mockBackend = mock(TransportBackend.class);
  private final TransportBackend mockBackend2 = mock(TransportBackend.class);
  private final BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private final Context context = InstrumentationRegistry.getInstrumentation().getContext();
  private final Uploader mockUploader = mock(Uploader.class);
  private final Locker<Boolean> locker = new Locker<>();

  @Rule
  public final TransportRuntimeRule runtimeRule =
      new TransportRuntimeRule(
          DaggerTestRuntimeComponent.builder()
              .setApplicationContext(context)
              .setUploader(mockUploader)
              .setBackendRegistry(mockRegistry)
              .setSchedulerConfig(
                  SchedulerConfig.builder()
                      .setClock(() -> 3)
                      .addConfig(
                          Priority.DEFAULT,
                          SchedulerConfig.ConfigValue.builder()
                              .setDelta(500)
                              .setMaxAllowedDelay(100000)
                              .build())
                      .addConfig(
                          Priority.VERY_LOW,
                          SchedulerConfig.ConfigValue.builder()
                              .setDelta(500)
                              .setMaxAllowedDelay(100000)
                              .build())
                      .addConfig(
                          Priority.HIGHEST,
                          SchedulerConfig.ConfigValue.builder()
                              .setDelta(500)
                              .setMaxAllowedDelay(100000)
                              .build())
                      .build())
              .setEventClock(() -> 3)
              .setUptimeClock(() -> 1)
              .build());

  @Before
  public void setUp() {
    when(mockBackend.decorate(any()))
        .thenAnswer(
            (Answer<EventInternal>)
                invocation ->
                    invocation
                        .<EventInternal>getArgument(0)
                        .toBuilder()
                        .addMetadata(TEST_KEY, TEST_VALUE)
                        .build());
    when(mockBackend2.decorate(any()))
        .thenAnswer(
            (Answer<EventInternal>)
                invocation ->
                    invocation
                        .<EventInternal>getArgument(0)
                        .toBuilder()
                        .addMetadata(TEST_KEY, TEST_VALUE)
                        .build());
    /*
     We need the locker to ensure that the job service is called before we continue our testing.
     The await present in the tests wait until the uploader's upload is called so that we know
     for sure that the service is already run.
     Also we would need to make sure that the runnable is being run. Because in
     JobInfoSchedulerService if we don't communicate that the job is complete. Then the
     JobScheduler will try to reschedule the service and will cause an exception for the retry
     though the tests are going to pass. In tests testing multiple uploader calls not running the
     runnable is going to cause significant delays.
    */
    doAnswer(
            (Answer<Void>)
                i -> {
                  locker.setResult(true);
                  i.<Runnable>getArgument(2).run();
                  return null;
                })
        .when(mockUploader)
        .upload(any(), anyInt(), any());
  }

  private String generateBackendName() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  @Test
  public void scheduler_whenEventScheduledForFirstTime_shouldUpload() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    String mockBackendName = generateBackendName();
    TransportContext context = TransportContext.builder().setBackendName(mockBackendName).build();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofData("Data");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(PROTOBUF_ENCODING, "Data".getBytes(Charset.defaultCharset())))
            .build();
    transport.send(stringEvent);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    locker.await();
    verify(mockUploader, times(1)).upload(eq(context), eq(1), any());
  }

  @Test
  public void scheduler_whenEventsScheduledWithSameBackend_shouldUploadOnce() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    String mockBackendName = generateBackendName();
    TransportContext context = TransportContext.builder().setBackendName(mockBackendName).build();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofData("Data");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(PROTOBUF_ENCODING, "Data".getBytes(Charset.defaultCharset())))
            .build();
    Event<String> stringEvent2 = Event.ofData("Data2");
    EventInternal expectedEvent2 =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(PROTOBUF_ENCODING, "Data2".getBytes(Charset.defaultCharset())))
            .build();
    transport.send(stringEvent);
    transport.send(stringEvent2);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    verify(mockBackend, times(1)).decorate(eq(expectedEvent2));
    locker.await();
    verify(mockUploader, times(1)).upload(eq(context), eq(1), any());
  }

  @Test
  public void scheduler_whenEventsScheduledWithDifferentBackends_shouldUploadTwice() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    String firstBackendName = generateBackendName();
    String secondBackendName = generateBackendName();
    TransportContext firstContext =
        TransportContext.builder().setBackendName(firstBackendName).build();
    TransportContext secondContext =
        TransportContext.builder().setBackendName(secondBackendName).build();
    when(mockRegistry.get(firstBackendName)).thenReturn(mockBackend);
    when(mockRegistry.get(secondBackendName)).thenReturn(mockBackend2);
    TransportFactory factory = runtime.newFactory(firstBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofData("Data");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(PROTOBUF_ENCODING, "Data".getBytes(Charset.defaultCharset())))
            .build();
    transport.send(stringEvent);
    TransportFactory factory2 = runtime.newFactory(secondBackendName);
    Transport<String> transport2 =
        factory2.getTransport(testTransport, String.class, String::getBytes);
    transport2.send(stringEvent);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    verify(mockBackend2, times(1)).decorate(eq(expectedEvent));
    locker.await();
    locker.await();
    verify(mockUploader, times(1)).upload(eq(firstContext), eq(1), any());
    verify(mockUploader, times(1)).upload(eq(secondContext), eq(1), any());
  }
}
