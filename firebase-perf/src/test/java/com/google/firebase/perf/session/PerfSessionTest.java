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
import static com.google.firebase.perf.util.Constants.PREFS_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import android.os.Bundle;
import com.google.common.collect.ImmutableList;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.ImmutableBundle;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.SessionVerbosity;
import com.google.testing.timing.FakeDirectExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.session.PerfSession}. */
@RunWith(RobolectricTestRunner.class)
public class PerfSessionTest extends FirebasePerformanceTestBase {

  @Mock private Clock mockClock;

  @Before
  public void setUp() {
    initMocks(this);

    DeviceCacheManager.clearInstance();
    ConfigResolver.clearInstance();

    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    ConfigResolver configResolver = ConfigResolver.getInstance();
    configResolver.setApplicationContext(appContext);
    configResolver.setDeviceCacheManager(new DeviceCacheManager(new FakeDirectExecutorService()));
  }

  @Test
  public void instanceCreation() {
    PerfSession session = new PerfSession("sessionId", mockClock);
    assertThat(session).isNotNull();
    session.setGaugeAndEventCollectionEnabled(true);
    Assert.assertTrue(session.isGaugeAndEventCollectionEnabled());
    session.setGaugeAndEventCollectionEnabled(false);
    Assert.assertFalse(session.isGaugeAndEventCollectionEnabled());
  }

  @Test
  public void shouldCollectGaugesAndEvents_perfMonDisabledAtRuntime_sessionNotVerbose() {
    ConfigResolver configResolver = ConfigResolver.getInstance();
    Bundle bundle = new Bundle();
    bundle.putFloat("sessions_sampling_percentage", 100);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // By default, session is verbose if developer has set 100% of session verbosity.
    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isTrue();

    // Case #1: developer has disabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(false);

    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isFalse();

    // Case #2: developer has enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isTrue();
  }

  @Test
  public void shouldCollectGaugesAndEvents_perfMonDisabledAtBuildtime_verbosityDependsOnRuntime() {
    ConfigResolver configResolver = ConfigResolver.getInstance();
    // Developer disables Performance collection at AndroidManifest.
    Bundle bundle = new Bundle();
    bundle.putFloat("sessions_sampling_percentage", 100);
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // By default, session is not verbose if developer disabled performance monitoring at build
    // time.
    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isFalse();

    // Case #1: developer has enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isTrue();

    // Case #2: developer has disabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(false);

    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isFalse();
  }

  @Test
  public void shouldCollectGaugesAndEvents_perfMonDeactivated_sessionNotVerbose() {
    ConfigResolver configResolver = ConfigResolver.getInstance();
    Bundle bundle = new Bundle();
    bundle.putFloat("sessions_sampling_percentage", 100);
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    configResolver.setMetadataBundle(new ImmutableBundle(bundle));

    // Session will never be verbose if developer deactivated performance monitoring at build time.
    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isFalse();

    // Case #1: developer has enabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(true);

    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isFalse();

    // Case #2: developer has disabled Performance Monitoring during runtime.
    configResolver.setIsPerformanceCollectionEnabled(false);

    assertThat(PerfSession.shouldCollectGaugesAndEvents()).isFalse();
  }

  @Test
  public void testPerfSessionConversion() {
    PerfSession session1 = new PerfSession("sessionId", mockClock);
    session1.setGaugeAndEventCollectionEnabled(true);

    com.google.firebase.perf.v1.PerfSession perfSession = session1.build();
    Assert.assertEquals(session1.sessionId(), perfSession.getSessionId());
    Assert.assertEquals(
        SessionVerbosity.GAUGES_AND_SYSTEM_EVENTS, perfSession.getSessionVerbosity(0));
  }

  @Test
  public void testPerfSessionConversionWithoutVerbosity() {
    PerfSession session1 = new PerfSession("sessionId", mockClock);

    com.google.firebase.perf.v1.PerfSession perfSession = session1.build();
    Assert.assertEquals(session1.sessionId(), perfSession.getSessionId());
    assertThat(perfSession.getSessionVerbosityList()).isEmpty();
  }

  @Test
  public void testPerfSessionsCreateDisabledGaugeCollectionWhenVerboseSessionForceDisabled() {
    forceNonVerboseSession();
    PerfSession testPerfSession = PerfSession.createWithId("sessionId");
    assertThat(testPerfSession.isGaugeAndEventCollectionEnabled()).isFalse();
  }

  @Test
  public void testPerfSessionsCreateDisabledGaugeCollectionWhenSessionsFeatureDisabled() {
    forceSessionsFeatureDisabled();
    PerfSession testPerfSession = PerfSession.createWithId("sessionId");
    assertThat(testPerfSession.isGaugeAndEventCollectionEnabled()).isFalse();
  }

  @Test
  public void testPerfSessionsCreateEnablesGaugeCollectionWhenVerboseSessionForceEnabled() {
    forceVerboseSession();
    PerfSession testPerfSession = PerfSession.createWithId("sessionId");
    assertThat(testPerfSession.isGaugeAndEventCollectionEnabled()).isTrue();
  }

  @Test
  public void testBuildAndSortMovesTheVerboseSessionToTop() {
    // Force all the sessions from now onwards to be non-verbose
    forceNonVerboseSession();

    // Next, create 3 non-verbose sessions
    List<PerfSession> sessions = new ArrayList<>();
    sessions.add(PerfSession.createWithId("sessionId1"));
    sessions.add(PerfSession.createWithId("sessionId2"));
    sessions.add(PerfSession.createWithId("sessionId3"));

    // Force all the sessions from now onwards to be verbose
    forceVerboseSession();

    // Next, create 2 verbose sessions
    sessions.add(PerfSession.createWithId("sessionId4"));
    sessions.add(PerfSession.createWithId("sessionId5"));

    // Verify that the first session in the list of sessions was not verbose
    assertThat(sessions.get(0).isVerbose()).isFalse();

    com.google.firebase.perf.v1.PerfSession[] perfSessions =
        PerfSession.buildAndSort(ImmutableList.copyOf(sessions));

    // Verify that after building the proto objects for PerfSessions, the first session in the array
    // of proto objects is a verbose session
    assertThat(PerfSession.isVerbose(perfSessions[0])).isTrue();
  }

  @Test
  public void testIsExpiredReturnsFalseWhenCurrentSessionLengthIsLessThanMaxSessionLength() {
    Timer mockTimer = mock(Timer.class);
    when(mockTimer.getDurationMicros())
        .thenReturn(
            TimeUnit.HOURS.toMicros(4)
                - TimeUnit.MINUTES.toMicros(1)); // Default Max Session Length is 4 hours
    when(mockClock.getTime()).thenReturn(mockTimer);

    PerfSession session = new PerfSession("sessionId", mockClock);
    assertThat(session.isSessionRunningTooLong()).isFalse();
  }

  @Test
  public void testIsExpiredReturnsFalseWhenCurrentSessionLengthIsEqualToMaxSessionLength() {
    Timer mockTimer = mock(Timer.class);
    when(mockTimer.getDurationMicros())
        .thenReturn(TimeUnit.HOURS.toMicros(4)); // Default Max Session Length is 4 hours
    when(mockClock.getTime()).thenReturn(mockTimer);

    PerfSession session = new PerfSession("sessionId", mockClock);
    assertThat(session.isSessionRunningTooLong()).isFalse();
  }

  @Test
  public void testIsExpiredReturnsTrueWhenCurrentSessionLengthIsGreaterThanMaxSessionLength() {
    Timer mockTimer = mock(Timer.class);
    when(mockTimer.getDurationMicros())
        .thenReturn(TimeUnit.HOURS.toMicros(5)); // Default Max Session Length is 4 hours
    when(mockClock.getTime()).thenReturn(mockTimer);

    PerfSession session = new PerfSession("sessionId", mockClock);
    assertThat(session.isSessionRunningTooLong()).isTrue();
  }
}
