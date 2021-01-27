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
import static com.google.firebase.perf.internal.ResourceType.NETWORK;
import static com.google.firebase.perf.internal.ResourceType.TRACE;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.transport.RateLimiter.RateLimiterImpl;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Constants.TraceNames;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.PerfMetric;
import com.google.firebase.perf.v1.PerfSession;
import com.google.firebase.perf.v1.SessionVerbosity;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

  private static final long FIFTEEN_SECONDS = TimeUnit.SECONDS.toMicros(15);

  @Mock private Clock mClock;
  @Mock private ConfigResolver mockConfigResolver;

  private long mCurrentTime = 0;

  @Before
  public void setUp() {
    initMocks(this);
    doAnswer(
            new Answer<Timer>() {
              @Override
              public Timer answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new Timer(mCurrentTime, mCurrentTime * 1000);
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
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0f);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0f);

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0, token count is 2.
    RateLimiterImpl limiter =
        new RateLimiterImpl(2.0d / 60, 2, mClock, mockConfigResolver, NETWORK, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();
    // clock is 15 seconds, token count is 1.
    mCurrentTime = 1 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 30 seconds, count is 0.
    mCurrentTime = 2 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 45 seconds, count is 0.
    mCurrentTime = 3 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isFalse();
    ;
    // clock is 60 seconds, count is 0
    mCurrentTime = 4 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 75 seconds, count is 0,
    mCurrentTime = 5 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isFalse();
    // clock is 90 seconds, count is 0
    mCurrentTime = 6 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 105 seconds, count is 0
    mCurrentTime = 7 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isFalse();
    // clock is 120 seconds, count is 0,
    mCurrentTime = 8 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 135 seconds, count is 0,
    mCurrentTime = 9 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isFalse();
    // clock is 150 seconds, count is 0,
    mCurrentTime = 10 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
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
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(1.0f);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(1.0f);

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0, token count is 2.

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);
    PerfMetric metric = PerfMetric.getDefaultInstance();
    // if PerfMetric object has neither TraceMetric or NetworkRequestMetric field set, always return
    // false.
    assertThat(limiter.check(metric)).isFalse();

    PerfMetric trace =
        PerfMetric.newBuilder().setTraceMetric(TraceMetric.getDefaultInstance()).build();
    PerfMetric network =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(NetworkRequestMetric.getDefaultInstance())
            .build();
    // clock is 15 seconds, token count is 1.
    mCurrentTime = 1 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isTrue();
    assertThat(limiter.check(network)).isTrue();
    // clock is 30 seconds, count is 0.
    mCurrentTime = 2 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isTrue();
    assertThat(limiter.check(network)).isTrue();
    // clock is 45 seconds, count is 0.
    mCurrentTime = 3 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isFalse();
    assertThat(limiter.check(network)).isFalse();
    // clock is 60 seconds, count is 0
    mCurrentTime = 4 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isTrue();
    assertThat(limiter.check(network)).isTrue();
    // clock is 75 seconds, count is 0,
    mCurrentTime = 5 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isFalse();
    assertThat(limiter.check(network)).isFalse();
    // clock is 90 seconds, count is 0
    mCurrentTime = 6 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isTrue();
    assertThat(limiter.check(network)).isTrue();
    // clock is 105 seconds, count is 0
    mCurrentTime = 7 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isFalse();
    assertThat(limiter.check(network)).isFalse();
    // clock is 120 seconds, count is 0,
    mCurrentTime = 8 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isTrue();
    assertThat(limiter.check(network)).isTrue();
    // clock is 135 seconds, count is 0,
    mCurrentTime = 9 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isFalse();
    assertThat(limiter.check(network)).isFalse();
    // clock is 150 seconds, count is 0,
    mCurrentTime = 10 * FIFTEEN_SECONDS;
    assertThat(limiter.check(trace)).isTrue();
    assertThat(limiter.check(network)).isTrue();
  }

  @Test
  public void testDeviceSampling_tracesEnabledButNetworkDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5f);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.02f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.49f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();
  }

  @Test
  public void testDeviceSampling_tracesDisabledButNetworkEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.02f);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.49f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
  }

  @Test
  public void getIsDeviceAllowedToSendTraces_8digitSamplingRate_traceIsEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.00000001f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.000000005f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
  }

  @Test
  public void getIsDeviceAllowedToSendTraces_8digitSamplingRate_traceIsDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.00000001f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.000000011f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
  }

  @Test
  public void getIsDeviceAllowedToSendNetwork_8digitSamplingRate_networkIsEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.00000001f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.000000005f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
  }

  @Test
  public void getIsDeviceAllowedToSendNetwork_8digitSamplingRate_networkIsDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.00000001f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.000000011f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();
  }

  @Test
  public void testDeviceSampling_bothTracesAndNetworkEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5f);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.49f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
  }

  @Test
  public void testDeviceSampling_bothTracesAndNetworkDisabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5f);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.51f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();
  }

  @Test
  public void testDeviceSampling_changeInTraceSamplingRateIsImmediatelyEffective() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.5f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.51f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();

    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.75f);

    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isTrue();
  }

  @Test
  public void testDeviceSampling_changeInNetworkSamplingRateIsImmediatelyEffective() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.5f);

    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.51f, mockConfigResolver);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.75f);

    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isTrue();
  }

  @Test
  public void getRateFromNetworkParams_nonDefaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkEventCountBackground()).thenReturn(60L);
    when(mockConfigResolver.getNetworkEventCountForeground()).thenReturn(700L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);

    RateLimiterImpl limiter =
        new RateLimiterImpl(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            NETWORK,
            /* isLogcatEnabled= */ false);

    assertThat(limiter.getRate()).isEqualTo(2.0);
    assertThat(limiter.getForegroundRate()).isEqualTo(700.0d / 60L);
    assertThat(limiter.getForegroundCapacity()).isEqualTo(700L);
    assertThat(limiter.getBackgroundRate()).isEqualTo(60.0d / 60L);
    assertThat(limiter.getBackgroundCapacity()).isEqualTo(60L);
  }

  @Test
  public void getRateFromNetworkParams_defaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();

    RateLimiterImpl limiter =
        new RateLimiterImpl(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            NETWORK,
            /* isLogcatEnabled= */ false);

    assertThat(limiter.getRate()).isEqualTo(2.0);

    assertThat(limiter.getForegroundRate()).isEqualTo(700.0d / 600);
    assertThat(limiter.getForegroundCapacity()).isEqualTo(700L);
    assertThat(limiter.getBackgroundRate()).isEqualTo(70.0d / 600);
    assertThat(limiter.getBackgroundCapacity()).isEqualTo(70L);
  }

  @Test
  public void getRateFromTraceParams_nonDefaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceEventCountBackground()).thenReturn(60L);
    when(mockConfigResolver.getTraceEventCountForeground()).thenReturn(600L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);
    when(mockConfigResolver.getRateLimitSec()).thenReturn(60L);

    RateLimiterImpl limiter =
        new RateLimiterImpl(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            TRACE,
            /* isLogcatEnabled= */ false);

    assertThat(limiter.getRate()).isEqualTo(2.0);
    assertThat(limiter.getForegroundRate()).isEqualTo(600.0d / 60L);
    assertThat(limiter.getForegroundCapacity()).isEqualTo(600L);
    assertThat(limiter.getBackgroundRate()).isEqualTo(60.0d / 60L);
    assertThat(limiter.getBackgroundCapacity()).isEqualTo(60L);
  }

  @Test
  public void getRateFromTraceParams_defaultValues_worksCorrectly() {
    makeConfigResolverReturnDefaultValues();

    RateLimiterImpl limiter =
        new RateLimiterImpl(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            mockConfigResolver,
            TRACE,
            /* isLogcatEnabled= */ false);

    assertThat(limiter.getRate()).isEqualTo(2.0);

    assertThat(limiter.getForegroundRate()).isEqualTo(300.0d / 600);
    assertThat(limiter.getForegroundCapacity()).isEqualTo(300L);
    assertThat(limiter.getBackgroundRate()).isEqualTo(30.0d / 600);
    assertThat(limiter.getBackgroundCapacity()).isEqualTo(30L);
  }

  /**
   * Initial rate is 2/minute, then increate to 4/minute. Compare to test case testRateLimit(), more
   * logs are allowed.
   */
  @Test
  public void testChangeRate() {
    makeConfigResolverReturnDefaultValues();

    // allow 2 logs every minute. token bucket capacity is 2.
    // clock is 0, token count is 2.
    RateLimiterImpl limiter =
        new RateLimiterImpl(2.0d / 60, 2, mClock, mockConfigResolver, TRACE, false);
    PerfMetric metric = PerfMetric.getDefaultInstance();
    // clock is 15 seconds, token count is 1.
    mCurrentTime = 1 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 30 seconds, count is 0.
    mCurrentTime = 2 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 45 seconds, count is 0.
    mCurrentTime = 3 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isFalse();
    // clock is 60 seconds, count is 0
    mCurrentTime = 4 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // change rate to 4/minute
    limiter.setRate(4.0d / 60);
    // clock is 75 seconds, count is 0,
    mCurrentTime = 5 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 90 seconds, count is 0
    mCurrentTime = 6 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 105 seconds, count is 0
    mCurrentTime = 7 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 120 seconds, count is 0,
    mCurrentTime = 8 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 135 seconds, count is 0,
    mCurrentTime = 9 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
    // clock is 150 seconds, count is 0,
    mCurrentTime = 10 * FIFTEEN_SECONDS;
    assertThat(limiter.check(metric)).isTrue();
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
  public void testBackgroundTraceWithCountersIsNotRateLimited() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .putCounters("counter1", 10)
                    .setName(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimited(metric)).isFalse();
  }

  @Test
  public void testBackgroundTraceWithoutCountersIsRateLimited() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .setName(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimited(metric)).isTrue();
  }

  @Test
  public void testForegroundTraceWithCountersIsNotRateLimited() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .putCounters("counter1", 10)
                    .setName(TraceNames.FOREGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimited(metric)).isFalse();
  }

  @Test
  public void testForegroundTraceWithoutCountersIsRateLimited() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder().setName(TraceNames.FOREGROUND_TRACE_NAME.toString()))
            .build();

    assertThat(limiter.isRateLimited(metric)).isTrue();
  }

  @Test
  public void testGaugeMetricIsNotRateLimited() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder().setGaugeMetric(GaugeMetric.getDefaultInstance()).build();

    assertThat(limiter.isRateLimited(metric)).isFalse();
  }

  @Test
  public void testTraceMetricNoSpecialNameIsRateLimited() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setTraceMetric(TraceMetric.newBuilder().setName("devInstrumentedTrace"))
            .build();

    assertThat(limiter.isRateLimited(metric)).isTrue();
  }

  @Test
  public void testNetworkRequestMetricIsRateLimited() {
    makeConfigResolverReturnDefaultValues();
    RateLimiter limiter = new RateLimiter(2.0d / 60, 2, mClock, 0.99f, mockConfigResolver);

    PerfMetric metric =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(NetworkRequestMetric.getDefaultInstance())
            .build();

    assertThat(limiter.isRateLimited(metric)).isTrue();
  }

  @Test
  public void testTracesAreNotSampledWhenSessionIsVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.70f);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();

    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createVerbosePerfSessions())))
            .build();

    assertThat(limiter.check(trace)).isTrue();
  }

  @Test
  public void testNetworkRequestsAreNotSampledWhenSessionIsVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.70f);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    PerfMetric network =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(
                NetworkRequestMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createVerbosePerfSessions())))
            .build();

    assertThat(limiter.check(network)).isTrue();
  }

  @Test
  public void testTracesAreSampledWhenSessionIsNonVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.70f);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();

    PerfMetric trace =
        PerfMetric.newBuilder()
            .setTraceMetric(
                TraceMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();

    assertThat(limiter.check(trace)).isFalse();
  }

  @Test
  public void testNetworkRequestsAreSampledWhenSessionIsNonVerboseAndSamplingEnabled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.70f);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    PerfMetric network =
        PerfMetric.newBuilder()
            .setNetworkRequestMetric(
                NetworkRequestMetric.newBuilder()
                    .addAllPerfSessions(Arrays.asList(createNonVerbosePerfSessions())))
            .build();

    assertThat(limiter.check(network)).isFalse();
  }

  @Test
  public void testGaugesAreNeverSampled() {
    makeConfigResolverReturnDefaultValues();
    when(mockConfigResolver.getTraceSamplingRate()).thenReturn(0.70f);
    when(mockConfigResolver.getNetworkRequestSamplingRate()).thenReturn(0.70f);

    // Passing a value for samplingBucketId which is greater than the sampling rate ensures that
    // the sampling will be enabled causing all the metrics to be dropped
    RateLimiter limiter =
        new RateLimiter(
            /* rate= */ 2,
            /* capacity= */ 2,
            mClock,
            /* samplingBucketId= */ 0.71f,
            mockConfigResolver);
    assertThat(limiter.getIsDeviceAllowedToSendTraces()).isFalse();
    assertThat(limiter.getIsDeviceAllowedToSendNetworkEvents()).isFalse();

    PerfMetric gauge =
        PerfMetric.newBuilder().setGaugeMetric(GaugeMetric.getDefaultInstance()).build();

    assertThat(limiter.check(gauge)).isTrue();
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
