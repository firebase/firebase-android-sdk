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
import static com.google.firebase.perf.metrics.resource.ResourceType.NETWORK;
import static com.google.firebase.perf.metrics.resource.ResourceType.TRACE;
import static java.time.temporal.ChronoUnit.MICROS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.transport.RateLimiter.RateLimiterImpl;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Constants.TraceNames;
import com.google.firebase.perf.util.Rate;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.PerfMetric;
import com.google.firebase.perf.v1.PerfSession;
import com.google.firebase.perf.v1.SessionVerbosity;
import com.google.firebase.perf.v1.TraceMetric;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link RateLimiter}. */
@RunWith(RobolectricTestRunner.class)
public class RateLimiterTest extends FirebasePerformanceTestBase {

  @Mock private Clock mClock;
  @Mock private ConfigResolver mockConfigResolver;

  private Instant currentTime = Instant.ofEpochSecond(0);
  private static final Rate TWO_TOKENS_PER_MINUTE = new Rate(2, 1, MINUTES);
  private static final Rate FOUR_TOKENS_PER_MINUTE = new Rate(4, 1, MINUTES);
  private static final Rate TWO_TOKENS_PER_SECOND = new Rate(2, 1, SECONDS);
  private static final Rate THREE_TOKENS_PER_SECOND = new Rate(3, 1, SECONDS);

  @Before
  public void setUp() {
    initMocks(this);
    doAnswer(
            new Answer<Timer>() {
              @Override
              public Timer answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new Timer(MICROS.between(Instant.EPOCH, currentTime));
              }
            })
        .when(mClock)
        .getTime();
  }

  /** A simple test case for Token Bucket algorithm. */
  @Test
  public void testRateLimitImpl() {

    makeConfigResolverReturnDefaultValues();

    // Make Config Resolver returns default value for resource sampling rate.
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0);

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0, token count is 2.
    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(TWO_TOKENS_PER_MINUTE, 2, mClock, mockConfigResolver, NETWORK, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();
    // clock is 30 seconds, count is 2, afterwards is 1
    currentTime = currentTime.plusSeconds(30);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 45 seconds, count is 1.5, afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 60 seconds, count is 1, afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 75 seconds, count is 0.5, afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isFalse();
    // clock is 90 seconds, count is 1, afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 105 seconds, count is 0.5, afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isFalse();
    // clock is 120 seconds, count is 1, afterwards is 1
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 135 seconds, count is 0.5, afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isFalse();
    // clock is 150 seconds, count is 1, afterwards is 1
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
  }

  /** An edge test case for Token Bucket algorithm. */
  @Test
  public void testRateLimiterImplWithIrregularTimeIntervals() {

    makeConfigResolverReturnDefaultValues();

    // Make Config Resolver returns default value for resource sampling rate.
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0);

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0s, token count is 2.0.
    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(TWO_TOKENS_PER_MINUTE, 2, mClock, mockConfigResolver, NETWORK, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();
    // clock is 20s, count before check is 2.00, 0.00 new tokens added, count after check is 1.00
    currentTime = currentTime.plusSeconds(20);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 60s, count before check is 1.00, 1.00 new tokens added, count after check is 1.00
    currentTime = currentTime.plusSeconds(40);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 89s, count before check is 1.00, 0.96 new tokens added, count after check is 0.96
    currentTime = currentTime.plusSeconds(29);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 95s, count before check is 0.96, 0.20 new tokens added, count after check is 0.16
    currentTime = currentTime.plusSeconds(6);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 110s, count before check is 0.16, 0.50 new tokens added, count after check is 0.66
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isFalse();
    // clock is 160s, count before check is 0.66, 1.34 new tokens added, count after check is 1.00
    currentTime = currentTime.plusSeconds(50);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 161s, count before check is 1.00, 0.03 new tokens added, count after check is 0.03
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 162s, count before check is 0.03, 0.03 new tokens added, count after check is 0.06
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isFalse();
  }

  @Test
  public void
      testRateLimiterImplWithLongTimeGapBetweenEvents_doesNotAccumulateTokensOrCauseBurst() {

    makeConfigResolverReturnDefaultValues();

    // Make Config Resolver returns default value for resource sampling rate.
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0);

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0, token count is 2.
    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(TWO_TOKENS_PER_MINUTE, 2, mClock, mockConfigResolver, NETWORK, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();

    // clock is 0, count before check is 2, 0 new tokens added, count after check is 1
    currentTime = currentTime.plusSeconds(0);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 30, count before check is 1, 1 new tokens added, count after check is 1
    currentTime = currentTime.plusSeconds(30);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 59, count before check is 1, 0.96 new tokens added, count after check is 0.96
    currentTime = currentTime.plusSeconds(29);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 60, count before check is 0.96, 0.04 new tokens added, count after check is 0
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();

    // clock is 660, count before check is 0, 2 new tokens added, count after check is 1
    currentTime = currentTime.plusSeconds(600);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 661, count before check is 1, 0.03 new tokens added, count after check is 0.03
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 662, count before check is 0.03, 0.03 new tokens added, count after check is 0.06
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isFalse();
    // clock is 663, count before check is 0.06, 0.03 new tokens added, count after check is 0.10
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isFalse();
  }

  @Test
  public void testRateLimiterImplWithBursts_rateLessThanCapacity_doesNotAllowMoreThanCapacity() {

    makeConfigResolverReturnDefaultValues();

    // Make Config Resolver returns default value for resource sampling rate.
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0);

    // allow 3 logs per second. token bucket capacity is 4.
    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(THREE_TOKENS_PER_SECOND, 4, mClock, mockConfigResolver, NETWORK, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();

    // clock is 0, token count starts at 4, none should be replenished
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isFalse();
    assertThat(limiterImpl.check(metric)).isFalse();

    // clock is 1 second, 3 events should be allowed within the second
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isFalse();

    // the first burst has finished, and there are 1 event per second for the next 3 seconds
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();

    // after 10 seconds, the second burst is here, but capacity = 4 events are allowed
    currentTime = currentTime.plusSeconds(10);
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isFalse();
    assertThat(limiterImpl.check(metric)).isFalse();
    assertThat(limiterImpl.check(metric)).isFalse();
  }

  @Test
  public void testRateLimiterImplWithBursts_rateMoreThanCapacity_doesNotAllowMoreThanCapacity() {

    makeConfigResolverReturnDefaultValues();

    // Make Config Resolver returns default value for resource sampling rate.
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0);

    // allow 3 logs per second. token bucket capacity is 2.
    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(THREE_TOKENS_PER_SECOND, 2, mClock, mockConfigResolver, NETWORK, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();

    // clock is 0, token count starts at 2, none should be replenished
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isFalse();
    assertThat(limiterImpl.check(metric)).isFalse();

    // clock is 1 second, 2 events should be allowed within the second due to capacity cap
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isFalse();
    assertThat(limiterImpl.check(metric)).isFalse();

    // the first burst has finished, and there are 1 event per second for the next 3 seconds
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();
    currentTime = currentTime.plusSeconds(1);
    assertThat(limiterImpl.check(metric)).isTrue();

    // the second burst is here, but only capacity = 2 events are allowed
    currentTime = currentTime.plusSeconds(10);
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isTrue();
    assertThat(limiterImpl.check(metric)).isFalse();
    assertThat(limiterImpl.check(metric)).isFalse();
  }

  /**
   * Inside class RateLimiter, there is one RateLimiterImpl instance for TraceMetric object, and one
   * RateLimiterImpl instance for NetworkRequestMetric object, each RateLimiterImpl instance is a
   * token bucket. Two token buckets works separately
   */
  @Test
  public void testRateLimit() {

    makeConfigResolverReturnDefaultValues();

    // Make Config Resolver returns default value for resource sampling rate.
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0);

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0, token count is 2.

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0, mockConfigResolver);
    PerfMetric metric = PerfMetric.getDefaultInstance();
    // if PerfMetric object has neither TraceMetric or NetworkRequestMetric field set, always return
    // true.
    assertThat(limiter.isEventRateLimited(metric)).isTrue();

    PerfMetric trace =
        PerfMetric.newBuilder().setTraceMetric(TraceMetric.getDefaultInstance()).build();
    PerfMetric network =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(NetworkRequestMetric.getDefaultInstance())
            .build();
    // clock is 15 seconds, token count afterwards is 1.
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isFalse();
    assertThat(limiter.isEventRateLimited(network)).isFalse();
    // clock is 30 seconds, count afterwards is 0.5.
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isFalse();
    assertThat(limiter.isEventRateLimited(network)).isFalse();
    // clock is 45 seconds, count afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isFalse();
    assertThat(limiter.isEventRateLimited(network)).isFalse();
    // clock is 60 seconds, count afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isTrue();
    assertThat(limiter.isEventRateLimited(network)).isTrue();
    // clock is 75 seconds, count afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isFalse();
    assertThat(limiter.isEventRateLimited(network)).isFalse();
    // clock is 90 seconds, count afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isTrue();
    assertThat(limiter.isEventRateLimited(network)).isTrue();
    // clock is 105 seconds, count afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isFalse();
    assertThat(limiter.isEventRateLimited(network)).isFalse();
    // clock is 120 seconds, count afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isTrue();
    assertThat(limiter.isEventRateLimited(network)).isTrue();
    // clock is 135 seconds, count afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isFalse();
    assertThat(limiter.isEventRateLimited(network)).isFalse();
    // clock is 150 seconds, count afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiter.isEventRateLimited(trace)).isTrue();
    assertThat(limiter.isEventRateLimited(network)).isTrue();
  }

  @Test
  public void testDeviceSampling_tracesEnabledButNetworkDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.02);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.49f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();
  }

  @Test
  public void testDeviceSampling_tracesDisabledButNetworkEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.02);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.49f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
  }

  @Test
  public void testDeviceSampling_tracesEnabledButFragmentDisabled_dropsFragmentTrace() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5);
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.02);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.49f, 0.49f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isFalse();
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();

    // Fragment trace should be dropped
    PerfMetric fragmentTrace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName("_st_TestFragment")
                    .putCustomAttributes(Constants.ACTIVITY_ATTRIBUTE_KEY, "TestActivity")
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();
    assertThat(limiter.isFragmentScreenTrace(fragmentTrace)).isTrue();
    assertThat(limiter.isEventSampled(fragmentTrace)).isFalse();

    // Non-fragment trace should be sampled
    PerfMetric activityTrace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName("_st_TestActivity")
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();
    assertThat(limiter.isFragmentScreenTrace(activityTrace)).isFalse();
    assertThat(limiter.isEventSampled(activityTrace)).isTrue();
  }

  @Test
  public void testDeviceSampling_tracesDisabledButFragmentEnabled_dropsFragmentTrace() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.02);
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.49f, 0.49f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();

    // All traces including fragment trace should be dropped
    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName("_st_TestFragment")
                    .putCustomAttributes(Constants.ACTIVITY_ATTRIBUTE_KEY, "TestActivity")
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();
    assertThat(limiter.isFragmentScreenTrace(trace)).isTrue();
    assertThat(limiter.isEventSampled(trace)).isFalse();
  }

  @Test
  public void getIsDeviceAllowedToSendTraces_8digitSamplingRate_traceIsEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.00000001);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.000000005f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
  }

  @Test
  public void getIsDeviceAllowedToSendTraces_8digitSamplingRate_traceIsDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.00000001);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.000000011f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
  }

  @Test
  public void getIsDeviceAllowedToSendNetwork_8digitSamplingRate_networkIsEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.00000001);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.000000005f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
  }

  @Test
  public void getIsDeviceAllowedToSendNetwork_8digitSamplingRate_networkIsDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.00000001);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.000000011f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();
  }

  @Test
  public void getIsDeviceAllowedToSendFragmentScreenTraces_8digitSamplingRate_fragmentIsEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.00000001);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.000000005f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isTrue();
  }

  @Test
  public void getIsDeviceAllowedToSendFragmentScreenTraces_8digitSamplingRate_fragmentIsDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.00000001);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0, 0.000000011f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isFalse();
  }

  @Test
  public void testDeviceSampling_bothTracesAndNetworkEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.49f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
  }

  @Test
  public void testDeviceSampling_bothTracesAndNetworkDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.51f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();
  }

  @Test
  public void testDeviceSampling_bothTracesAndFragmentEnabled_acceptsFragmentTrace() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5);
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.49f, 0.49f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isTrue();

    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName("_st_TestFragment")
                    .putCustomAttributes(Constants.ACTIVITY_ATTRIBUTE_KEY, "TestActivity")
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();
    assertThat(limiter.isFragmentScreenTrace(trace)).isTrue();
    assertThat(limiter.isEventSampled(trace)).isTrue();
  }

  @Test
  public void testDeviceSampling_changeInTraceSamplingRateIsImmediatelyEffective() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.51f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();

    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.75);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
  }

  @Test
  public void testDeviceSampling_changeInNetworkSamplingRateIsImmediatelyEffective() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.51f, 0, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.75);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
  }

  @Test
  public void testDeviceSampling_changeInFragmentSamplingRateIsImmediatelyEffective() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.5);

    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0, 0.51f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isFalse();

    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.75);

    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isTrue();
  }

  @Test
  public void getRateFromNetworkParams_nonDefaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkEventCountBackground()).thenReturn(60L);
    when(mockConfigResolver.getNetworkEventCountForeground()).thenReturn(700L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);

    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            NETWORK,
            /* isLogcatEnabled= */ false);

    assertThat(limiterImpl.getRate().getTokensPerSeconds()).isEqualTo(2.0);
    assertThat(limiterImpl.getForegroundRate().getTokensPerSeconds()).isEqualTo(700.0d / 60L);
    assertThat(limiterImpl.getForegroundCapacity()).isEqualTo(700L);
    assertThat(limiterImpl.getBackgroundRate().getTokensPerSeconds()).isEqualTo(60.0d / 60L);
    assertThat(limiterImpl.getBackgroundCapacity()).isEqualTo(60L);
  }

  @Test
  public void getRateFromNetworkParams_defaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();

    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            NETWORK,
            /* isLogcatEnabled= */ false);

    assertThat(limiterImpl.getRate().getTokensPerSeconds()).isEqualTo(2.0);
    assertThat(limiterImpl.getForegroundRate().getTokensPerSeconds()).isEqualTo(700.0d / 600);
    assertThat(limiterImpl.getForegroundCapacity()).isEqualTo(700L);
    assertThat(limiterImpl.getBackgroundRate().getTokensPerSeconds()).isEqualTo(70.0d / 600);
    assertThat(limiterImpl.getBackgroundCapacity()).isEqualTo(70L);
  }

  @Test
  public void getRateFromTraceParams_nonDefaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceEventCountBackground()).thenReturn(60L);
    when(mockConfigResolver.getTraceEventCountForeground()).thenReturn(600L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);

    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            TRACE,
            /* isLogcatEnabled= */ false);

    assertThat(limiterImpl.getRate().getTokensPerSeconds()).isEqualTo(2.0);
    assertThat(limiterImpl.getForegroundRate().getTokensPerSeconds()).isEqualTo(600.0d / 60L);
    assertThat(limiterImpl.getForegroundCapacity()).isEqualTo(600L);
    assertThat(limiterImpl.getBackgroundRate().getTokensPerSeconds()).isEqualTo(60.0d / 60L);
    assertThat(limiterImpl.getBackgroundCapacity()).isEqualTo(60L);
  }

  @Test
  public void getRateFromTraceParams_defaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();

    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            TRACE,
            /* isLogcatEnabled= */ false);

    assertThat(limiterImpl.getRate().getTokensPerSeconds()).isEqualTo(2.0);

    assertThat(limiterImpl.getForegroundRate().getTokensPerSeconds()).isEqualTo(300.0d / 600);
    assertThat(limiterImpl.getForegroundCapacity()).isEqualTo(300L);
    assertThat(limiterImpl.getBackgroundRate().getTokensPerSeconds()).isEqualTo(30.0d / 600);
    assertThat(limiterImpl.getBackgroundCapacity()).isEqualTo(30L);
  }

  /**
   * Initial rate is 2/minute, then increase to 4/minute. Compare to test case testRateLimit(), more
   * logs are allowed.
   */
  @Test
  public void testChangeRate() {
    makeConfigResolverReturnDefaultValues();

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0, token count is 2.
    RateLimiterImpl limiterImpl =
        new RateLimiterImpl(TWO_TOKENS_PER_MINUTE, 2, mClock, mockConfigResolver, TRACE, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();
    // clock is 0 seconds, token count is 2, afterwards is 1
    currentTime = currentTime.plusSeconds(0);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 15 seconds, count is 1.5, afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 30 seconds, count is 1, afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 45 seconds, count is 0.5, afterwards is 0.5
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isFalse();
    // clock is 60 seconds, count is 1, afterwards is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();

    // change rate to 4/minute
    limiterImpl.setRate(FOUR_TOKENS_PER_MINUTE);

    // clock is 75 seconds, count is 0,
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 90 seconds, count is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 105 seconds, count is 0
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 120 seconds, count is 0,
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 135 seconds, count is 0,
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
    // clock is 150 seconds, count is 0,
    currentTime = currentTime.plusSeconds(15);
    assertThat(limiterImpl.check(metric)).isTrue();
  }

  /**
   * Tests the randomness of the bucket ID generation algorithm.
   *
   * <p>To test randomness, this test generates a random number 100000 times, uses that to generate
   * a bucketId, scales the bucketId to be between 1 and 100 (by multipling by 100 and then verifies
   * that there is at least one bucket ID in each "bucket" between 1 and 100 which is a decent
   * enough test of randomness.
   */
  @Test
  public void samplingBucketIdRandomness() {
    Map<Long, Long> bucketCount = new HashMap<>();
    final long count = 100000;
    for (long i = 0; i < count; ++i) {
      long bucket = (long) (RateLimiter.getSamplingBucketId() * 100 + 1);
      if (bucketCount.containsKey(bucket)) {
        bucketCount.put(bucket, bucketCount.get(bucket) + 1);
      } else {
        bucketCount.put(bucket, 1L);
      }
    }

    for (long bucket : bucketCount.keySet()) {
      assertThat(bucket).isAtLeast(1L);
      assertThat(bucket).isAtMost(100L);
    }

    for (long bucket = 1; bucket <= 100; ++bucket) {
      assertThat(bucketCount).containsKey(bucket);
    }
  }

  @Test
  public void testBackgroundTraceWithCountersIsNotRateLimitApplicable() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .putCounters("counter1", 10)
                    .setName(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimitApplicable(metric)).isFalse();
  }

  @Test
  public void testBackgroundTraceWithoutCountersIsRateLimitApplicable() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimitApplicable(metric)).isTrue();
  }

  @Test
  public void testForegroundTraceWithCountersIsNotRateLimitApplicable() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .putCounters("counter1", 10)
                    .setName(TraceNames.FOREGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimitApplicable(metric)).isFalse();
  }

  @Test
  public void testForegroundTraceWithoutCountersIsRateLimitApplicable() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder().setName(TraceNames.FOREGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimitApplicable(metric)).isTrue();
  }

  @Test
  public void testGaugeMetricIsNotRateLimitApplicable() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder().setGaugeMetric(GaugeMetric.getDefaultInstance()).build();

    assertThat(limiter.isRateLimitApplicable(metric)).isFalse();
  }

  @Test
  public void testTraceMetricNoSpecialNameIsRateLimitApplicable() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(TraceMetric.newBuilder().setName("devInstrumentedTrace"))
            .build();

    assertThat(limiter.isRateLimitApplicable(metric)).isTrue();
  }

  @Test
  public void testNetworkRequestMetricIsRateLimitApplicable() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter =
        new RateLimiter(TWO_TOKENS_PER_MINUTE, 2, mClock, 0.99f, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(NetworkRequestMetric.getDefaultInstance())
            .build();

    assertThat(limiter.isRateLimitApplicable(metric)).isTrue();
  }

  @Test
  public void testTracesAreNotSampledWhenSessionIsVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.70);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            /* fragmentBucketId= */ 0,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();

    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createVerbosePerfSessions())))
            .build();

    assertThat(limiter.isEventSampled(trace)).isTrue();
  }

  @Test
  public void testNetworkRequestsAreNotSampledWhenSessionIsVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.70);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            /* fragmentBucketId= */ 0,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    PerfMetric network =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(
                NetworkRequestMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createVerbosePerfSessions())))
            .build();

    assertThat(limiter.isEventSampled(network)).isTrue();
  }

  @Test
  public void isEventSampled_fragmentWithVerboseSessionEnabled_returnsTrue() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.70);

    // Passing a value for samplingBucketId which is greater than the sampling rate means that
    // the sampling dice roll failed causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0,
            /* fragmentBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isFalse();

    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName("_st_TestFragment")
                    .putCustomAttributes(Constants.ACTIVITY_ATTRIBUTE_KEY, "TestActivity")
                    .addAllPerfSessions(Arrays.asList(createVerbosePerfSessions())))
            .build();
    assertThat(limiter.isFragmentScreenTrace(trace)).isTrue();
    assertThat(limiter.isEventSampled(trace)).isTrue();
  }

  @Test
  public void testTracesAreSampledWhenSessionIsNonVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.70);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            /* fragmentBucketId= */ 0,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();

    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();

    assertThat(limiter.isEventSampled(trace)).isFalse();
  }

  @Test
  public void testNetworkRequestsAreSampledWhenSessionIsNonVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.70);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            /* fragmentBucketId= */ 0,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    PerfMetric network =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(
                NetworkRequestMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();

    assertThat(limiter.isEventSampled(network)).isFalse();
  }

  @Test
  public void isEventSampled_fragmentWithVerboseSessionDisabled_returnsFalse() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.70);
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0);

    // Passing a value for samplingBucketId which is greater than the sampling rate means that
    // the sampling dice roll failed causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0,
            /* fragmentBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendFragmentScreenTraces()).isFalse();

    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName("_st_TestFragment")
                    .putCustomAttributes(Constants.ACTIVITY_ATTRIBUTE_KEY, "TestActivity")
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();
    assertThat(limiter.isFragmentScreenTrace(trace)).isTrue();
    assertThat(limiter.isEventSampled(trace)).isFalse();
  }

  @Test
  public void testGaugesAreNeverSampled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.70);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.70);
    when(mockConfigResolver.getFragmentSamplingRate()).thenReturn(0.70);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ TWO_TOKENS_PER_SECOND,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            /* fragmentBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    PerfMetric gauge =
        PerfMetric.newBuilder().setGaugeMetric(GaugeMetric.getDefaultInstance()).build();

    assertThat(limiter.isEventSampled(gauge)).isTrue();
  }

  /**
   * Makes the {@link #mockConfigResolver} return the default value passed to the get config
   * methods.
   */
  private void makeConfigResolverReturnDefaultValues() {
    when(mockConfigResolver.getNetworkEventCountBackground()).thenReturn(70L);
    when(mockConfigResolver.getNetworkEventCountForeground()).thenReturn(700L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(600L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(600L);
    when(mockConfigResolver.getTraceEventCountBackground()).thenReturn(30L);
    when(mockConfigResolver.getTraceEventCountForeground()).thenReturn(300L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(600L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(600L);
  }

  // TODO(b/118844549): Migrate to lists when TraceMetric is migrated.
  private PerfSession[] createVerbosePerfSessions() {
    PerfSession[] perfSessions = new PerfSession[1];
    PerfSession perfSessions1 =
        PerfSession.newBuilder()
            .setSessionId("abcdefg")
            .addSessionVerbosity(SessionVerbosity.GAUGES_AND_SYSTEM_EVENTS)
            .build();
    perfSessions[0] = perfSessions1;

    return perfSessions;
  }

  // TODO(b/118844549): Migrate to lists when TraceMetric is migrated.
  private PerfSession[] createNonVerbosePerfSessions() {
    PerfSession[] perfSessions = new PerfSession[1];
    PerfSession perfSessions1 = PerfSession.newBuilder().setSessionId("abcdefg").build();
    perfSessions[0] = perfSessions1;

    return perfSessions;
  }
}
