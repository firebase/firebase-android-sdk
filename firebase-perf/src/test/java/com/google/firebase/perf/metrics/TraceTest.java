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

package com.google.firebase.perf.metrics;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.util.Constants.PREFS_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import android.os.Parcel;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeDirectExecutorService;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link Trace}. */
@RunWith(RobolectricTestRunner.class)
public class TraceTest extends FirebasePerformanceTestBase {

  private static final String UNIQUE_TRACE_NAME = "trace_unique";
  private static final String TRACE_1 = "trace_1";
  private static final String TRACE_2 = "trace_1";
  private static final String METRIC_1 = "metric_1";
  private static final String METRIC_2 = "metric_2";

  @Mock private TransportManager mockTransportManager;
  @Mock private Clock mockClock;
  @Mock private AppStateMonitor mockAppStateMonitor;

  private ArgumentCaptor<TraceMetric> arguments;

  private long currentTime;

  @Before
  public void setUp() {
    currentTime = 1;
    initMocks(this);
    doAnswer((Answer<Timer>) invocationOnMock -> new Timer(currentTime)).when(mockClock).getTime();
    arguments = ArgumentCaptor.forClass(TraceMetric.class);

    DeviceCacheManager.clearInstance();
    ConfigResolver.clearInstance();

    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    ConfigResolver configResolver = ConfigResolver.getInstance();
    configResolver.setDeviceCacheManager(new DeviceCacheManager(new FakeDirectExecutorService()));
    configResolver.setApplicationContext(appContext);
  }

  @Test
  public void recordTrace_performanceDisabledBeforeCreation_notCrash() {
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    trace.start();
    trace.incrementMetric("metric1", 100);
    trace.putMetric("metric2", 200);
    trace.getLongMetric("metric2");
    trace.putAttribute("attribute1", "value1");
    trace.putAttribute("attribute2", "value2");
    trace.getAttribute("attribute1");
    trace.removeAttribute("attribute1");
    trace.getAttributes();
    trace.describeContents();
    trace.writeToParcel(Parcel.obtain(), 1);
    trace.stop();

    verify(mockTransportManager, never()).log(any(TraceMetric.class), any());
  }

  @Test
  public void recordTrace_performanceDisabledAfterCreation_notCrash() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);

    trace.start();
    trace.incrementMetric("metric1", 100);
    trace.putMetric("metric2", 200);
    trace.getLongMetric("metric2");
    trace.putAttribute("attribute1", "value1");
    trace.putAttribute("attribute2", "value2");
    trace.getAttribute("attribute1");
    trace.removeAttribute("attribute1");
    trace.getAttributes();
    trace.describeContents();
    trace.writeToParcel(Parcel.obtain(), 1);
    trace.stop();

    verify(mockTransportManager, never()).log(any(TraceMetric.class), any());
  }

  @Test
  public void recordTrace_performanceDisabledAfterStart_notCrash() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    trace.start();
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);

    trace.incrementMetric("metric1", 100);
    trace.putMetric("metric2", 200);
    trace.getLongMetric("metric2");
    trace.putAttribute("attribute1", "value1");
    trace.putAttribute("attribute2", "value2");
    trace.getAttribute("attribute1");
    trace.removeAttribute("attribute1");
    trace.getAttributes();
    trace.describeContents();
    trace.writeToParcel(Parcel.obtain(), 1);
    trace.stop();

    // Trace will still call logger because it checks for primary flag only during trace start time.
    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }

  @Test
  public void recordTrace_performanceEnabledAfterCreation_notCrash() {
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);

    trace.start();
    trace.incrementMetric("metric1", 100);
    trace.putMetric("metric2", 200);
    trace.getLongMetric("metric2");
    trace.putAttribute("attribute1", "value1");
    trace.putAttribute("attribute2", "value2");
    trace.getAttribute("attribute1");
    trace.removeAttribute("attribute1");
    trace.getAttributes();
    trace.describeContents();
    trace.writeToParcel(Parcel.obtain(), 1);
    trace.stop();

    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }

  @Test
  public void recordTrace_performanceEnabledAfterStart_notCrash() {
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    trace.start();
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);
    trace.incrementMetric("metric1", 100);
    trace.putMetric("metric2", 200);
    trace.getLongMetric("metric2");
    trace.putAttribute("attribute1", "value1");
    trace.putAttribute("attribute2", "value2");
    trace.getAttribute("attribute1");
    trace.removeAttribute("attribute1");
    trace.getAttributes();
    trace.describeContents();
    trace.writeToParcel(Parcel.obtain(), 1);
    trace.stop();

    verify(mockTransportManager, never()).log(any(TraceMetric.class), any());
  }

  @Test
  public void recordTrace_performanceEnabledBeforeStop_notCrash() {
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(false);
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    trace.start();
    trace.incrementMetric("metric1", 100);
    trace.putMetric("metric2", 200);
    trace.getLongMetric("metric2");
    trace.putAttribute("attribute1", "value1");
    trace.putAttribute("attribute2", "value2");
    trace.getAttribute("attribute1");
    trace.removeAttribute("attribute1");
    trace.getAttributes();
    trace.describeContents();
    trace.writeToParcel(Parcel.obtain(), 1);
    ConfigResolver.getInstance().setIsPerformanceCollectionEnabled(true);
    trace.stop();

    // Trace will still skip logger because it checks for primary flag only during trace start time.
    verify(mockTransportManager, never()).log(any(TraceMetric.class), any());
  }

  @Test
  public void testJustStartAndStopWithoutCheckpointsAndCounters() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    currentTime = 1;
    trace.start();

    verify(mockAppStateMonitor).registerForAppState(any());

    currentTime = 2;
    trace.stop();

    verify(mockAppStateMonitor).unregisterForAppState(any());

    assertThat(trace.getName()).isEqualTo(TRACE_1);
    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(2);
    assertThat(trace.getSubtraces()).isEmpty();
    assertThat(trace.getCounters()).isEmpty();

    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }

  @Test
  public void testAddingCountersWithStartAndStop() {
    Trace trace = createTraceWithCounters();
    verifyTraceWithCounters(trace);
  }

  @Test
  public void testIncrementingCounterByX() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    currentTime = 1;
    trace.start();

    trace.incrementMetric(METRIC_1, 5);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 10);

    currentTime = 2;
    trace.stop();

    assertThat(trace.getName()).isEqualTo(TRACE_1);
    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(2);
    assertThat(trace.getCounters()).hasSize(2);
    assertThat(trace.getCounters().get(METRIC_1).getCount()).isEqualTo(5);
    assertThat(trace.getCounters().get(METRIC_2).getCount()).isEqualTo(11);
    assertThat(trace.getSubtraces()).isEmpty();

    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }

  @Test
  public void testIncrementingCounterByNegativeValue() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    currentTime = 1;
    trace.start();

    trace.incrementMetric(METRIC_1, 5);
    trace.incrementMetric(METRIC_1, -5);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, -1);

    currentTime = 2;
    trace.stop();

    assertThat(trace.getName()).isEqualTo(TRACE_1);
    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(2);
    assertThat(trace.getCounters()).hasSize(2);
    assertThat(trace.getCounters().get(METRIC_1).getCount()).isEqualTo(0);
    assertThat(trace.getCounters().get(METRIC_2).getCount()).isEqualTo(0);
    assertThat(trace.getSubtraces()).isEmpty();

    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }

  @Test
  public void testIncrementingCounterBeforeStart() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    trace.incrementMetric(METRIC_1, 5);

    currentTime = 1;
    trace.start();

    currentTime = 2;
    trace.stop();

    assertThat(trace.getCounters()).isEmpty();
  }

  @Test
  public void testIncrementingCounterAfterStop() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();

    currentTime = 2;
    trace.stop();

    trace.incrementMetric(METRIC_1, 5);
    assertThat(trace.getCounters()).isEmpty();
  }

  @Test
  public void testAddingSubtraceWithStartAndStop() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();

    currentTime = 2;
    trace.startStage(TRACE_2);

    currentTime = 3;
    trace.stopStage();

    currentTime = 4;
    trace.stop();

    assertThat(trace.getName()).isEqualTo(TRACE_1);
    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(4);
    assertThat(trace.getSubtraces()).hasSize(1);
    assertThat(trace.getCounters()).isEmpty();

    Trace subtrace = trace.getSubtraces().get(0);

    assertThat(subtrace.getName()).isEqualTo(TRACE_2);
    assertThat(subtrace.getStartTime().getMicros()).isEqualTo(2);
    assertThat(subtrace.getEndTime().getMicros()).isEqualTo(3);
    assertThat(subtrace.getSubtraces()).isEmpty();
    assertThat(subtrace.getCounters()).isEmpty();

    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }

  @Test
  public void testAddingSubtraceAndCountersWithStartAndStop() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();

    currentTime = 2;
    trace.startStage(TRACE_2);
    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);

    currentTime = 3;
    trace.stop();

    assertThat(trace.getName()).isEqualTo(TRACE_1);
    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(3);
    assertThat(trace.getSubtraces()).hasSize(1);
    assertThat(trace.getCounters()).hasSize(2);
    assertThat(trace.getCounters().get(METRIC_1).getCount()).isEqualTo(2);
    assertThat(trace.getCounters().get(METRIC_2).getCount()).isEqualTo(3);

    Trace subtrace = trace.getSubtraces().get(0);

    assertThat(subtrace.getName()).isEqualTo(TRACE_2);
    assertThat(subtrace.getStartTime().getMicros()).isEqualTo(2);
    assertThat(subtrace.getEndTime().getMicros()).isEqualTo(3);
    assertThat(subtrace.getSubtraces()).isEmpty();
    assertThat(subtrace.getCounters()).isEmpty();

    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }

  @Test
  public void testGlobalTrace() {
    Trace trace = Trace.getTrace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    Trace.startTrace(TRACE_1);

    currentTime = 2;
    trace.startStage(TRACE_2);
    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);

    currentTime = 3;
    Trace.stopTrace(TRACE_1);

    assertThat(trace.getName()).isEqualTo(TRACE_1);
    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(3);
    assertThat(trace.getSubtraces()).hasSize(1);
    assertThat(trace.getCounters()).hasSize(2);
    assertThat(trace.getCounters().get(METRIC_1).getCount()).isEqualTo(2);
    assertThat(trace.getCounters().get(METRIC_2).getCount()).isEqualTo(3);

    Trace subtrace = trace.getSubtraces().get(0);

    assertThat(subtrace.getName()).isEqualTo(TRACE_2);
    assertThat(subtrace.getStartTime().getMicros()).isEqualTo(2);
    assertThat(subtrace.getEndTime().getMicros()).isEqualTo(3);
    assertThat(subtrace.getSubtraces()).isEmpty();
    assertThat(subtrace.getCounters()).isEmpty();

    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));

    // Trace is removed from global traces when Trace.stopTrace() is called.
    currentTime = 3;
    trace = Trace.startTrace(TRACE_1);
    assertThat(trace).isNull();

    currentTime = 4;
    trace = Trace.stopTrace(TRACE_1);
    assertThat(trace).isNull();

    // Trace is put into global traces again.
    trace = Trace.getTrace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    assertThat(trace).isNotNull();

    currentTime = 5;
    trace = Trace.startTrace(TRACE_1);
    assertThat(trace).isNotNull();

    currentTime = 6;
    trace = Trace.stopTrace(TRACE_1);
    assertThat(trace).isNotNull();
  }

  /** Test fix for b/35856554 */
  @Test
  public void testIsStopped() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    assertThat(trace.isStopped()).isFalse();
    verify(mockTransportManager, never())
        .log(arguments.capture(), nullable(ApplicationProcessState.class));

    currentTime = 2;
    trace.stop();
    assertThat(trace.isStopped()).isTrue();

    trace.stop();
    assertThat(trace.isStopped()).isTrue();

    // Make sure log() method is called only once.
    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));

    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(2);
  }

  @Test
  public void testNoStopIfNotStarted() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    trace.stop();

    assertThat(trace.getEndTime()).isNull();
    verify(mockTransportManager, never())
        .log(arguments.capture(), nullable(ApplicationProcessState.class));

    currentTime = 1;
    trace.start();

    currentTime = 2;
    trace.stop();

    assertThat(trace.getEndTime()).isNotNull();
  }

  @Test
  public void testFinalize() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();

    assertThat(trace.hasStarted()).isTrue();
    assertThat(trace.isStopped()).isFalse();

    try {
      trace.finalize();
    } catch (Throwable e) {
      System.out.println(e);
    }

    verify(mockAppStateMonitor).incrementTsnsCount(eq(Integer.valueOf(1)));
  }

  @Test
  public void testTraceInvalidNameDoesNotGetStarted() {
    Trace trace = new Trace("_aaa", mockTransportManager, mockClock, mockAppStateMonitor);
    trace.start();
    assertThat(trace.hasStarted()).isFalse();

    StringBuilder longStr = new StringBuilder();
    for (int i = 0; i <= Constants.MAX_TRACE_ID_LENGTH; ++i) {
      longStr.append("a");
    }

    trace = new Trace(longStr.toString(), mockTransportManager, mockClock, mockAppStateMonitor);
    trace.start();
    assertThat(trace.hasStarted()).isFalse();

    Constants.TraceNames[] validTraceNames = Constants.TraceNames.values();
    Random random = new Random();

    trace =
        new Trace(
            validTraceNames[random.nextInt(validTraceNames.length)].toString(),
            mockTransportManager,
            mockClock,
            mockAppStateMonitor);
    trace.start();
    assertThat(trace.hasStarted()).isTrue();
  }

  @Test
  public void testParcel() {
    Trace trace1 = createTraceWithCounters();
    verifyTraceWithCounters(trace1);

    Parcel p1 = Parcel.obtain();
    trace1.writeToParcel(p1, 0);
    byte[] bytes = p1.marshall();

    Parcel p2 = Parcel.obtain();
    p2.unmarshall(bytes, 0, bytes.length);
    p2.setDataPosition(0);

    Trace trace2 = Trace.CREATOR_DATAONLY.createFromParcel(p2);
    verifyTraceWithCounters(trace2);

    p1.recycle();
    p2.recycle();
  }

  @Test
  public void testParcelWithoutStopTrace() {
    // start the trace
    Trace trace1 = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace1.start();
    trace1.incrementMetric(METRIC_1, 1);
    trace1.incrementMetric(METRIC_1, 1);
    trace1.incrementMetric(METRIC_2, 1);
    trace1.incrementMetric(METRIC_2, 1);
    trace1.incrementMetric(METRIC_2, 1);
    // without stopping trace.

    Parcel p1 = Parcel.obtain();
    trace1.writeToParcel(p1, 0);
    byte[] bytes = p1.marshall();

    Parcel p2 = Parcel.obtain();
    p2.unmarshall(bytes, 0, bytes.length);
    p2.setDataPosition(0);

    Trace trace2 = Trace.CREATOR_DATAONLY.createFromParcel(p2);
    assertThat(trace2.getName()).isEqualTo(TRACE_1);
    assertThat(trace2.getStartTime().getMicros()).isEqualTo(1);

    // Trace is not stopped, so there is no end time.
    assertThat(trace2.getEndTime()).isNull();
    assertThat(trace2.getCounters()).hasSize(2);
    assertThat(trace2.getCounters().get(METRIC_1).getCount()).isEqualTo(2);
    assertThat(trace2.getCounters().get(METRIC_2).getCount()).isEqualTo(3);
    assertThat(trace2.getSubtraces()).isEmpty();

    verify(mockTransportManager, never()).log(arguments.capture());

    p1.recycle();
    p2.recycle();
  }

  @Test
  public void testMetricWithInvalidNameGetsIgnored() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    trace.incrementMetric("__metric1", 1);

    currentTime = 2;
    trace.stop();
    assertThat(trace.getCounters()).isEmpty();

    trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();

    StringBuilder counterName = new StringBuilder();
    for (int i = 0; i <= Constants.MAX_COUNTER_ID_LENGTH; i++) {
      counterName.append("a");
    }

    trace.incrementMetric(counterName.toString(), 1);

    currentTime = 2;
    trace.stop();
    assertThat(trace.getCounters()).isEmpty();

    trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    Constants.CounterNames[] validCounterNames = Constants.CounterNames.values();
    trace.start();
    trace.incrementMetric(
        validCounterNames[new Random().nextInt(validCounterNames.length)].toString(), 1);

    currentTime = 2;
    trace.stop();
    assertThat(trace.getCounters()).hasSize(1);
  }

  @Test
  public void testSettingOrIncrementingMetricBeforeTraceIsStartedDoesNothing() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    trace.putMetric("metric1", 10);
    trace.incrementMetric("metric2", 10);
    assertThat(trace.getCounters()).isEmpty();
  }

  @Test
  public void testSettingNegativeValueToMetric() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    trace.putMetric("metric1", -10);

    currentTime = 2;
    trace.stop();

    assertThat(trace.getCounters()).hasSize(1);
    assertThat(trace.getCounters().get("metric1").getCount()).isEqualTo(-10);
  }

  @Test
  public void testSettingOrIncrementingMetricAfterTraceIsStoppedDoesNothing() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    trace.start();

    currentTime = 1;
    trace.putMetric("metric1", 10);
    trace.incrementMetric("metric1", 10);

    currentTime = 2;
    trace.stop();
    trace.putMetric("metric2", 10);
    trace.putMetric("metric2", 100);
    trace.incrementMetric("metric1", 10);
    trace.putMetric("metric1", 100);

    assertThat(trace.getCounters()).hasSize(1);
    assertThat(trace.getCounters().get("metric1").getCount()).isEqualTo(20);
  }

  @Test
  public void testMetricWithUnderscoreInNameGetsIgnored() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    trace.incrementMetric("__metric1", 10);
    trace.putMetric("__metric2", 100);

    currentTime = 2;
    trace.stop();

    assertThat(trace.getCounters()).isEmpty();
  }

  @Test
  public void testMetricWithNameExceedsMaxLengthIsIgnored() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();

    StringBuilder metricName = new StringBuilder();
    for (int i = 0; i <= Constants.MAX_COUNTER_ID_LENGTH; i++) {
      metricName.append("a");
    }

    trace.incrementMetric(metricName.toString(), 10);
    metricName.append("2");
    trace.putMetric(metricName.toString(), 100);

    currentTime = 2;
    trace.stop();

    assertThat(trace.getCounters()).isEmpty();
  }

  @Test
  public void testGettingNonExistentMetricDoesntCreateIt() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    long metricValue = trace.getLongMetric("metric1");

    currentTime = 2;
    trace.stop();

    assertThat(metricValue).isEqualTo(0);
    assertThat(trace.getCounters()).isEmpty();
  }

  @Test
  public void testGettingMetricWithNullNameReturnsZeroAndDoesntCreateAMetric() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    long metricValue = trace.getLongMetric(null);

    currentTime = 2;
    trace.stop();

    assertThat(metricValue).isEqualTo(0);
    assertThat(trace.getCounters()).isEmpty();
  }

  @Test
  public void testGetAttributesReturnsCopyOfUnderlyingMap() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    trace.putAttribute("dim1", "value1");
    trace.getAttributes().put("dim2", "values");

    currentTime = 2;
    trace.stop();

    assertThat(trace.getAttribute("dim2")).isNull();
    assertThat(trace.getAttribute("dim1")).isEqualTo("value1");
  }

  @Test
  public void testCanAddAttributeBeforeStart() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    trace.putAttribute("dim1", "value1");
    assertThat(trace.getAttribute("dim1")).isEqualTo("value1");
  }

  @Test
  public void testSettingAttributeBetweenStartAndStop() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    trace.putAttribute("dim1", "value1");

    currentTime = 2;
    trace.stop();

    assertThat(trace.getAttribute("dim1")).isEqualTo("value1");
  }

  @Test
  public void testSettingAttributeAfterStopIsNoOp() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    trace.putAttribute("dim1", "value1");

    currentTime = 2;
    trace.stop();
    trace.putAttribute("dim2", "value2");

    assertThat(trace.getAttribute("dim1")).isEqualTo("value1");
    assertThat(trace.getAttribute("dim2")).isNull();
  }

  @Test
  public void testUpdatingAttributeAfterStopIsNoOp() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();
    trace.putAttribute("dim1", "value1");
    trace.putAttribute("dim1", "value2");

    currentTime = 2;
    trace.stop();

    assertThat(trace.getAttribute("dim1")).isEqualTo("value2");
  }

  @Test
  public void testRemoveAttributeBeforeStart() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.putAttribute("dim1", "value1");
    trace.removeAttribute("dim1");
    trace.start();

    currentTime = 2;
    trace.stop();

    assertThat(trace.getAttribute("dim1")).isNull();
  }

  @Test
  public void testRemoveAttributeBetweenStartAndStop() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.putAttribute("dim1", "value1");
    trace.start();
    trace.removeAttribute("dim1");

    currentTime = 2;
    trace.stop();

    assertThat(trace.getAttribute("dim1")).isNull();
  }

  @Test
  public void testRemoveAttributeAfterStopIsNoOp() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.putAttribute("dim1", "value1");
    trace.start();

    currentTime = 2;
    trace.stop();
    trace.removeAttribute("dim1");

    assertThat(trace.getAttribute("dim1")).isEqualTo("value1");
  }

  @Test
  public void testRemovingNonExistingAttributeAttribute() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.putAttribute("dim1", "value1");
    trace.removeAttribute("dim2");
    trace.start();

    currentTime = 2;
    trace.stop();

    assertThat(trace.getAttribute("dim1")).isEqualTo("value1");
  }

  @Test
  public void testRemovingExistingAndAddingUpdatedValue() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.putAttribute("dim1", "value1");
    trace.removeAttribute("dim1");
    trace.putAttribute("dim1", "value2");
    trace.start();

    currentTime = 2;
    trace.stop();

    assertThat(trace.getAttribute("dim1")).isEqualTo("value2");
  }

  @Test
  public void testAddingMoreThanMaxLocalAttributes() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    currentTime = 1;
    trace.start();

    currentTime = 2;
    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      trace.putAttribute("dim" + i, "value" + i);
    }

    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      trace.putAttribute("dim" + i, "value" + (i + 1));
    }

    trace.stop();

    assertThat(trace.getAttributes()).hasSize(Constants.MAX_TRACE_CUSTOM_ATTRIBUTES);

    for (int i = 0; i < Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      String attributeValue = "value" + (i + 1);
      String attributeKey = "dim" + i;
      assertThat(trace.getAttribute(attributeKey)).isEqualTo(attributeValue);
    }
  }

  @Test
  public void testLongNameGetsIgnored() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    char[] underscores = new char[Constants.MAX_TRACE_ID_LENGTH];
    for (int i = 0; i < underscores.length; i++) {
      underscores[i] = '_';
    }

    String dimName = "dim" + String.valueOf(underscores);
    trace.putAttribute(dimName, "value1");

    assertThat(trace.getAttribute(dimName)).isNull();
    assertThat(trace.getAttributes()).isEmpty();
  }

  @Test
  public void testLongValueGetsIgnored() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);

    char[] underscores = new char[Constants.MAX_ATTRIBUTE_VALUE_LENGTH];
    for (int i = 0; i < underscores.length; i++) {
      underscores[i] = '_';
    }

    String valueString = "value" + String.valueOf(underscores);
    trace.putAttribute("dim", valueString);

    assertThat(trace.getAttribute("dim")).isNull();
    assertThat(trace.getAttributes()).isEmpty();
  }

  @Test
  public void testInvalidKeyNamesAreIgnored() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    trace.putAttribute("_dim", "value1");
    assertThat(trace.getAttribute("_dim")).isNull();
    assertThat(trace.getAttributes()).isEmpty();

    trace.putAttribute("0_dim", "value1");
    assertThat(trace.getAttribute("0_dim")).isNull();
    assertThat(trace.getAttributes()).isEmpty();

    trace.putAttribute("google_dim", "value1");
    assertThat(trace.getAttribute("google_dim")).isNull();
    assertThat(trace.getAttributes()).isEmpty();

    trace.putAttribute("firebase_dim", "value1");
    assertThat(trace.getAttribute("firebase_dim")).isNull();
    assertThat(trace.getAttributes()).isEmpty();

    trace.putAttribute("ga_dim", "value1");
    assertThat(trace.getAttribute("ga_dim")).isNull();
    assertThat(trace.getAttributes()).isEmpty();
  }

  @Test
  public void testGetTracesReturnsSameTraceWhenNameIsSame() {
    Trace trace = Trace.getTrace(UNIQUE_TRACE_NAME);
    assertThat(trace).isNotNull();
    assertThat(trace).isSameInstanceAs(Trace.getTrace(UNIQUE_TRACE_NAME));
  }

  @Test
  public void testSessionIdsEmptyWhenTraceNotStarted() {
    Trace trace = Trace.getTrace("myRandomTrace1");

    assertThat(trace.getSessions()).isNotNull();
    assertThat(trace.getSessions()).isEmpty();

    trace.start();
    trace.stop();
  }

  @Test
  public void testSessionIdsInTrace() {
    Trace trace = Trace.getTrace("myRandomTrace2");
    trace.start();

    assertThat(trace.getSessions()).isNotNull();
    assertThat(trace.getSessions()).isNotEmpty();

    trace.stop();
  }

  @Test
  public void testSessionIdAdditionInTrace() {
    Trace trace = Trace.getTrace("myRandomTrace3");
    trace.start();

    assertThat(trace.getSessions()).isNotNull();

    int numberOfSessionIds = trace.getSessions().size();

    PerfSession perfSession = PerfSession.createWithId("test_session_id");
    SessionManager.getInstance().updatePerfSession(perfSession);
    assertThat(trace.getSessions()).hasSize(numberOfSessionIds + 1);

    trace.stop();
  }

  @Test
  public void testSessionIdNotAddedIfPerfSessionIsNull() {
    Trace trace = Trace.getTrace("myRandomTrace4");
    trace.start();

    assertThat(trace.getSessions()).isNotNull();

    int numberOfSessionIds = trace.getSessions().size();

    new SessionManager(mock(GaugeManager.class), null, mock(AppStateMonitor.class));

    assertThat(trace.getSessions()).hasSize(numberOfSessionIds);

    trace.stop();
  }

  @Test
  public void testTraceStartStopTriggerSingleGaugeCollectionOnVerboseSession() {
    forceVerboseSession();

    GaugeManager gaugeManager = mock(GaugeManager.class);
    Trace trace =
        new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor, gaugeManager);
    trace.start();
    trace.stop();

    verify(gaugeManager, times(2)).collectGaugeMetricOnce(ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void testTraceStartStopDoesNotTriggerSingleGaugeCollectionOnNonVerboseSession() {
    forceNonVerboseSession();

    GaugeManager gaugeManager = mock(GaugeManager.class);
    Trace trace =
        new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor, gaugeManager);
    trace.start();
    trace.stop();

    verify(gaugeManager, never()).collectGaugeMetricOnce(ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void testUpdateSessionWithValidSessionIsAdded() {
    Trace trace = Trace.getTrace("getSessionTest1");
    trace.start();

    assertThat(trace.getSessions()).hasSize(1);
    trace.updateSession(PerfSession.createWithId("test_session_id"));
    assertThat(trace.getSessions()).hasSize(2);

    trace.stop();
  }

  @Test
  public void testUpdateSessionWithNullIsNotAdded() {
    Trace trace = Trace.getTrace("getSessionTest2");
    trace.start();

    assertThat(trace.getSessions()).hasSize(1);
    trace.updateSession(null);
    assertThat(trace.getSessions()).hasSize(1);

    trace.stop();
  }

  private Trace createTraceWithCounters() {
    Trace trace = new Trace(TRACE_1, mockTransportManager, mockClock, mockAppStateMonitor);
    currentTime = 1;
    trace.start();

    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);

    currentTime = 2;
    trace.stop();

    return trace;
  }

  private void verifyTraceWithCounters(Trace trace) {
    assertThat(trace.getName()).isEqualTo(TRACE_1);
    assertThat(trace.getStartTime().getMicros()).isEqualTo(1);
    assertThat(trace.getEndTime().getMicros()).isEqualTo(2);
    assertThat(trace.getCounters()).hasSize(2);
    assertThat(trace.getCounters().get(METRIC_1).getCount()).isEqualTo(2);
    assertThat(trace.getCounters().get(METRIC_2).getCount()).isEqualTo(3);

    assertThat(trace.getSubtraces()).isEmpty();
    verify(mockTransportManager).log(arguments.capture(), nullable(ApplicationProcessState.class));
  }
}
