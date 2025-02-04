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
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;
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
    // We assume that an AQS session has been created in all tests.
    triggerAqsSession();
  }

  @Test
  public void testInstanceCreation() {
    assertThat(SessionManager.getInstance()).isNotNull();
    assertThat(SessionManager.getInstance()).isEqualTo(SessionManager.getInstance());
    assertThat(SessionManager.getInstance().perfSession().sessionId())
        .contains(FAKE_AQS_SESSION_PREFIX);
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
  public void testPerfSession_sessionAwareObjects_doesntNotifyIfNotRegistered() {
    SessionManager testSessionManager = SessionManager.getInstance();

    FakeSessionAwareObject spySessionAwareObjectOne = spy(new FakeSessionAwareObject());
    FakeSessionAwareObject spySessionAwareObjectTwo = spy(new FakeSessionAwareObject());

    testSessionManager.updatePerfSession(PerfSession.createNewSession());

    verify(spySessionAwareObjectOne, never())
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
    verify(spySessionAwareObjectTwo, never())
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
  }

  @Test
  public void testPerfSession_sessionAwareObjects_NotifiesIfRegistered() {
    SessionManager testSessionManager = SessionManager.getInstance();
    FakeSessionAwareObject spySessionAwareObjectOne = spy(new FakeSessionAwareObject());
    FakeSessionAwareObject spySessionAwareObjectTwo = spy(new FakeSessionAwareObject());

    testSessionManager.registerForSessionUpdates(new WeakReference<>(spySessionAwareObjectOne));
    testSessionManager.registerForSessionUpdates(new WeakReference<>(spySessionAwareObjectTwo));

    triggerAqsSession();
    triggerAqsSession();

    verify(spySessionAwareObjectOne, times(2))
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
    verify(spySessionAwareObjectTwo, times(2))
        .updateSession(ArgumentMatchers.nullable(PerfSession.class));
  }

  @Test
  public void testPerfSession_sessionAwareObjects_DoesNotNotifyIfUnregistered() {
    SessionManager testSessionManager = SessionManager.getInstance();
    FakeSessionAwareObject spySessionAwareObjectOne = spy(new FakeSessionAwareObject());
    FakeSessionAwareObject spySessionAwareObjectTwo = spy(new FakeSessionAwareObject());

    WeakReference<SessionAwareObject> weakSpySessionAwareObjectOne =
        new WeakReference<>(spySessionAwareObjectOne);
    WeakReference<SessionAwareObject> weakSpySessionAwareObjectTwo =
        new WeakReference<>(spySessionAwareObjectTwo);

    testSessionManager.registerForSessionUpdates(weakSpySessionAwareObjectOne);
    testSessionManager.registerForSessionUpdates(weakSpySessionAwareObjectTwo);

    triggerAqsSession();

    testSessionManager.unregisterForSessionUpdates(weakSpySessionAwareObjectOne);
    testSessionManager.unregisterForSessionUpdates(weakSpySessionAwareObjectTwo);
    triggerAqsSession();

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
