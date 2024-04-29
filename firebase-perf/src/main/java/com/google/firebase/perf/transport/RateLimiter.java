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

import static com.google.firebase.perf.metrics.resource.ResourceType.NETWORK;
import static com.google.firebase.perf.metrics.resource.ResourceType.TRACE;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.resource.ResourceType;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Rate;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.Utils;
import com.google.firebase.perf.v1.PerfMetric;
import com.google.firebase.perf.v1.PerfSession;
import com.google.firebase.perf.v1.SessionVerbosity;
import java.util.List;
import java.util.Random;

/**
 * Implement the Token Bucket rate limiting algorithm. The token bucket initially holds "capacity"
 * number of tokens. when the request comes in, base on the last time token is issued, token is
 * replenished into the bucket at the "rate", and token is consumed by the request. It depends on
 * two parameters: Rate: the number of token generated per minute. Capacity: bucket capacity, the
 * initial number of tokens, also the max number of burst allowed.
 */
final class RateLimiter {

  /** Gets the sampling and rate limiting configs. */
  private final ConfigResolver configResolver;
  /** The app's bucket ID for sampling, a number in [0.0, 1.0). */
  private final double samplingBucketId;

  private final double fragmentBucketId;

  private RateLimiterImpl traceLimiter = null;
  private RateLimiterImpl networkLimiter = null;

  /** Enable android logging or not */
  private boolean isLogcatEnabled = false;

  /**
   * Construct a token bucket rate limiter.
   *
   * @param appContext the application's context.
   * @param rate the Rate object representing the number of tokens generated per specified time unit
   * @param capacity token bucket capacity
   */
  public RateLimiter(@NonNull Context appContext, final Rate rate, final long capacity) {
    this(
        rate,
        capacity,
        new Clock(),
        getSamplingBucketId(),
        getSamplingBucketId(),
        ConfigResolver.getInstance());
    this.isLogcatEnabled = Utils.isDebugLoggingEnabled(appContext);
  }

  /** Generates a bucket id between [0.0f, 1.0f) for sampling, it is sticky across app lifecycle. */
  @VisibleForTesting
  static double getSamplingBucketId() {
    return new Random().nextDouble();
  }

  RateLimiter(
      final Rate rate,
      final long capacity,
      final Clock clock,
      double samplingBucketId,
      double fragmentBucketId,
      ConfigResolver configResolver) {
    Utils.checkArgument(
        0.0 <= samplingBucketId && samplingBucketId < 1.0,
        "Sampling bucket ID should be in range [0.0, 1.0).");
    Utils.checkArgument(
        0.0 <= fragmentBucketId && fragmentBucketId < 1.0,
        "Fragment sampling bucket ID should be in range [0.0, 1.0).");
    this.samplingBucketId = samplingBucketId;
    this.fragmentBucketId = fragmentBucketId;
    this.configResolver = configResolver;

    traceLimiter =
        new RateLimiterImpl(rate, capacity, clock, configResolver, TRACE, isLogcatEnabled);

    networkLimiter =
        new RateLimiterImpl(rate, capacity, clock, configResolver, NETWORK, isLogcatEnabled);
  }

  /** Returns whether device is allowed to send trace events based on trace sampling rate. */
  private boolean isDeviceAllowedToSendTraces() {
    double validTraceSamplingBucketIdThreshold = configResolver.getTraceSamplingRate();
    return samplingBucketId < validTraceSamplingBucketIdThreshold;
  }

  /** Returns whether device is allowed to send network events based on network sampling rate. */
  private boolean isDeviceAllowedToSendNetworkEvents() {
    double validNetworkSamplingBucketIdThreshold = configResolver.getNetworkRequestSamplingRate();
    return samplingBucketId < validNetworkSamplingBucketIdThreshold;
  }

  /**
   * Returns whether device is allowed to send Fragment screen trace events based on Fragment screen
   * trace sampling rate.
   */
  private boolean isDeviceAllowedToSendFragmentScreenTraces() {
    double validFragmentSamplingBucketIdThreshold = configResolver.getFragmentSamplingRate();
    return fragmentBucketId < validFragmentSamplingBucketIdThreshold;
  }

  /** Identifies if the {@link PerfMetric} is a Fragment screen trace */
  protected boolean isFragmentScreenTrace(PerfMetric metric) {
    return metric.hasTraceMetric()
        && metric.getTraceMetric().getName().startsWith(Constants.SCREEN_TRACE_PREFIX)
        && metric.getTraceMetric().containsCustomAttributes(Constants.ACTIVITY_ATTRIBUTE_KEY);
  }

  /**
   * Check if the {@link PerfMetric} should be rate limited.
   *
   * @param metric {@link PerfMetric} object.
   * @return true if event is rated limited, false if event is not rate limited.
   */
  boolean isEventRateLimited(PerfMetric metric) {
    if (!isRateLimitApplicable(metric)) {
      // Apply rate limiting on this metric.
      return false;
    }

    if (metric.hasNetworkRequestMetric()) {
      return !networkLimiter.check(metric);
    } else if (metric.hasTraceMetric()) {
      return !traceLimiter.check(metric);
    } else {
      // Should not reach here
      return true;
    }
  }

  /**
   * Check if the {@link PerfMetric} should be sampled. A {@link PerfMetric} is considered sampled
   * if the device isn't allowed to send the event type and it is not part of a verbose session.
   *
   * @param metric {@link PerfMetric} object.
   * @return true if allowed, false if not allowed.
   */
  boolean isEventSampled(PerfMetric metric) {
    if (metric.hasTraceMetric()
        && !(isDeviceAllowedToSendTraces()
            || hasVerboseSessions(metric.getTraceMetric().getPerfSessionsList()))) {
      return false;
    }

    if (isFragmentScreenTrace(metric)
        && !(isDeviceAllowedToSendFragmentScreenTraces()
            || hasVerboseSessions(metric.getTraceMetric().getPerfSessionsList()))) {
      return false;
    }

    if (metric.hasNetworkRequestMetric()
        && !(isDeviceAllowedToSendNetworkEvents()
            || hasVerboseSessions(metric.getNetworkRequestMetric().getPerfSessionsList()))) {
      return false;
    }
    return true;
  }

  /**
   * Tells us if the {@link PerfSession} array has any verbose sessions.
   *
   * @param perfSessions The array of {@link PerfSession} from a {@link
   *     com.google.firebase.perf.v1.TraceMetric} or {@link
   *     com.google.firebase.perf.v1.NetworkRequestMetric}
   * @return true if has a verbose session, false otherwise.
   * @implNote We're guaranteed that the sessions are sorted such that if there is verbose sessions,
   *     it is always at the first index, and so we only check the first session in the array.
   */
  private boolean hasVerboseSessions(List<PerfSession> perfSessions) {
    if (perfSessions.size() > 0 && perfSessions.get(0).getSessionVerbosityCount() > 0) {
      return perfSessions.get(0).getSessionVerbosity(0)
          == SessionVerbosity.GAUGES_AND_SYSTEM_EVENTS;
    } else {
      return false;
    }
  }

  /**
   * Check if rate limiting should be applied to a metric object.
   *
   * @param metric {@link PerfMetric} object.
   * @return true if applying rate limiting. false if not.
   */
  boolean isRateLimitApplicable(@NonNull PerfMetric metric) {
    if (metric.hasTraceMetric()
        && (metric
                .getTraceMetric()
                .getName()
                .equals(Constants.TraceNames.FOREGROUND_TRACE_NAME.toString())
            || metric
                .getTraceMetric()
                .getName()
                .equals(Constants.TraceNames.BACKGROUND_TRACE_NAME.toString()))
        && metric.getTraceMetric().getCountersCount() > 0) { // Background or foreground trace.
      return false;
    } else if (metric.hasGaugeMetric()) { // Gauge Metric.
      return false;
    }
    return true;
  }

  /** Change rate when app switch between foreground and background. */
  void changeRate(boolean isForeground) {
    traceLimiter.changeRate(isForeground);
    networkLimiter.changeRate(isForeground);
  }

  @VisibleForTesting
  boolean getIsDeviceAllowedToSendTraces() {
    return isDeviceAllowedToSendTraces();
  }

  @VisibleForTesting
  boolean getIsDeviceAllowedToSendNetworkEvents() {
    return isDeviceAllowedToSendNetworkEvents();
  }

  @VisibleForTesting
  boolean getIsDeviceAllowedToSendFragmentScreenTraces() {
    return isDeviceAllowedToSendFragmentScreenTraces();
  }

  /** The implementation of Token Bucket rate limiter. */
  static class RateLimiterImpl {

    private static final AndroidLogger logger = AndroidLogger.getInstance();
    private static final long MICROS_IN_A_SECOND = SECONDS.toMicros(1);

    private final Clock clock;
    private final boolean isLogcatEnabled;

    // Last time a token is consumed.
    private Timer lastTimeTokenReplenished;

    // The Rate object representing the number of tokens generated per specified time unit
    private Rate rate;
    // Token bucket capacity, also the initial number of tokens in the bucket.
    private long capacity;
    // Number of tokens in the bucket.
    private double tokenCount;

    private Rate foregroundRate;
    private Rate backgroundRate;

    private long foregroundCapacity;
    private long backgroundCapacity;

    RateLimiterImpl(
        final Rate rate,
        final long capacity,
        final Clock clock,
        ConfigResolver configResolver,
        final @ResourceType String type,
        boolean isLogcatEnabled) {
      this.clock = clock;
      this.capacity = capacity;
      this.rate = rate;
      tokenCount = capacity;
      lastTimeTokenReplenished = this.clock.getTime();
      setRateByReadingRemoteConfigValues(configResolver, type, isLogcatEnabled);
      this.isLogcatEnabled = isLogcatEnabled;
    }

    /**
     * Check if a event log can pass the Token Bucket rate limiter. Make it package private because
     * it is called only from TransportManager. Make it synchronized because TransportManager's
     * interfaces are called from multiple threads.
     *
     * @param metric a {@link PerfMetric} object
     * @return true if pass, false if fail.
     */
    synchronized boolean check(@NonNull PerfMetric metric) {
      Timer now = clock.getTime();
      double newTokens =
          (lastTimeTokenReplenished.getDurationMicros(now)
              * rate.getTokensPerSeconds()
              / MICROS_IN_A_SECOND);
      if (newTokens > 0.0) {
        tokenCount = Math.min(tokenCount + newTokens, capacity);
        lastTimeTokenReplenished = now;
      }
      if (tokenCount >= 1.0) {
        tokenCount -= 1.0;
        return true;
      }
      if (isLogcatEnabled) {
        logger.warn("Exceeded log rate limit, dropping the log.");
      }
      return false;
    }

    /**
     * Change rate when app switch between foreground and background.
     *
     * @param isForeground if true, apply foreground rate, otherwise apply background rate.
     */
    synchronized void changeRate(boolean isForeground) {
      rate = isForeground ? foregroundRate : backgroundRate;
      capacity = isForeground ? foregroundCapacity : backgroundCapacity;
    }

    /**
     * Set rate limit parameters from the parameter fetched from remote config and apply default
     * value if not present.
     *
     * @param configResolver The single source of truth for getting the rate limiting configuration.
     * @param type The resource type for calculating rate limiting configuration.
     */
    private void setRateByReadingRemoteConfigValues(
        ConfigResolver configResolver, final @ResourceType String type, boolean isLogcatEnabled) {

      // Calculates foreground rate limit.
      long fLimitTime = getFlimitSec(configResolver, type);
      long fLimitEvents = getFlimitEvents(configResolver, type);

      foregroundRate = new Rate(fLimitEvents, fLimitTime, SECONDS);
      foregroundCapacity = fLimitEvents;
      if (isLogcatEnabled) {
        logger.debug(
            "Foreground %s logging rate:%f, burst capacity:%d",
            type, foregroundRate, foregroundCapacity);
      }

      // Calculates background rate limit.
      long bLimitTime = getBlimitSec(configResolver, type);
      long bLimitEvents = getBlimitEvents(configResolver, type);

      backgroundRate = new Rate(bLimitEvents, bLimitTime, SECONDS);
      backgroundCapacity = bLimitEvents;
      if (isLogcatEnabled) {
        logger.debug(
            "Background %s logging rate:%f, capacity:%d", type, backgroundRate, backgroundCapacity);
      }
    }

    private static long getFlimitSec(
        ConfigResolver configResolver, final @ResourceType String type) {
      if (type == TRACE) {
        return configResolver.getRateLimitSec();
      }
      return configResolver.getRateLimitSec();
    }

    private static long getFlimitEvents(
        ConfigResolver configResolver, final @ResourceType String type) {
      if (type == TRACE) {
        return configResolver.getTraceEventCountForeground();
      }
      return configResolver.getNetworkEventCountForeground();
    }

    private static long getBlimitSec(
        ConfigResolver configResolver, final @ResourceType String type) {
      if (type == TRACE) {
        return configResolver.getRateLimitSec();
      }
      return configResolver.getRateLimitSec();
    }

    private static long getBlimitEvents(
        ConfigResolver configResolver, final @ResourceType String type) {
      if (type == TRACE) {
        return configResolver.getTraceEventCountBackground();
      }
      return configResolver.getNetworkEventCountBackground();
    }

    @VisibleForTesting
    Rate getForegroundRate() {
      return foregroundRate;
    }

    @VisibleForTesting
    long getForegroundCapacity() {
      return foregroundCapacity;
    }

    @VisibleForTesting
    Rate getBackgroundRate() {
      return backgroundRate;
    }

    @VisibleForTesting
    long getBackgroundCapacity() {
      return backgroundCapacity;
    }

    @VisibleForTesting
    Rate getRate() {
      return rate;
    }

    @VisibleForTesting
    void setRate(Rate newRate) {
      rate = newRate;
    }
  }
}
