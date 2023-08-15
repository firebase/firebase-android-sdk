// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.transport;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageInfo;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.shadows.ShadowPreconditions;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Constants.CounterNames;
import com.google.firebase.perf.v1.AndroidMemoryReading;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.CpuMetricReading;
import com.google.firebase.perf.v1.GaugeMetadata;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import com.google.firebase.perf.v1.PerfMetric;
import com.google.firebase.perf.v1.PerfSession;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeScheduledExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

/** Unit tests for {@link TransportManager}. */
@Config(shadows = ShadowPreconditions.class)
@RunWith(RobolectricTestRunner.class)
public class TransportManagerTest extends FirebasePerformanceTestBase {

  private static final String FAKE_FIREBASE_INSTALLATIONS_ID = "fakeFID";

  private TransportManager testTransportManager;
  private FakeScheduledExecutorService fakeExecutorService;

  @Mock private FirebaseInstallationsApi mockFirebaseInstallationsApi;
  @Mock private Provider<TransportFactory> mockFlgTransportFactoryProvider;
  @Mock private ConfigResolver mockConfigResolver;
  @Mock private RateLimiter mockRateLimiter;
  @Mock private AppStateMonitor mockAppStateMonitor;
  @Mock private FlgTransport mockFlgTransport;
  @Captor private ArgumentCaptor<PerfMetric> perfMetricArgumentCaptor;

  @Before
  public void setUp() {
    initMocks(this);

    when(mockConfigResolver.isPerformanceMonitoringEnabled()).thenReturn(true);
    mockInstallationsGetId(FAKE_FIREBASE_INSTALLATIONS_ID);
    when(mockRateLimiter.isEventSampled(ArgumentMatchers.any())).thenReturn(true);
    when(mockRateLimiter.isEventRateLimited(ArgumentMatchers.any())).thenReturn(false);

    fakeExecutorService = new FakeScheduledExecutorService();
    initializeTransport(true);
    fakeExecutorService.runAll();
  }

  @After
  public void tearDown() {
    reset(mockFirebaseInstallationsApi);
    FirebaseApp.clearInstancesForTest();
  }

  // region Transport Initialization

  @Test
  public void validTraceMetric_transportNotInitialized_getLoggedAfterInitialization() {
    initializeTransport(false);
    TraceMetric validTrace = createValidTraceMetric();
    testTransportManager.log(validTrace, ApplicationProcessState.BACKGROUND);

    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(1);

    initializeTransport(true);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));

    assertThat(loggedPerfMetric.getTraceMetric()).isEqualTo(validTrace);
    validateApplicationInfo(loggedPerfMetric, ApplicationProcessState.BACKGROUND);
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();
  }

  @Test
  public void validNetworkMetric_transportNotInitialized_getLoggedAfterInitialization() {
    initializeTransport(false);
    NetworkRequestMetric validNetworkRequest = createValidNetworkRequestMetric();
    testTransportManager.log(validNetworkRequest, ApplicationProcessState.BACKGROUND);

    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(1);

    initializeTransport(true);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));

    assertThat(loggedPerfMetric.getNetworkRequestMetric()).isEqualTo(validNetworkRequest);
    validateApplicationInfo(loggedPerfMetric, ApplicationProcessState.BACKGROUND);
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();
  }

  @Test
  public void validGaugeMetric_transportNotInitialized_getLoggedAfterInitialization() {
    initializeTransport(false);
    GaugeMetric validGauge = createValidGaugeMetric();
    testTransportManager.log(validGauge, ApplicationProcessState.BACKGROUND);

    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(1);

    initializeTransport(true);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));

    assertThat(loggedPerfMetric.getGaugeMetric()).isEqualTo(validGauge);
    validateApplicationInfo(loggedPerfMetric, ApplicationProcessState.BACKGROUND);
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();
  }

  @Test
  public void invalidTraceMetric_transportNotInitialized_notLoggedAfterInitialization() {
    initializeTransport(false);
    testTransportManager.log(createInvalidTraceMetric(), ApplicationProcessState.BACKGROUND);

    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(1);

    initializeTransport(true);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();
  }

  @Test
  public void invalidNetworkMetric_transportNotInitialized_notLoggedAfterInitialization() {
    initializeTransport(false);
    testTransportManager.log(
        createInvalidNetworkRequestMetric(), ApplicationProcessState.BACKGROUND);

    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(1);

    initializeTransport(true);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();
  }

  @Test
  public void invalidGaugeMetric_transportNotInitialized_notLoggedAfterInitialization() {
    initializeTransport(false);
    testTransportManager.log(createInValidGaugeMetric(), ApplicationProcessState.BACKGROUND);

    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(1);

    initializeTransport(true);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();
  }

  @Test
  public void
      logMultipleEvents_transportNotInitialized_validEventsGetLoggedInOrderAfterInitialization() {
    initializeTransport(false);

    TraceMetric validTrace = createValidTraceMetric();
    testTransportManager.log(validTrace, ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();

    TraceMetric invalidTrace = createInvalidTraceMetric();
    testTransportManager.log(invalidTrace, ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();

    NetworkRequestMetric validNetworkRequest = createValidNetworkRequestMetric();
    testTransportManager.log(validNetworkRequest, ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();

    NetworkRequestMetric invalidNetworkRequest = createInvalidNetworkRequestMetric();
    testTransportManager.log(invalidNetworkRequest, ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();

    GaugeMetric validGauge = createValidGaugeMetric();
    testTransportManager.log(validGauge);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();

    GaugeMetric invalidGauge = createInValidGaugeMetric();
    testTransportManager.log(invalidGauge);
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();

    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(6);

    initializeTransport(true);

    clearLastLoggedEvents();
    fakeExecutorService.runNext();
    PerfMetric loggedValidTrace = getLastLoggedEvent(times(1));
    assertThat(loggedValidTrace.getTraceMetric()).isEqualTo(validTrace);
    validateApplicationInfo(loggedValidTrace, ApplicationProcessState.BACKGROUND);

    clearLastLoggedEvents();
    fakeExecutorService.runNext();
    PerfMetric loggedInvalidTrace = getLastLoggedEvent(never());
    assertThat(loggedInvalidTrace).isNull();

    clearLastLoggedEvents();
    fakeExecutorService.runNext();
    PerfMetric loggedValidNetworkRequest = getLastLoggedEvent(times(1));
    assertThat(loggedValidNetworkRequest.getNetworkRequestMetric()).isEqualTo(validNetworkRequest);
    validateApplicationInfo(loggedValidNetworkRequest, ApplicationProcessState.FOREGROUND);

    clearLastLoggedEvents();
    fakeExecutorService.runNext();
    PerfMetric loggedInValidNetworkRequest = getLastLoggedEvent(never());
    assertThat(loggedInValidNetworkRequest).isNull();

    clearLastLoggedEvents();
    fakeExecutorService.runNext();
    PerfMetric loggedValidGauge = getLastLoggedEvent(times(1));
    assertThat(loggedValidGauge.getGaugeMetric()).isEqualTo(validGauge);
    validateApplicationInfo(
        loggedValidGauge, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);

    clearLastLoggedEvents();
    fakeExecutorService.runNext();
    PerfMetric loggedInValidGauge = getLastLoggedEvent(never());
    assertThat(loggedInValidGauge).isNull();

    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();
  }

  @Test
  public void logMultipleTraces_transportNotInitialized_tracesAfterMaxCapAreNotQueued() {
    // 1. Transport is not initialized in the beginning
    initializeTransport(false);

    // 2. Log multiple Traces such that they are capped
    int maxTracesCacheSize = 50; // only 50 TraceMetric events are allowed to cache
    int totalTraceEvents = maxTracesCacheSize + 10;
    TraceMetric[] validTraces = new TraceMetric[totalTraceEvents];

    for (int i = 0; i < totalTraceEvents; i++) {
      validTraces[i] = createValidTraceMetric().toBuilder().setName("Trace - " + (i + 1)).build();
      testTransportManager.log(validTraces[i], ApplicationProcessState.FOREGROUND);
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 3. Even though we recorded "totalTraceEvents", events up-to "maxTracesCacheSize" are only
    // queued
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(maxTracesCacheSize);

    // 4. Initialize Transport
    initializeTransport(true);

    // 5. Consume all queued Traces and validate them
    for (int i = 0; i < maxTracesCacheSize; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
      PerfMetric loggedValidTrace = getLastLoggedEvent(times(1));
      assertThat(loggedValidTrace.getTraceMetric()).isEqualTo(validTraces[i]);
      validateApplicationInfo(loggedValidTrace, ApplicationProcessState.FOREGROUND);
    }

    // 6. Queue is all consumed after iterating "maxTracesCacheSize" events
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();

    // 7. No pending events
    clearLastLoggedEvents();
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void
      logMultipleNetworkRequests_transportNotInitialized_networkRequestsAfterMaxCapAreNotQueued() {
    // 1. Transport is not initialized in the beginning
    initializeTransport(false);

    // 2. Log multiple Network Requests such that they are capped
    int maxNetworkRequestsCacheSize =
        50; // only 50 NetworkRequestMetric events are allowed to cache
    int totalNetworkRequestEvents = maxNetworkRequestsCacheSize + 10;
    NetworkRequestMetric[] validNetworkRequests =
        new NetworkRequestMetric[totalNetworkRequestEvents];

    for (int i = 0; i < totalNetworkRequestEvents; i++) {
      validNetworkRequests[i] =
          createValidNetworkRequestMetric().toBuilder().setClientStartTimeUs(i + 1).build();
      testTransportManager.log(validNetworkRequests[i], ApplicationProcessState.FOREGROUND);
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 3. Even though we recorded "totalNetworkRequestEvents", events up-to
    // "maxNetworkRequestsCacheSize" are only queued
    assertThat(testTransportManager.getPendingEventsQueue().size())
        .isEqualTo(maxNetworkRequestsCacheSize);

    // 4. Initialize Transport
    initializeTransport(true);

    // 5. Consume all queued Network Requests and validate them
    for (int i = 0; i < maxNetworkRequestsCacheSize; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
      PerfMetric loggedValidNetworkRequest = getLastLoggedEvent(times(1));
      assertThat(loggedValidNetworkRequest.getNetworkRequestMetric())
          .isEqualTo(validNetworkRequests[i]);
      validateApplicationInfo(loggedValidNetworkRequest, ApplicationProcessState.FOREGROUND);
    }

    // 6. Queue is all consumed after iterating "maxNetworkRequestsCacheSize" events
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();

    // 7. No pending events
    clearLastLoggedEvents();
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void logMultipleGauges_transportNotInitialized_gaugesAfterMaxCapAreNotQueued() {
    // 1. Transport is not initialized in the beginning
    initializeTransport(false);

    // 2. Log multiple Gauges such that they are capped
    int maxGaugesCacheSize = 50; // only 50 GaugeMetric events are allowed to cache
    int totalGaugeEvents = maxGaugesCacheSize + 10;
    GaugeMetric[] validGauges = new GaugeMetric[totalGaugeEvents];

    for (int i = 0; i < totalGaugeEvents; i++) {
      validGauges[i] =
          createValidGaugeMetric().toBuilder().setSessionId("Session - " + (i + 1)).build();
      testTransportManager.log(validGauges[i], ApplicationProcessState.FOREGROUND);
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 3. Even though we recorded "totalGaugeEvents", events up-to "maxGaugesCacheSize" are only
    // queued
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(maxGaugesCacheSize);

    // 4. Initialize Transport
    initializeTransport(true);

    // 5. Consume all queued Gauges and validate them
    for (int i = 0; i < maxGaugesCacheSize; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
      PerfMetric loggedValidGauge = getLastLoggedEvent(times(1));
      assertThat(loggedValidGauge.getGaugeMetric()).isEqualTo(validGauges[i]);
      validateApplicationInfo(loggedValidGauge, ApplicationProcessState.FOREGROUND);
    }

    // 6. Queue is all consumed after iterating "maxGaugesCacheSize" events
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();

    // 7. No pending events
    clearLastLoggedEvents();
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void
      logTraces_fewTracesAfterOtherCappedEventsAndTransportNotInitialized_cappedEventsDoesNotCauseOtherEventsToCap() {
    // 1. Transport is not initialized in the beginning
    initializeTransport(false);

    // 2. Log multiple Network Requests (any one of PerfMetric event other than TraceMetric) such
    // that they are capped
    int maxNetworkRequestsCacheSize =
        50; // only 50 NetworkRequestMetric events are allowed to cache
    int totalNetworkRequestEvents = maxNetworkRequestsCacheSize + 10;

    for (int i = 0; i < totalNetworkRequestEvents; i++) {
      testTransportManager.log(
          createValidNetworkRequestMetric().toBuilder().setClientStartTimeUs(i + 1).build());
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 3. Even though we recorded "totalNetworkRequestEvents", events up-to
    // "maxNetworkRequestsCacheSize" are only
    // queued
    assertThat(testTransportManager.getPendingEventsQueue().size())
        .isEqualTo(maxNetworkRequestsCacheSize);

    // 4. Log few Traces such that they are under the max cap
    int totalTraceEvents = 20; // less than max cache for TraceMetric events
    TraceMetric[] validTraces = new TraceMetric[totalTraceEvents];

    for (int i = 0; i < totalTraceEvents; i++) {
      validTraces[i] = createValidTraceMetric().toBuilder().setName("Trace - " + (i + 1)).build();
      testTransportManager.log(validTraces[i], ApplicationProcessState.FOREGROUND);
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 5. All Traces are queued even after Network Requests are capped
    assertThat(testTransportManager.getPendingEventsQueue().size())
        .isEqualTo(maxNetworkRequestsCacheSize + totalTraceEvents);

    // 6. Initialize Transport
    initializeTransport(true);

    // 7. Consume all queued Network Requests
    for (int i = 0; i < maxNetworkRequestsCacheSize; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
    }

    // 8. Consume all queued Traces and validate them
    for (int i = 0; i < totalTraceEvents; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
      PerfMetric loggedValidTrace = getLastLoggedEvent(times(1));
      assertThat(loggedValidTrace.getTraceMetric()).isEqualTo(validTraces[i]);
      validateApplicationInfo(loggedValidTrace, ApplicationProcessState.FOREGROUND);
    }

    // 9. Queue is all consumed
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();

    // 10. No pending events
    clearLastLoggedEvents();
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void
      logNetworkRequests_fewNetworkRequestsAfterOtherCappedEventsAndTransportNotInitialized_cappedEventsDoesNotCauseOtherEventsToCap() {
    // 1. Transport is not initialized in the beginning
    initializeTransport(false);

    // 2. Log multiple Traces (any one of PerfMetric event other than NetworkRequestMetric) such
    // that they are capped
    int maxTracesCacheSize = 50; // only 50 TraceMetric events are allowed to cache
    int totalTraceEvents = maxTracesCacheSize + 10;

    for (int i = 0; i < totalTraceEvents; i++) {
      testTransportManager.log(
          createValidTraceMetric().toBuilder().setName("Trace - " + (i + 1)).build());
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 3. Even though we recorded "totalTraceEvents", events up-to "maxTracesCacheSize" are only
    // queued
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(maxTracesCacheSize);

    // 4. Log few Network Requests such that they are under the max cap
    int totalNetworkRequestEvents = 20; // less than max cache for NetworkRequestMetric events
    NetworkRequestMetric[] validNetworkRequests =
        new NetworkRequestMetric[totalNetworkRequestEvents];

    for (int i = 0; i < totalNetworkRequestEvents; i++) {
      validNetworkRequests[i] =
          createValidNetworkRequestMetric().toBuilder().setClientStartTimeUs(i + 1).build();
      testTransportManager.log(validNetworkRequests[i], ApplicationProcessState.FOREGROUND);
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 5. All NetworkRequests are queued even after Traces are capped
    assertThat(testTransportManager.getPendingEventsQueue().size())
        .isEqualTo(maxTracesCacheSize + totalNetworkRequestEvents);

    // 6. Initialize Transport
    initializeTransport(true);

    // 7. Consume all queued Traces
    for (int i = 0; i < maxTracesCacheSize; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
    }

    // 8. Consume all queued Network Requests and validate them
    for (int i = 0; i < totalNetworkRequestEvents; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
      PerfMetric loggedValidNetworkRequest = getLastLoggedEvent(times(1));
      assertThat(loggedValidNetworkRequest.getNetworkRequestMetric())
          .isEqualTo(validNetworkRequests[i]);
      validateApplicationInfo(loggedValidNetworkRequest, ApplicationProcessState.FOREGROUND);
    }

    // 9. Queue is all consumed
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();

    // 10. No pending events
    clearLastLoggedEvents();
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void
      logGauges_fewGaugesAfterOtherCappedEventsAndTransportNotInitialized_cappedEventsDoesNotCauseOtherEventsToCap() {
    // 1. Transport is not initialized in the beginning
    initializeTransport(false);

    // 2. Log multiple Traces (any one of PerfMetric event other than GaugeMetric) such that they
    // are capped
    int maxTracesCacheSize = 50; // only 50 TraceMetric events are allowed to cache
    int totalTraceEvents = maxTracesCacheSize + 10;

    for (int i = 0; i < totalTraceEvents; i++) {
      testTransportManager.log(
          createValidTraceMetric().toBuilder().setName("Trace - " + (i + 1)).build());
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 3. Even though we recorded "totalTraceEvents", events up-to "maxTracesCacheSize" are only
    // queued
    assertThat(testTransportManager.getPendingEventsQueue().size()).isEqualTo(maxTracesCacheSize);

    // 4. Log few Gauges such that they are under the max cap
    int totalGaugeEvents = 20; // less than max cache for GaugeMetric events
    GaugeMetric[] validGauges = new GaugeMetric[totalGaugeEvents];

    for (int i = 0; i < totalGaugeEvents; i++) {
      validGauges[i] =
          createValidGaugeMetric().toBuilder().setSessionId("Session - " + (i + 1)).build();
      testTransportManager.log(validGauges[i], ApplicationProcessState.FOREGROUND);
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 5. All Gauges are queued even after Traces are capped
    assertThat(testTransportManager.getPendingEventsQueue().size())
        .isEqualTo(maxTracesCacheSize + totalGaugeEvents);

    // 6. Initialize Transport
    initializeTransport(true);

    // 7. Consume all queued Traces
    for (int i = 0; i < maxTracesCacheSize; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
    }

    // 8. Consume all queued Gauges and validate them
    for (int i = 0; i < totalGaugeEvents; i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
      PerfMetric loggedValidGauge = getLastLoggedEvent(times(1));
      assertThat(loggedValidGauge.getGaugeMetric()).isEqualTo(validGauges[i]);
      validateApplicationInfo(loggedValidGauge, ApplicationProcessState.FOREGROUND);
    }

    // 9. Queue is all consumed
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();

    // 10. No pending events
    clearLastLoggedEvents();
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void
      logMultipleEvents_exhaustTheEntireCacheWhileTransportNotInitialized_eventsAfterMaxCapAreNotQueued() {
    // 1. Transport is not initialized in the beginning
    initializeTransport(false);

    // 2. Log multiple Traces such that they are capped
    int maxTracesCacheSize = 50; // only 50 TraceMetric events are allowed to cache
    int totalTraceEvents = maxTracesCacheSize + 10;

    for (int i = 0; i < totalTraceEvents; i++) {
      testTransportManager.log(
          createValidTraceMetric().toBuilder().setName("Trace - " + (i + 1)).build());
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 3. Log multiple Network Requests such that they are capped
    int maxNetworkRequestsCacheSize =
        50; // only 50 NetworkRequestMetric events are allowed to cache
    int totalNetworkRequestEvents = maxNetworkRequestsCacheSize + 10;

    for (int i = 0; i < totalNetworkRequestEvents; i++) {
      testTransportManager.log(
          createValidNetworkRequestMetric().toBuilder().setClientStartTimeUs(i + 1).build());
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 4. Log multiple Gauges such that they are capped
    int maxGaugesCacheSize = 50; // only 50 GaugeMetric events are allowed to cache
    int totalGaugeEvents = maxGaugesCacheSize + 10;
    GaugeMetric[] validGauges = new GaugeMetric[totalGaugeEvents];

    for (int i = 0; i < totalGaugeEvents; i++) {
      validGauges[i] =
          createValidGaugeMetric().toBuilder().setSessionId("Session - " + (i + 1)).build();
      testTransportManager.log(validGauges[i], ApplicationProcessState.FOREGROUND);
      fakeExecutorService.runAll();
      assertThat(getLastLoggedEvent(never())).isNull();
    }

    // 5. Even though we recorded "totalTraceEvents" + "totalNetworkRequestEvents" +
    // "totalGaugeEvents" events up-to "maxTracesCacheSize" +  "maxNetworkRequestsCacheSize" +
    // "maxGaugesCacheSize" are only
    // queued
    assertThat(testTransportManager.getPendingEventsQueue().size())
        .isEqualTo(maxTracesCacheSize + maxNetworkRequestsCacheSize + maxGaugesCacheSize);

    // 6. Initialize Transport
    initializeTransport(true);

    // 7. Consume all queued Events
    for (int i = 1;
        i <= (maxTracesCacheSize + maxNetworkRequestsCacheSize + maxGaugesCacheSize);
        i++) {
      clearLastLoggedEvents();
      fakeExecutorService.runNext();
    }

    // 8. Queue is all consumed
    assertThat(testTransportManager.getPendingEventsQueue().isEmpty()).isTrue();

    // 9. No pending events
    clearLastLoggedEvents();
    fakeExecutorService.runAll();
    assertThat(getLastLoggedEvent(never())).isNull();
  }

  // endregion

  // region Flg Destination

  @Test
  public void logPerfMetric_dispatchedToFlgOnly() {
    TraceMetric validTrace = createValidTraceMetric();

    testTransportManager.log(validTrace, ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();

    verify(mockFlgTransport, times(1)).log(any());
    assertThat(getLastLoggedEvent(times(1)).getTraceMetric()).isEqualTo(validTrace);
  }

  // endregion

  // region Performance Enable/Disable or Rate Limited

  @Test
  public void validTraceMetric_perfDisabled_notLogged() {
    when(mockConfigResolver.isPerformanceMonitoringEnabled()).thenReturn(false);

    testTransportManager.log(createValidTraceMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void validNetworkMetric_perfDisabled_notLogged() {
    when(mockConfigResolver.isPerformanceMonitoringEnabled()).thenReturn(false);

    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void validGaugeMetric_perfDisabled_notLogged() {
    when(mockConfigResolver.isPerformanceMonitoringEnabled()).thenReturn(false);

    testTransportManager.log(createValidGaugeMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void validTraceMetric_rateLimited_notLogged() {
    when(mockRateLimiter.isEventRateLimited(ArgumentMatchers.nullable(PerfMetric.class)))
        .thenReturn(true);

    testTransportManager.log(createValidTraceMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
    verify(mockAppStateMonitor)
        .incrementCount(Constants.CounterNames.TRACE_EVENT_RATE_LIMITED.toString(), 1);
  }

  @Test
  public void validNetworkMetric_rateLimited_notLogged() {
    when(mockRateLimiter.isEventRateLimited(ArgumentMatchers.nullable(PerfMetric.class)))
        .thenReturn(true);

    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
    verify(mockAppStateMonitor)
        .incrementCount(CounterNames.NETWORK_TRACE_EVENT_RATE_LIMITED.toString(), 1);
  }

  @Test
  public void validTraceMetric_notSampled_notLogged() {
    when(mockRateLimiter.isEventSampled(ArgumentMatchers.nullable(PerfMetric.class)))
        .thenReturn(false);

    testTransportManager.log(createValidTraceMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
    verify(mockAppStateMonitor)
        .incrementCount(Constants.CounterNames.TRACE_EVENT_RATE_LIMITED.toString(), 1);
  }

  @Test
  public void validNetworkMetric_notSampled_notLogged() {
    when(mockRateLimiter.isEventSampled(ArgumentMatchers.nullable(PerfMetric.class)))
        .thenReturn(false);

    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
    verify(mockAppStateMonitor)
        .incrementCount(CounterNames.NETWORK_TRACE_EVENT_RATE_LIMITED.toString(), 1);
  }

  // endregion

  // region ApplicationProcessState Behaviour

  @Test
  public void validTraceMetric_knownApplicationProcessState_getLogged() {
    TraceMetric validTrace = createValidTraceMetric();

    testTransportManager.log(validTrace, ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getTraceMetric()).isEqualTo(validTrace);
    validateApplicationInfo(loggedPerfMetric, ApplicationProcessState.BACKGROUND);
  }

  @Test
  public void validNetworkMetric_knownApplicationProcessState_getLogged() {
    NetworkRequestMetric validNetworkRequest = createValidNetworkRequestMetric();

    testTransportManager.log(validNetworkRequest, ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getNetworkRequestMetric()).isEqualTo(validNetworkRequest);
    validateApplicationInfo(loggedPerfMetric, ApplicationProcessState.FOREGROUND);
  }

  @Test
  public void validGaugeMetric_knownApplicationProcessState_getLogged() {
    GaugeMetric validGauge = createValidGaugeMetric();

    testTransportManager.log(validGauge, ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getGaugeMetric()).isEqualTo(validGauge);
    validateApplicationInfo(loggedPerfMetric, ApplicationProcessState.BACKGROUND);
  }

  @Test
  public void validTraceMetric_unknownApplicationProcessState_getLogged() {
    TraceMetric validTrace = createValidTraceMetric();

    testTransportManager.log(validTrace);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getTraceMetric()).isEqualTo(validTrace);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  @Test
  public void validNetworkMetric_unknownApplicationProcessState_getLogged() {
    NetworkRequestMetric validNetworkRequest = createValidNetworkRequestMetric();

    testTransportManager.log(validNetworkRequest);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getNetworkRequestMetric()).isEqualTo(validNetworkRequest);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  @Test
  public void validGaugeMetric_unknownApplicationProcessState_getLogged() {
    GaugeMetric validGauge = createValidGaugeMetric();

    testTransportManager.log(validGauge);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getGaugeMetric()).isEqualTo(validGauge);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  @Test
  public void invalidTraceMetric_knownApplicationProcessState_notLogged() {
    testTransportManager.log(createInvalidTraceMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void invalidNetworkMetric_knownApplicationProcessState_notLogged() {
    testTransportManager.log(
        createInvalidNetworkRequestMetric(), ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void invalidGaugeMetric_knownApplicationProcessState_notLogged() {
    testTransportManager.log(createInValidGaugeMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void invalidTraceMetric_unknownApplicationProcessState_notLogged() {
    testTransportManager.log(createInvalidTraceMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void invalidNetworkMetric_unknownApplicationProcessState_notLogged() {
    testTransportManager.log(createInvalidNetworkRequestMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void invalidGaugeMetric_unknownApplicationProcessState_notLogged() {
    testTransportManager.log(createInValidGaugeMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void invalidNetworkMetric_validURIButInvalidHostWithUnknownApplicationState_notLogged() {
    NetworkRequestMetric invalidNetworkRequest =
        createValidNetworkRequestMetric().toBuilder().setUrl("validUriButInvalidHost").build();

    testTransportManager.log(invalidNetworkRequest);
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void appStateChanges_capturedByUpdatingRate() {
    testTransportManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();
    verify(mockRateLimiter).changeRate(/* isForeground= */ false);

    testTransportManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();
    verify(mockRateLimiter).changeRate(/* isForeground= */ true);
  }

  // endregion

  // region Installations Interaction

  @Test
  public void validTraceMetric_nullInstallationsAndNoCache_notLogged() {
    clearInstallationsIdFromCache();
    mockInstallationsGetId(null);

    clearLastLoggedEvents();
    testTransportManager.log(createValidTraceMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void validNetworkMetric_nullInstallationsAndNoCache_notLogged() {
    clearInstallationsIdFromCache();
    mockInstallationsGetId(null);

    clearLastLoggedEvents();
    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
  }

  @Test
  public void validTraceMetric_nullInstallationsDuringInitButValidValueLater_getLogged() {
    // Null Installations during initialization
    mockInstallationsGetId(null);
    initializeTransport(true);
    fakeExecutorService.runAll();

    // Valid FID later
    mockInstallationsGetId(FAKE_FIREBASE_INSTALLATIONS_ID);
    TraceMetric validTrace = createValidTraceMetric();

    testTransportManager.log(validTrace);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getTraceMetric()).isEqualTo(validTrace);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  @Test
  public void validNetworkMetric_nullInstallationsDuringInitButValidValueLater_getLogged() {
    // Null Installations during initialization
    mockInstallationsGetId(null);
    initializeTransport(true);
    fakeExecutorService.runAll();

    // Valid FID later
    mockInstallationsGetId(FAKE_FIREBASE_INSTALLATIONS_ID);
    NetworkRequestMetric validNetworkRequest = createValidNetworkRequestMetric();

    testTransportManager.log(validNetworkRequest);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getNetworkRequestMetric()).isEqualTo(validNetworkRequest);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
  }

  @Test
  public void syncLog_appInBackgroundWithoutCache_callsFirebaseInstallationsOnce() {
    clearInstallationsIdFromCache();

    // Mimic the Background app state event
    testTransportManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    // Attempts to send logs first time when the app is in the background
    clearLastLoggedEvents();
    testTransportManager.log(createValidTraceMetric());
    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    // getId() call happens only once in the background
    verify(mockFirebaseInstallationsApi, times(1)).getId();
  }

  @Test
  public void syncLog_appInBackgroundWithCache_neverCallsFirebaseInstallations() {
    // Mimic the Foreground app state event
    testTransportManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);
    testTransportManager.log(createValidTraceMetric());
    fakeExecutorService.runAll();

    // getId() is called since syncLog() is called during foreground (FID is cached after this call)
    verify(mockFirebaseInstallationsApi, times(1)).getId();

    // Mimic the Background app state event
    testTransportManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    // Attempt to send logs when the app is in background
    testTransportManager.log(createValidTraceMetric());
    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    // getId() call count doesn't increment because cache already exists
    verify(mockFirebaseInstallationsApi, times(1)).getId();
  }

  @Test
  public void syncLog_appInForeground_alwaysCallFirebaseInstallations() {
    // Mimic the Foreground app state event
    testTransportManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);
    testTransportManager.log(createValidTraceMetric());
    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    // getId() is called when syncLog() is called during foreground
    verify(mockFirebaseInstallationsApi, times(2)).getId();
  }

  @Test
  public void syncLogForTraceMetric_performanceDisabled_noInteractionWithFirebaseInstallations() {
    when(mockConfigResolver.isPerformanceMonitoringEnabled()).thenReturn(false);

    testTransportManager.log(createValidTraceMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
    verify(mockFirebaseInstallationsApi, never()).getId();
  }

  @Test
  public void syncLogForNetworkMetric_performanceDisabled_noInteractionWithFirebaseInstallations() {
    when(mockConfigResolver.isPerformanceMonitoringEnabled()).thenReturn(false);

    testTransportManager.log(createValidNetworkRequestMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
    verify(mockFirebaseInstallationsApi, never()).getId();
  }

  @Test
  public void syncLogForGaugeMetric_performanceDisabled_noInteractionWithFirebaseInstallations() {
    when(mockConfigResolver.isPerformanceMonitoringEnabled()).thenReturn(false);

    testTransportManager.log(createValidGaugeMetric());
    fakeExecutorService.runAll();

    assertThat(getLastLoggedEvent(never())).isNull();
    verify(mockFirebaseInstallationsApi, never()).getId();
  }

  // endregion

  // region Global Custom Attributes Behaviour

  @Test
  public void logTraceMetric_globalCustomAttributesAreAdded() {
    FirebasePerformance.getInstance().putAttribute("test_key1", "test_value1");
    FirebasePerformance.getInstance().putAttribute("test_key2", "test_value2");
    TraceMetric validTrace = createValidTraceMetric();

    testTransportManager.log(validTrace);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getTraceMetric()).isEqualTo(validTrace);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);

    Map<String, String> globalCustomAttributes =
        loggedPerfMetric.getApplicationInfo().getCustomAttributesMap();
    assertThat(globalCustomAttributes).hasSize(2);
    assertThat(globalCustomAttributes).containsEntry("test_key1", "test_value1");
    assertThat(globalCustomAttributes).containsEntry("test_key2", "test_value2");
  }

  @Test
  public void logNetworkMetric_globalCustomAttributesAreAdded() {
    FirebasePerformance.getInstance().putAttribute("test_key1", "test_value1");
    FirebasePerformance.getInstance().putAttribute("test_key2", "test_value2");
    NetworkRequestMetric validNetworkRequest = createValidNetworkRequestMetric();

    testTransportManager.log(validNetworkRequest);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getNetworkRequestMetric()).isEqualTo(validNetworkRequest);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);

    Map<String, String> globalCustomAttributes =
        loggedPerfMetric.getApplicationInfo().getCustomAttributesMap();
    assertThat(globalCustomAttributes).hasSize(2);
    assertThat(globalCustomAttributes).containsEntry("test_key1", "test_value1");
    assertThat(globalCustomAttributes).containsEntry("test_key2", "test_value2");
  }

  @Test
  public void logGaugeMetric_globalCustomAttributesAreNotAdded() {
    FirebasePerformance.getInstance().putAttribute("test_key1", "test_value1");
    FirebasePerformance.getInstance().putAttribute("test_key2", "test_value2");
    GaugeMetric validGauge = createValidGaugeMetric();

    testTransportManager.log(validGauge);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getGaugeMetric()).isEqualTo(validGauge);
    validateApplicationInfo(
        loggedPerfMetric, ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN);
    assertThat(loggedPerfMetric.getApplicationInfo().getCustomAttributesCount()).isEqualTo(0);
  }

  // endregion

  // region Session's Behaviour

  @Test
  public void logTraceMetric_sessionEnabled_doesNotStripOffSessionId() {
    TraceMetric.Builder validTrace = createValidTraceMetric().toBuilder();
    List<PerfSession> perfSessions = new ArrayList<>();
    perfSessions.add(
        new com.google.firebase.perf.session.PerfSession("fakeSessionId", new Clock()).build());
    validTrace.addAllPerfSessions(perfSessions);

    testTransportManager.log(validTrace.build());
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getTraceMetric().getPerfSessionsCount()).isEqualTo(1);
    assertThat(loggedPerfMetric.getTraceMetric().getPerfSessions(0).getSessionId())
        .isEqualTo("fakeSessionId");
  }

  @Test
  public void logNetworkMetric_sessionEnabled_doesNotStripOffSessionId() {
    NetworkRequestMetric.Builder validNetworkRequest =
        createValidNetworkRequestMetric().toBuilder();
    List<PerfSession> perfSessions = new ArrayList<>();
    perfSessions.add(
        new com.google.firebase.perf.session.PerfSession("fakeSessionId", new Clock()).build());
    validNetworkRequest.clearPerfSessions().addAllPerfSessions(perfSessions);

    testTransportManager.log(validNetworkRequest.build());
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getNetworkRequestMetric().getPerfSessionsCount()).isEqualTo(1);
    assertThat(loggedPerfMetric.getNetworkRequestMetric().getPerfSessions(0).getSessionId())
        .isEqualTo("fakeSessionId");
  }

  @Test
  public void logTraceMetric_perfSessionExpired_doesNotUpdateSessionId() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(true);

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    testTransportManager.log(createValidTraceMetric(), ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void logNetworkMetric_perfSessionExpired_doesNotUpdateSessionId() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(true);

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    testTransportManager.log(createValidNetworkRequestMetric(), ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void logGaugeMetric_perfSessionExpired_doesNotUpdateSessionId() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(true);

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    testTransportManager.log(createValidGaugeMetric(), ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();

    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void logTraceMetric_perfSessionNotExpired_doesNotUpdateSessionId() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(false);

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    testTransportManager.log(createValidTraceMetric(), ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void logNetworkMetric_perfSessionNotExpired_doesNotUpdateSessionId() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(false);

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    testTransportManager.log(createValidNetworkRequestMetric(), ApplicationProcessState.BACKGROUND);
    fakeExecutorService.runAll();

    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void logGaugeMetric_perfSessionNotExpired_doesNotUpdateSessionId() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(false);

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    testTransportManager.log(createValidGaugeMetric(), ApplicationProcessState.FOREGROUND);
    fakeExecutorService.runAll();

    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  // endregion

  // region Gauge Specific

  @Test
  public void validGaugeMetric_withCpuReadings_isLogged() {
    ApplicationProcessState expectedAppState = ApplicationProcessState.FOREGROUND;

    // Construct a list of Cpu metric readings
    List<CpuMetricReading> expectedCpuMetricReadings = new ArrayList<>();
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 10, /* systemTimeUs= */ 20));
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 20, /* systemTimeUs= */ 30));

    GaugeMetric validGauge =
        GaugeMetric.newBuilder()
            .setSessionId("sessionId")
            .addAllCpuMetricReadings(expectedCpuMetricReadings)
            .build();

    testTransportManager.log(validGauge, expectedAppState);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getGaugeMetric().getCpuMetricReadingsList())
        .containsExactlyElementsIn(expectedCpuMetricReadings);
    assertThat(loggedPerfMetric.getGaugeMetric().getSessionId()).isEqualTo("sessionId");
  }

  @Test
  public void validGaugeMetric_withMemoryReadings_isLogged() {
    ApplicationProcessState expectedAppState = ApplicationProcessState.FOREGROUND;

    // Construct a list of Memory metric readings
    List<AndroidMemoryReading> expectedMemoryMetricReadings = new ArrayList<>();
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234));
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 23456));

    GaugeMetric validGauge =
        GaugeMetric.newBuilder()
            .setSessionId("sessionId")
            .addAllAndroidMemoryReadings(expectedMemoryMetricReadings)
            .build();

    testTransportManager.log(validGauge, expectedAppState);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getGaugeMetric().getAndroidMemoryReadingsList())
        .containsExactlyElementsIn(expectedMemoryMetricReadings);
    assertThat(loggedPerfMetric.getGaugeMetric().getSessionId()).isEqualTo("sessionId");
  }

  @Test
  public void validGaugeMetric_withMetadata_isLogged() {
    ApplicationProcessState expectedAppState = ApplicationProcessState.FOREGROUND;

    GaugeMetadata gaugeMetadata =
        GaugeMetadata.newBuilder()
            .setDeviceRamSizeKb(2000)
            .setMaxAppJavaHeapMemoryKb(1000)
            .setMaxEncouragedAppJavaHeapMemoryKb(800)
            .build();

    GaugeMetric validGauge =
        GaugeMetric.newBuilder().setSessionId("sessionId").setGaugeMetadata(gaugeMetadata).build();

    testTransportManager.log(validGauge, expectedAppState);
    fakeExecutorService.runAll();

    PerfMetric loggedPerfMetric = getLastLoggedEvent(times(1));
    assertThat(loggedPerfMetric.getGaugeMetric().getSessionId()).isEqualTo("sessionId");
    assertThat(loggedPerfMetric.getGaugeMetric().getGaugeMetadata().getDeviceRamSizeKb())
        .isEqualTo(2000);
    assertThat(loggedPerfMetric.getGaugeMetric().getGaugeMetadata().getMaxAppJavaHeapMemoryKb())
        .isEqualTo(1000);
    assertThat(
            loggedPerfMetric
                .getGaugeMetric()
                .getGaugeMetadata()
                .getMaxEncouragedAppJavaHeapMemoryKb())
        .isEqualTo(800);
  }

  // endregion

  // region Helper Methods

  private void initializeTransport(boolean shouldInitialize) {
    // # Before Initializing
    // Clear any logged events
    clearLastLoggedEvents();

    if (shouldInitialize) {
      // Set the version name since Firebase sessions needs it.
      Context context = ApplicationProvider.getApplicationContext();
      ShadowPackageManager shadowPackageManager = shadowOf(context.getPackageManager());

      PackageInfo packageInfo =
          shadowPackageManager.getInternalMutablePackageInfo(context.getPackageName());
      packageInfo.versionName = "1.0.0";

      packageInfo.applicationInfo.metaData.clear();

      testTransportManager = TransportManager.getInstance();
      testTransportManager.initializeForTest(
          FirebaseApp.getInstance(),
          FirebasePerformance.getInstance(),
          mockFirebaseInstallationsApi,
          mockFlgTransportFactoryProvider,
          mockConfigResolver,
          mockRateLimiter,
          mockAppStateMonitor,
          mockFlgTransport,
          fakeExecutorService);

    } else {
      testTransportManager.setInitialized(false);
    }
  }

  private void clearInstallationsIdFromCache() {
    testTransportManager.clearAppInstanceId();
  }

  private void mockInstallationsGetId(final String installationsId) {
    when(mockFirebaseInstallationsApi.getId()).thenReturn(Tasks.forResult(installationsId));
  }

  private List<PerfMetric> getLastLoggedEvents(VerificationMode expectedVerificationMode) {
    verifyDispatch(expectedVerificationMode);

    if (!expectedVerificationMode.toString().equals(never().toString())) {
      return perfMetricArgumentCaptor.getAllValues();
    }

    return new ArrayList<>();
  }

  private PerfMetric getLastLoggedEvent(VerificationMode expectedVerificationMode) {
    verifyDispatch(expectedVerificationMode);

    if (!expectedVerificationMode.toString().equals(never().toString())) {
      return perfMetricArgumentCaptor.getValue();
    }

    return null;
  }

  private void verifyDispatch(VerificationMode expectedVerificationMode) {
    verify(mockFlgTransport, expectedVerificationMode).log(perfMetricArgumentCaptor.capture());
  }

  private void clearLastLoggedEvents() {
    clearInvocations(mockFlgTransport);
  }

  private static void validateApplicationInfo(
      PerfMetric loggedPerfMetric, ApplicationProcessState applicationProcessState) {
    assertThat(loggedPerfMetric.getApplicationInfo().getAppInstanceId())
        .isEqualTo(FAKE_FIREBASE_INSTALLATIONS_ID);
    assertThat(loggedPerfMetric.getApplicationInfo().getGoogleAppId())
        .isEqualTo(FAKE_FIREBASE_APPLICATION_ID);
    assertThat(loggedPerfMetric.getApplicationInfo().getApplicationProcessState())
        .isEqualTo(applicationProcessState);
    assertThat(loggedPerfMetric.getApplicationInfo().hasAndroidAppInfo()).isTrue();
  }

  private static TraceMetric createInvalidTraceMetric() {
    return createValidTraceMetric().toBuilder().setName("").build();
  }

  private static TraceMetric createValidTraceMetric() {
    return TraceMetric.newBuilder()
        .setName("test_trace")
        .setClientStartTimeUs(100L)
        .setDurationUs(100L)
        .build();
  }

  private static NetworkRequestMetric createInvalidNetworkRequestMetric() {
    return createValidNetworkRequestMetric().toBuilder().setUrl("//: invalidUrl").build();
  }

  private static NetworkRequestMetric createValidNetworkRequestMetric() {
    return NetworkRequestMetric.newBuilder()
        .setUrl("https://www.google.com")
        .setHttpMethod(HttpMethod.GET)
        .setHttpResponseCode(200)
        .setClientStartTimeUs(100L)
        .setTimeToResponseCompletedUs(100L)
        .build();
  }

  private static GaugeMetric createInValidGaugeMetric() {
    return createValidGaugeMetric().toBuilder().clearSessionId().build();
  }

  private static GaugeMetric createValidGaugeMetric() {
    // Construct a list of Cpu metric readings
    List<CpuMetricReading> cpuMetricReadings = new ArrayList<>();
    cpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 10, /* systemTimeUs= */ 20));
    cpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 20, /* systemTimeUs= */ 30));

    // Construct a list of Memory metric readings
    List<AndroidMemoryReading> memoryMetricReadings = new ArrayList<>();
    memoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234));
    memoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 23456));

    return GaugeMetric.newBuilder()
        .setSessionId("sessionId")
        .addAllCpuMetricReadings(cpuMetricReadings)
        .addAllAndroidMemoryReadings(memoryMetricReadings)
        .build();
  }

  private static CpuMetricReading createValidCpuMetricReading(long userTimeUs, long systemTimeUs) {
    return CpuMetricReading.newBuilder()
        .setClientTimeUs(System.currentTimeMillis())
        .setUserTimeUs(userTimeUs)
        .setSystemTimeUs(systemTimeUs)
        .build();
  }

  private static AndroidMemoryReading createValidAndroidMetricReading(
      int currentUsedAppJavaHeapMemoryKb) {
    return AndroidMemoryReading.newBuilder()
        .setClientTimeUs(System.currentTimeMillis())
        .setUsedAppJavaHeapMemoryKb(currentUsedAppJavaHeapMemoryKb)
        .build();
  }

  // endregion

}
