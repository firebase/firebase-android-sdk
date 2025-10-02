// Copyright 2021 Google LLC
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

package com.google.firebase.perf.session;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link SessionManager}. */
@RunWith(RobolectricTestRunner.class)
public class SessionManagerTest extends FirebasePerformanceTestBase {

  @Mock private GaugeManager mockGaugeManager;
  @Mock private PerfSession mockPerfSession;
  @Mock private AppStateMonitor mockAppStateMonitor;
  @Mock private Context mockApplicationContext;

  @Mock private Clock mockClock;

  @Before
  public void setUp() {
    initMocks(this);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockAppStateMonitor.isColdStart()).thenReturn(false);
    AppStateMonitor.getInstance().setIsColdStart(false);
  }

  @Test
  public void testInstanceCreation() {
    assertThat(SessionManager.getInstance()).isNotNull();
    assertThat(SessionManager.getInstance()).isEqualTo(SessionManager.getInstance());
    assertThat(SessionManager.getInstance().perfSession().sessionId()).isNotNull();
  }

  @Test
  public void setApplicationContext_logGaugeMetadata_afterGaugeMetadataManagerIsInitialized()
      throws ExecutionException, InterruptedException {
    when(mockPerfSession.isGaugeAndEventCollectionEnabled()).thenReturn(true);
    InOrder inOrder = Mockito.inOrder(mockGaugeManager);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.setApplicationContext(mockApplicationContext);

    testSessionManager.getSyncInitFuture().get();
    inOrder.verify(mockGaugeManager).initializeGaugeMetadataManager(any());
    inOrder.verify(mockGaugeManager).logGaugeMetadata(any(), any());
  }

  @Test
  public void testOnUpdateAppStateDoesNothingDuringAppStart() {
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();

    assertThat(oldSessionId).isNotNull();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    AppStateMonitor.getInstance().setIsColdStart(true);

    SessionManager.getInstance().onUpdateAppState(ApplicationProcessState.FOREGROUND);
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void testOnUpdateAppStateGeneratesNewSessionIdOnForegroundState() {
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();

    assertThat(oldSessionId).isNotNull();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    SessionManager.getInstance().onUpdateAppState(ApplicationProcessState.FOREGROUND);
    assertThat(oldSessionId).isNotEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void testOnUpdateAppStateDoesntGenerateNewSessionIdOnBackgroundState() {
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();

    assertThat(oldSessionId).isNotNull();
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());

    SessionManager.getInstance().onUpdateAppState(ApplicationProcessState.BACKGROUND);
    assertThat(oldSessionId).isEqualTo(SessionManager.getInstance().perfSession().sessionId());
  }

  @Test
  public void testOnUpdateAppStateGeneratesNewSessionIdOnBackgroundStateIfPerfSessionExpires() {
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(true);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    String oldSessionId = testSessionManager.perfSession().sessionId();

    assertThat(oldSessionId).isNotNull();
    assertThat(oldSessionId).isEqualTo(testSessionManager.perfSession().sessionId());

    testSessionManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);
    assertThat(oldSessionId).isNotEqualTo(testSessionManager.perfSession().sessionId());
  }

  @Test
  public void
      testOnUpdateAppStateMakesGaugeManagerLogGaugeMetadataOnForegroundStateIfSessionIsVerbose() {
    forceVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);

    verify(mockGaugeManager)
        .logGaugeMetadata(
            anyString(), nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void
      testOnUpdateAppStateDoesntMakeGaugeManagerLogGaugeMetadataOnForegroundStateIfSessionIsNonVerbose() {
    forceNonVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);

    verify(mockGaugeManager, never())
        .logGaugeMetadata(
            anyString(), nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void
      testOnUpdateAppStateDoesntMakeGaugeManagerLogGaugeMetadataOnBackgroundStateEvenIfSessionIsVerbose() {
    forceVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);

    verify(mockGaugeManager, never())
        .logGaugeMetadata(
            anyString(), nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void
      testOnUpdateAppStateMakesGaugeManagerLogGaugeMetadataOnBackgroundAppStateIfSessionIsVerboseAndTimedOut() {
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(true);
    forceVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);

    verify(mockGaugeManager)
        .logGaugeMetadata(
            anyString(), nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void testOnUpdateAppStateMakesGaugeManagerStartCollectingGaugesIfSessionIsVerbose() {
    forceVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);

    verify(mockGaugeManager)
        .startCollectingGauges(AdditionalMatchers.not(eq(mockPerfSession)), any());
  }

  // LogGaugeData on new perf session when Verbose
  // NotLogGaugeData on new perf session when not Verbose
  // Mark Session as expired after time limit.

  @Test
  public void testOnUpdateAppStateMakesGaugeManagerStopCollectingGaugesIfSessionIsNonVerbose() {
    forceNonVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId"));

    verify(mockGaugeManager).stopCollectingGauges();
  }

  @Test
  public void testOnUpdateAppStateMakesGaugeManagerStopCollectingGaugesWhenSessionsDisabled() {
    forceSessionsFeatureDisabled();

    SessionManager testSessionManager =
        new SessionManager(
            mockGaugeManager, PerfSession.createWithId("testSessionId"), mockAppStateMonitor);
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId2"));

    verify(mockGaugeManager).stopCollectingGauges();
  }

  @Test
  public void testGaugeMetadataIsFlushedOnlyWhenNewVerboseSessionIsCreated() {
    when(mockPerfSession.isSessionRunningTooLong()).thenReturn(false);

    // Start with a non verbose session
    forceNonVerboseSession();
    SessionManager testSessionManager =
        new SessionManager(
            mockGaugeManager, PerfSession.createWithId("testSessionId1"), mockAppStateMonitor);

    verify(mockGaugeManager, times(0))
        .logGaugeMetadata(
            eq("testSessionId1"),
            eq(com.google.firebase.perf.v1.ApplicationProcessState.FOREGROUND));

    // Forcing a verbose session will enable Gauge collection
    forceVerboseSession();
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId2"));
    verify(mockGaugeManager, times(1)).logGaugeMetadata(eq("testSessionId2"), any());

    // Force a non-verbose session and verify if we are not logging metadata
    forceVerboseSession();
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId3"));
    verify(mockGaugeManager, times(1)).logGaugeMetadata(eq("testSessionId3"), any());
  }

  @Test
  public void testSessionIdDoesNotUpdateIfPerfSessionRunsTooLong() {
    Timer mockTimer = mock(Timer.class);
    when(mockClock.getTime()).thenReturn(mockTimer);

    PerfSession session = new PerfSession("sessionId", mockClock);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, session, mockAppStateMonitor);

    assertThat(session.isSessionRunningTooLong()).isFalse();

    when(mockTimer.getDurationMicros())
        .thenReturn(TimeUnit.HOURS.toMicros(5)); // Default Max Session Length is 4 hours
    assertThat(session.isSessionRunningTooLong()).isTrue();

    assertThat(testSessionManager.perfSession().sessionId()).isEqualTo("sessionId");
  }

  @Test
  public void testPerfSessionExpiredMakesGaugeManagerStopsCollectingGaugesIfSessionIsVerbose() {
    forceVerboseSession();
    Timer mockTimer = mock(Timer.class);
    when(mockClock.getTime()).thenReturn(mockTimer);

    PerfSession session = new PerfSession("sessionId", mockClock);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, session, mockAppStateMonitor);

    assertThat(session.isSessionRunningTooLong()).isFalse();

    when(mockTimer.getDurationMicros())
        .thenReturn(TimeUnit.HOURS.toMicros(5)); // Default Max Session Length is 4 hours

    assertThat(session.isSessionRunningTooLong()).isTrue();
    verify(mockGaugeManager, times(0)).logGaugeMetadata(any(), any());
  }

  @Test
  public void testPerfSession_sessionAwareObjects_doesntNotifyIfNotRegistered() {
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);

    FakeSessionAwareObject spySessionAwareObjectOne = spy(new FakeSessionAwareObject());
    FakeSessionAwareObject spySessionAwareObjectTwo = spy(new FakeSessionAwareObject());

    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId1"));

    verify(spySessionAwareObjectOne, never())
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
    verify(spySessionAwareObjectTwo, never())
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
  }

  @Test
  public void testPerfSession_sessionAwareObjects_NotifiesIfRegistered() {
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);

    FakeSessionAwareObject spySessionAwareObjectOne = spy(new FakeSessionAwareObject());
    FakeSessionAwareObject spySessionAwareObjectTwo = spy(new FakeSessionAwareObject());

    testSessionManager.registerForSessionUpdates(new WeakReference<>(spySessionAwareObjectOne));
    testSessionManager.registerForSessionUpdates(new WeakReference<>(spySessionAwareObjectTwo));

    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId1"));
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId2"));

    verify(spySessionAwareObjectOne, times(2))
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
    verify(spySessionAwareObjectTwo, times(2))
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
  }

  @Test
  public void testPerfSession_sessionAwareObjects_DoesNotNotifyIfUnregistered() {
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);

    FakeSessionAwareObject spySessionAwareObjectOne = spy(new FakeSessionAwareObject());
    FakeSessionAwareObject spySessionAwareObjectTwo = spy(new FakeSessionAwareObject());

    WeakReference<SessionAwareObject> weakSpySessionAwareObjectOne =
        new WeakReference<>(spySessionAwareObjectOne);
    WeakReference<SessionAwareObject> weakSpySessionAwareObjectTwo =
        new WeakReference<>(spySessionAwareObjectTwo);

    testSessionManager.registerForSessionUpdates(weakSpySessionAwareObjectOne);
    testSessionManager.registerForSessionUpdates(weakSpySessionAwareObjectTwo);

    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId1"));

    testSessionManager.unregisterForSessionUpdates(weakSpySessionAwareObjectOne);
    testSessionManager.unregisterForSessionUpdates(weakSpySessionAwareObjectTwo);
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId2"));

    verify(spySessionAwareObjectOne, times(1))
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
    verify(spySessionAwareObjectTwo, times(1))
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
  }
}

class FakeSessionAwareObject implements SessionAwareObject {

  @Override
  public void updateSession(PerfSession session) {}
}
