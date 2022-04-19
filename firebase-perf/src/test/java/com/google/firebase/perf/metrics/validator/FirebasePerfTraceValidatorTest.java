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

package com.google.firebase.perf.metrics.validator;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.metrics.validator.FirebasePerfTraceValidator}. */
@RunWith(RobolectricTestRunner.class)
public class FirebasePerfTraceValidatorTest extends FirebasePerformanceTestBase {

  private long currentTime = 0;

  @Mock private Clock clock;

  @Before
  public void setUp() {
    initMocks(this);
    doAnswer(
            new Answer<Timer>() {
              @Override
              public Timer answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new Timer(currentTime);
              }
            })
        .when(clock)
        .getTime();
  }

  @Test
  public void testIsValidTrace() {
    TraceMetric trace = createValidTraceMetric().build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isTrue();
  }

  @Test
  public void testExceedMaxSubtrace() {
    TraceMetric.Builder trace = createValidTraceMetric();

    TraceMetric.Builder subtrace = createValidTraceMetric();
    TraceMetric subSubtrace = createValidTraceMetric().build();

    subtrace.addSubtraces(subSubtrace);
    trace.addSubtraces(subtrace);
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testNullTraceId() {
    TraceMetric trace = createValidTraceMetric().clearName().build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testEmptyTraceId() {
    TraceMetric trace = createValidTraceMetric().setName("").build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testExceedsMaxLengthTraceId() {
    TraceMetric.Builder trace = createValidTraceMetric();
    StringBuilder traceName = new StringBuilder();
    for (int i = 0; i <= Constants.MAX_TRACE_ID_LENGTH; i++) {
      traceName.append("a");
    }
    trace.setName(traceName.toString());
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testNullDuration() {
    TraceMetric trace = createValidTraceMetric().clearDurationUs().build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testNegativeDuration() {
    TraceMetric trace = createValidTraceMetric().setDurationUs(-20L).build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testInvalidSubtrace() {
    TraceMetric.Builder trace = createValidTraceMetric();
    TraceMetric subtrace = createValidTraceMetric().clearName().build();
    trace.addSubtraces(subtrace);
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testEmptyCounterId() {
    TraceMetric trace = createValidTraceMetric().putCounters("", 10).build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testExceedsMaxLengthCounterId() {
    TraceMetric.Builder trace = createValidTraceMetric();
    StringBuilder counterName = new StringBuilder();
    for (int i = 0; i <= Constants.MAX_COUNTER_ID_LENGTH; i++) {
      counterName.append("a");
    }
    trace.putCounters(counterName.toString(), 10);
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testZeroCounterValue() {
    TraceMetric trace = createValidTraceMetric().putCounters("counterKey", 0).build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isTrue();
  }

  @Test
  public void testNegativeCounterValue() {
    TraceMetric trace = createValidTraceMetric().putCounters("counterKey", -2).build();
    assertThat(new FirebasePerfTraceValidator(trace).isValidPerfMetric()).isTrue();
  }

  @Test
  public void testInvalidCounterSubtrace() {
    TraceMetric.Builder trace = createValidTraceMetric();
    TraceMetric subtrace = createValidTraceMetric().putCounters("", 10).build();
    trace.addSubtraces(subtrace);
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void screenTrace_shouldNotAllowNonPositiveTotalFrames() {
    TraceMetric.Builder trace =
        createValidTraceMetric().setName(Constants.SCREEN_TRACE_PREFIX + "TestActivity");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
    trace.putCounters(Constants.CounterNames.FRAMES_TOTAL.toString(), 0L);
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeWithUnderscorePrefix_marksPerfMetricInvalid() {
    TraceMetric.Builder trace = createValidTraceMetric().putCustomAttributes("_test", "value");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeWithNumberPrefix_marksPerfMetricInvalid() {
    TraceMetric.Builder trace = createValidTraceMetric().putCustomAttributes("0_test", "value");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeWithGooglePrefix_marksPerfMetricInvalid() {
    TraceMetric.Builder trace =
        createValidTraceMetric().putCustomAttributes("google_test", "value");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeWithFirebasePrefix_marksPerfMetricInvalid() {
    TraceMetric.Builder trace =
        createValidTraceMetric().putCustomAttributes("firebase_test", "value");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeWithGAPrefix_marksPerfMetricInvalid() {
    TraceMetric.Builder trace = createValidTraceMetric().putCustomAttributes("ga_test", "value");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeEmptyValue_marksPerfMetricInvalid() {
    TraceMetric.Builder trace = createValidTraceMetric().putCustomAttributes("key", "");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeEmptyKey_marksPerfMetricInvalid() {
    TraceMetric.Builder trace = createValidTraceMetric().putCustomAttributes("", "value");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeEmptyKeyAndValue_marksPerfMetricInvalid() {
    TraceMetric.Builder trace = createValidTraceMetric().putCustomAttributes("", "");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeWithLongKey_marksPerfMetricInvalid() {
    TraceMetric.Builder trace =
        createValidTraceMetric()
            .putCustomAttributes("a".repeat(Constants.MAX_ATTRIBUTE_KEY_LENGTH + 1), "value");
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void traceValidator_customAttributeWithLongValue_marksPerfMetricInvalid() {
    TraceMetric.Builder trace =
        createValidTraceMetric()
            .putCustomAttributes("key", "a".repeat(Constants.MAX_ATTRIBUTE_VALUE_LENGTH + 1));
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isFalse();
  }

  @Test
  public void testIsValid() {
    TraceMetric.Builder trace = createValidTraceMetric().putCounters("counter", 2);
    TraceMetric subtrace =
        createValidTraceMetric().setName("subtrace1").putCounters("subtraceCounter", 2).build();
    trace.addSubtraces(subtrace);
    assertThat(new FirebasePerfTraceValidator(trace.build()).isValidPerfMetric()).isTrue();
  }

  @Test
  public void testAbsenceOfRequiredFieldsFailsValidation() {
    TraceMetric.Builder validTraceMetric = createValidTraceMetric();
    FirebasePerfTraceValidator traceValidator =
        new FirebasePerfTraceValidator(validTraceMetric.build());
    assertThat(traceValidator.isValidPerfMetric()).isTrue();

    TraceMetric.Builder traceMetricToTest = validTraceMetric.clone().clearName();
    assertThat(new FirebasePerfTraceValidator(traceMetricToTest.build()).isValidPerfMetric())
        .isFalse();

    traceMetricToTest = validTraceMetric.clone();
    traceMetricToTest.clearClientStartTimeUs();
    assertThat(new FirebasePerfTraceValidator(traceMetricToTest.build()).isValidPerfMetric())
        .isFalse();

    traceMetricToTest = validTraceMetric.clone();
    traceMetricToTest.clearDurationUs();
    assertThat(new FirebasePerfTraceValidator(traceMetricToTest.build()).isValidPerfMetric())
        .isFalse();
  }

  private TraceMetric.Builder createValidTraceMetric() {
    String traceName = "trace_1";
    long expectedClientStartTime = 1;
    long expectedTraceDuration = 50;

    TransportManager transportManager = mock(TransportManager.class);
    AppStateMonitor appStateMonitor = mock(AppStateMonitor.class);
    ArgumentCaptor<TraceMetric> argMetric = ArgumentCaptor.forClass(TraceMetric.class);

    Trace trace = new Trace(traceName, transportManager, clock, appStateMonitor);
    currentTime = expectedClientStartTime;
    trace.start();
    currentTime += expectedTraceDuration;
    trace.stop();
    verify(transportManager)
        .log(argMetric.capture(), ArgumentMatchers.nullable(ApplicationProcessState.class));
    TraceMetric traceMetric = argMetric.getValue();

    assertThat(traceMetric.getName()).isEqualTo(traceName);
    assertThat(traceMetric.getClientStartTimeUs()).isEqualTo(expectedClientStartTime);
    assertThat(traceMetric.getDurationUs()).isEqualTo(expectedTraceDuration);

    return traceMetric.toBuilder();
  }
}
