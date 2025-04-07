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
  }

  @Test
  public void setApplicationContext_initializeGaugeMetadataManager()
      throws ExecutionException, InterruptedException {
    when(mockPerfSession.isGaugeAndEventCollectionEnabled()).thenReturn(true);
    InOrder inOrder = Mockito.inOrder(mockGaugeManager);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.setApplicationContext(mockApplicationContext);

    inOrder.verify(mockGaugeManager).initializeGaugeMetadataManager(any());
  }

  // LogGaugeData on new perf session when Verbose
  // NotLogGaugeData on new perf session when not Verbose
  // Mark Session as expired after time limit.

  @Test
  public void testUpdatePerfSessionMakesGaugeManagerStopCollectingGaugesIfSessionIsNonVerbose() {
    forceNonVerboseSession();

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, mockPerfSession, mockAppStateMonitor);
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId"));

    verify(mockGaugeManager).stopCollectingGauges();
  }

  @Test
  public void testUpdatePerfSessionMakesGaugeManagerStopCollectingGaugesWhenSessionsDisabled() {
    forceSessionsFeatureDisabled();

    SessionManager testSessionManager =
        new SessionManager(
            mockGaugeManager, PerfSession.createWithId("testSessionId"), mockAppStateMonitor);
    testSessionManager.updatePerfSession(PerfSession.createWithId("testSessionId2"));

    verify(mockGaugeManager).stopCollectingGauges();
  }

  @Test
  public void testSessionIdDoesNotUpdateIfPerfSessionRunsTooLong() {
    Timer mockTimer = mock(Timer.class);
    when(mockClock.getTime()).thenReturn(mockTimer);

    PerfSession session = new PerfSession("sessionId", mockClock, true);
    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, session, mockAppStateMonitor);

    assertThat(session.isSessionRunningTooLong()).isFalse();

    when(mockTimer.getDurationMicros())
        .thenReturn(TimeUnit.HOURS.toMicros(5)); // Default Max Session Length is 4 hours
    assertThat(session.isSessionRunningTooLong()).isTrue();

    assertThat(testSessionManager.perfSession().sessionId()).isEqualTo("sessionId");
  }

  @Test
  public void testUpdatePerfSessionStartsCollectingGaugesIfSessionIsVerbose() {
    Timer mockTimer = mock(Timer.class);
    when(mockClock.getTime()).thenReturn(mockTimer);
    when(mockAppStateMonitor.getAppState()).thenReturn(ApplicationProcessState.FOREGROUND);

    PerfSession previousSession = new PerfSession("previousSession", mockClock, true);
    previousSession.setGaugeAndEventCollectionEnabled(false);

    PerfSession newSession = new PerfSession("newSession", mockClock, true);
    newSession.setGaugeAndEventCollectionEnabled(true);

    SessionManager testSessionManager =
        new SessionManager(mockGaugeManager, previousSession, mockAppStateMonitor);
    testSessionManager.updatePerfSession(newSession);
    testSessionManager.setApplicationContext(mockApplicationContext);

    verify(mockGaugeManager, times(1)).initializeGaugeMetadataManager(mockApplicationContext);
    verify(mockGaugeManager, times(1))
        .startCollectingGauges(newSession, ApplicationProcessState.FOREGROUND);
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
