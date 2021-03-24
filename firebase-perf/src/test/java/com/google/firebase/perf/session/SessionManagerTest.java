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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.v1.ApplicationProcessState;
import java.lang.ref.WeakReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link SessionManager}. */
@RunWith(RobolectricTestRunner.class)
public class SessionManagerTest extends FirebasePerformanceTestBase {

  @Mock private GaugeManager mockGaugeManager;
  @Mock private PerfSession mockPerfSession;
  @Mock private AppStateMonitor mockAppStateMonitor;

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
    when(mockPerfSession.isExpired()).thenReturn(true);
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
    when(mockPerfSession.isExpired()).thenReturn(true);
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
        .startCollectingGauges(
            AdditionalMatchers.not(eq(mockPerfSession)), eq(ApplicationProcessState.FOREGROUND));
  }

  @Test
  public void testOnUpdateAppStateMakesGaugeManagerStopCollectingGaugesIfSessionIsNonVerbose() {
    forceNonVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);

    verify(mockGaugeManager).stopCollectingGauges();
  }

  @Test
  public void testOnUpdateAppStateMakesGaugeManagerStopCollectingGaugesWhenSessionsDisabled() {
    forceSessionsFeatureDisabled();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, PerfSession.create(), mockAppStateMonitor);
    testSessionManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);

    verify(mockGaugeManager).stopCollectingGauges();
  }

  @Test
  public void testGaugeMetadataIsFlushedOnlyWhenNewVerboseSessionIsCreated() {
    when(mockPerfSession.isExpired()).thenReturn(false);

    // Forcing a verbose session will enable Gauge collection
    forceVerboseSession();
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, PerfSession.create(), mockAppStateMonitor);

    // A new session is created when the app comes to foreground
    testSessionManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);
    String sessionId1 = testSessionManager.perfSession().sessionId();

    // Session ID should remain unchanged when the app goes to background
    testSessionManager.onUpdateAppState(ApplicationProcessState.BACKGROUND);
    String sessionId2 = testSessionManager.perfSession().sessionId();
    assertThat(sessionId2).matches(sessionId1);

    // Forcing a non-verbose session will disable Gauge collection
    forceNonVerboseSession();

    // A new session is created when the app comes to foreground
    testSessionManager.onUpdateAppState(ApplicationProcessState.FOREGROUND);
    String sessionId3 = testSessionManager.perfSession().sessionId();
    assertThat(sessionId3).doesNotMatch(sessionId2);

    verify(mockGaugeManager, times(1))
        .logGaugeMetadata(
            eq(sessionId1), eq(com.google.firebase.perf.v1.ApplicationProcessState.FOREGROUND));
  }

  @Test
  public void testSessionIdUpdatesIfPerfSessionExpires() {
    when(mockPerfSession.isExpired()).thenReturn(true);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    String oldSessionId = testSessionManager.perfSession().sessionId();

    assertThat(oldSessionId).isNotNull();
    assertThat(oldSessionId).isEqualTo(testSessionManager.perfSession().sessionId());

    testSessionManager.updatePerfSessionIfExpired();
    assertThat(oldSessionId).isNotEqualTo(testSessionManager.perfSession().sessionId());
  }

  @Test
  public void testSessionIdDoesntUpdateIfPerfSessionDoesntExpires() {
    when(mockPerfSession.isExpired()).thenReturn(false);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    String oldSessionId = testSessionManager.perfSession().sessionId();

    assertThat(oldSessionId).isNotNull();
    assertThat(oldSessionId).isEqualTo(testSessionManager.perfSession().sessionId());

    testSessionManager.updatePerfSessionIfExpired();
    assertThat(oldSessionId).isEqualTo(testSessionManager.perfSession().sessionId());
  }

  @Test
  public void testPerfSessionExpiredMakesGaugeManagerLogGaugeMetadataIfSessionIsVerbose() {
    when(mockPerfSession.isExpired()).thenReturn(true);
    forceVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.updatePerfSessionIfExpired();

    verify(mockGaugeManager)
        .logGaugeMetadata(
            anyString(), nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void testPerfSessionExpiredDoesntMakeGaugeManagerLogGaugeMetadataIfSessionIsNonVerbose() {
    when(mockPerfSession.isExpired()).thenReturn(true);
    forceNonVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.updatePerfSessionIfExpired();

    verify(mockGaugeManager, never())
        .logGaugeMetadata(
            anyString(), nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void testPerfSessionExpiredMakesGaugeManagerStartCollectingGaugesIfSessionIsVerbose() {
    when(mockPerfSession.isExpired()).thenReturn(true);
    forceVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.updatePerfSessionIfExpired();

    verify(mockGaugeManager)
        .startCollectingGauges(
            AdditionalMatchers.not(eq(mockPerfSession)),
            nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void
      testPerfSessionExpiredDoesntMakeGaugeManagerStartCollectingGaugesIfSessionIsNonVerbose() {
    when(mockPerfSession.isExpired()).thenReturn(true);
    forceNonVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.updatePerfSessionIfExpired();

    verify(mockGaugeManager, never())
        .startCollectingGauges(
            eq(mockPerfSession),
            nullable(com.google.firebase.perf.v1.ApplicationProcessState.class));
  }

  @Test
  public void testPerfSessionExpiredMakesGaugeManagerStopCollectingGaugesIfSessionIsNonVerbose() {
    when(mockPerfSession.isExpired()).thenReturn(true);
    forceNonVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.updatePerfSessionIfExpired();

    verify(mockGaugeManager).stopCollectingGauges();
  }

  @Test
  public void testPerfSession_sessionAwareObjects_doesntNotifyIfNotRegistered() {
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);

    FakeSessionAwareObject spySessionAwareObjectOne = spy(new FakeSessionAwareObject());
    FakeSessionAwareObject spySessionAwareObjectTwo = spy(new FakeSessionAwareObject());

    testSessionManager.updatePerfSession(ApplicationProcessState.FOREGROUND);

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

    testSessionManager.updatePerfSession(ApplicationProcessState.FOREGROUND);
    testSessionManager.updatePerfSession(ApplicationProcessState.BACKGROUND);

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

    testSessionManager.updatePerfSession(ApplicationProcessState.FOREGROUND);

    testSessionManager.unregisterForSessionUpdates(weakSpySessionAwareObjectOne);
    testSessionManager.unregisterForSessionUpdates(weakSpySessionAwareObjectTwo);
    testSessionManager.updatePerfSession(ApplicationProcessState.BACKGROUND);

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
