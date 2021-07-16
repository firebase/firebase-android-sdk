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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link TraceMetricBuilder}. */
@RunWith(RobolectricTestRunner.class)
public class TraceMetricBuilderTest extends FirebasePerformanceTestBase {
  private static final String TRACE_1 = "trace_1";
  private static final String TRACE_2 = "trace_1";
  private static final String METRIC_1 = "metric_1";
  private static final String METRIC_2 = "metric_2";
  private static final String TRACE_ATTRIBUTE_KEY = "TRACE_ATTRIBUTE_KEY";
  private static final String TRACE_ATTRIBUTE_VALUE = "TRACE_ATTRIBUTE_VALUE";

  private long currentTime = 0;

  @Mock private Clock clock;

  @Mock private TransportManager transportManager;

  @Mock private AppStateMonitor appStateMonitor;

  @Before
  public void setUp() {
    currentTime = 0;
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
  public void testJustStartAndStopWithoutCheckpointsAndCounters() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();

    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getClientStartTimeUs());
    Assert.assertEquals(1, traceMetric.getDurationUs());
    Assert.assertEquals(0, traceMetric.getCountersCount());
    Assert.assertEquals(0, traceMetric.getSubtracesCount());
  }

  @Test
  public void testAddingCountersWithStartAndStop() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_1, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 1);
    currentTime = 2;
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();

    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getClientStartTimeUs());
    Assert.assertEquals(1, traceMetric.getDurationUs());

    Map<String, Long> counterMap = traceMetric.getCountersMap();

    Assert.assertEquals(2, counterMap.size());
    Assert.assertEquals(Long.valueOf(2), counterMap.get(METRIC_1));
    Assert.assertEquals(Long.valueOf(3), counterMap.get(METRIC_2));
    Assert.assertEquals(0, traceMetric.getSubtracesCount());
  }

  @Test
  public void testIncrementingCounterByX() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    trace.incrementMetric(METRIC_1, 5);
    trace.incrementMetric(METRIC_2, 1);
    trace.incrementMetric(METRIC_2, 10);
    currentTime = 2;
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();

    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getClientStartTimeUs());
    Assert.assertEquals(1, traceMetric.getDurationUs());

    Map<String, Long> counterMap = traceMetric.getCountersMap();

    Assert.assertEquals(2, counterMap.size());
    Assert.assertEquals(Long.valueOf(5), counterMap.get(METRIC_1));
    Assert.assertEquals(Long.valueOf(11), counterMap.get(METRIC_2));
    Assert.assertEquals(0, traceMetric.getSubtracesCount());
  }

  @Test
  public void testAddingSubtraceWithStartAndStop() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.startStage(TRACE_2);
    currentTime = 3;
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();

    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getClientStartTimeUs());
    Assert.assertEquals(2, traceMetric.getDurationUs());
    Assert.assertEquals(0, traceMetric.getCountersCount());

    TraceMetric subtrace = traceMetric.getSubtraces(0);
    Assert.assertEquals(TRACE_2, subtrace.getName());
    Assert.assertEquals(2, subtrace.getClientStartTimeUs());
    Assert.assertEquals(1, subtrace.getDurationUs());
    Assert.assertEquals(0, subtrace.getCountersCount());
    Assert.assertEquals(0, subtrace.getSubtracesCount());
  }

  @Test
  public void testAddingSubtraceAndCountersWithStartAndStop() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
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
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();

    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getClientStartTimeUs());
    Assert.assertEquals(2, traceMetric.getDurationUs());

    Map<String, Long> counterMap = traceMetric.getCountersMap();

    Assert.assertEquals(2, counterMap.size());
    Assert.assertEquals(Long.valueOf(2), counterMap.get(METRIC_1));
    Assert.assertEquals(Long.valueOf(3), counterMap.get(METRIC_2));
    Assert.assertEquals(1, traceMetric.getSubtracesCount());

    TraceMetric subtrace = traceMetric.getSubtraces(0);
    Assert.assertEquals(TRACE_2, subtrace.getName());
    Assert.assertEquals(2, subtrace.getClientStartTimeUs());
    Assert.assertEquals(1, subtrace.getDurationUs());
    Assert.assertEquals(0, subtrace.getCountersCount());
    Assert.assertEquals(0, subtrace.getSubtracesCount());
  }

  @Test
  public void testAddingCustomAttributes() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.startStage(TRACE_2);
    trace.putAttribute(TRACE_ATTRIBUTE_KEY, TRACE_ATTRIBUTE_VALUE);
    currentTime = 3;
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getCustomAttributesCount());
    Assert.assertEquals(TRACE_ATTRIBUTE_VALUE, trace.getAttribute(TRACE_ATTRIBUTE_KEY));
  }

  @Test
  public void testAddingCustomAttributesBeforeStartAfterStop() {
    String beforeStart = "beforeStart";
    String afterStart = "afterStart";
    String afterStop = "afterStop";
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.putAttribute(TRACE_ATTRIBUTE_KEY + beforeStart, TRACE_ATTRIBUTE_VALUE + beforeStart);
    trace.startStage(TRACE_2);
    trace.putAttribute(TRACE_ATTRIBUTE_KEY + afterStart, TRACE_ATTRIBUTE_VALUE + afterStart);
    currentTime = 3;
    trace.stop();
    trace.putAttribute(TRACE_ATTRIBUTE_KEY + afterStop, TRACE_ATTRIBUTE_VALUE + afterStop);
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(2, traceMetric.getCustomAttributesCount());
    Assert.assertEquals(
        TRACE_ATTRIBUTE_VALUE + beforeStart, trace.getAttribute(TRACE_ATTRIBUTE_KEY + beforeStart));
    Assert.assertEquals(
        TRACE_ATTRIBUTE_VALUE + afterStart, trace.getAttribute(TRACE_ATTRIBUTE_KEY + afterStart));
    Assert.assertNull(trace.getAttribute(TRACE_ATTRIBUTE_KEY + afterStop));
  }

  @Test
  public void testAddingMoreThanMaxLocalAttributes() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      trace.putAttribute(TRACE_ATTRIBUTE_KEY + i, TRACE_ATTRIBUTE_VALUE + i);
    }
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(
        Constants.MAX_TRACE_CUSTOM_ATTRIBUTES, traceMetric.getCustomAttributesCount());
    for (int i = 0; i < Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      String attributeValue = TRACE_ATTRIBUTE_VALUE + i;
      String attributeKey = TRACE_ATTRIBUTE_KEY + i;
      Assert.assertEquals(attributeValue, trace.getAttribute(attributeKey));
    }
  }

  @Test
  public void testRemovingCustomAttributes() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.startStage(TRACE_2);
    trace.putAttribute(TRACE_ATTRIBUTE_KEY, TRACE_ATTRIBUTE_VALUE);
    currentTime = 3;
    trace.removeAttribute(TRACE_ATTRIBUTE_KEY);
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(0, traceMetric.getCustomAttributesCount());
  }

  @Test
  public void testAddingAttributeWithNullKey() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.putAttribute(null, TRACE_ATTRIBUTE_VALUE);
    currentTime = 3;
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(0, traceMetric.getCustomAttributesCount());
  }

  @Test
  public void testAddingAttributeWithNullValue() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.putAttribute(TRACE_ATTRIBUTE_KEY, null);
    currentTime = 3;
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(0, traceMetric.getCustomAttributesCount());
  }

  @Test
  public void testRemovingNonExistingCustomAttributes() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.startStage(TRACE_2);
    trace.putAttribute(TRACE_ATTRIBUTE_KEY, TRACE_ATTRIBUTE_VALUE);
    currentTime = 3;
    trace.removeAttribute(TRACE_ATTRIBUTE_KEY + "NonExisting");
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_ATTRIBUTE_VALUE, trace.getAttribute(TRACE_ATTRIBUTE_KEY));
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getCustomAttributesCount());
  }

  @Test
  public void testUpdatingCustomAttributes() {
    Trace trace = new Trace(TRACE_1, transportManager, clock, appStateMonitor);
    currentTime = 1;
    trace.start();
    currentTime = 2;
    trace.startStage(TRACE_2);
    trace.putAttribute(TRACE_ATTRIBUTE_KEY, TRACE_ATTRIBUTE_VALUE);
    currentTime = 3;
    trace.putAttribute(TRACE_ATTRIBUTE_KEY, TRACE_ATTRIBUTE_VALUE + "New");
    trace.stop();
    TraceMetric traceMetric = new TraceMetricBuilder(trace).build();
    Assert.assertEquals(TRACE_ATTRIBUTE_VALUE + "New", trace.getAttribute(TRACE_ATTRIBUTE_KEY));
    Assert.assertEquals(TRACE_1, traceMetric.getName());
    Assert.assertEquals(1, traceMetric.getCustomAttributesCount());
  }
}
