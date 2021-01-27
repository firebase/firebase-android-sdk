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

import static com.google.firebase.perf.internal.ResourceType.NETWORK;
import static com.google.firebase.perf.internal.ResourceType.TRACE;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.internal.ResourceType;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.Utils;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.PerfMetric;
import com.google.firebase.perf.v1.PerfSession;
import com.google.firebase.perf.v1.SessionVerbosity;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Implement the Token Bucket rate limiting algorithm. The token bucket initially holds "capacity"
 * number of tokens. when the request comes in, base on the last time token is issued, token is
 * replenished into the bucket at the "rate", and token is consumed by the request. It depends on
 * two parameters: Rate: the number of token generated per minute. Capacity: bucket capacity, the
 * initial number of tokens, also the max number of burst allowed.
 */
final class RateLimiter {

  /** The app's bucket ID for sampling, a number in [0.0f, 1.0f). */
  private final float samplingBucketId;

  /** Enable android logging or not */
  private boolean isLogcatEnabled = false;

  private RateLimiterImpl mTraceLimiter = null;
  private RateLimiterImpl mNetworkLimiter = null;

  /** Gets the sampling and rate limiting configs. */
  private final ConfigResolver configResolver;

  /**
   * Construct a token bucket rate limiter.
   *
   * @param context app context.
   * @param rate number of token generated per minute.
   * @param capacity token bucket capacity
   */
  public RateLimiter(@NonNull Context context, final double rate, final long capacity) {
    this(rate, capacity, new Clock(), getSamplingBucketId(), ConfigResolver.getInstance());
    this.isLogcatEnabled = Utils.isDebugLoggingEnabled(context);
  }

  /** Generates a bucket id between [0.0f, 1.0f) for sampling, it is sticky across app lifecycle. */
  @VisibleForTesting
  static float getSamplingBucketId() {
    return new Random().nextFloat();
  }

  RateLimiter(
      final double rate,
      final long capacity,
      final Clock clock,
      float samplingBucketId,
      ConfigResolver configResolver) {
    Utils.checkArgument(
        0.0f <= samplingBucketId && samplingBucketId < 1.0f,
        "Sampling bucket ID should be in range [0.0f, 1.0f).");
    this.samplingBucketId = samplingBucketId;
    this.configResolver = configResolver;

    mTraceLimiter =
        new RateLimiterImpl(rate, capacity, clock, configResolver, TRACE, isLogcatEnabled);

    mNetworkLimiter =
        new RateLimiterImpl(rate, capacity, clock, configResolver, NETWORK, isLogcatEnabled);
  }

  /** Returns whether device is allowed to send trace events based on trace sampling rate. */
  private boolean isDeviceAllowedToSendTraces() {
    float validTraceSamplingBucketIdThreshold = configResolver.getTraceSamplingRate();
    return samplingBucketId < validTraceSamplingBucketIdThreshold;
  }

  /** Returns whether device is allowed to send network events based on network sampling rate. */
  private boolean isDeviceAllowedToSendNetworkEvents() {
    float validNetworkSamplingBucketIdThreshold = configResolver.getNetworkRequestSamplingRate();
    return samplingBucketId < validNetworkSamplingBucketIdThreshold;
  }

  /**
   * Check if we should log the {@link PerfMetric} to transport.
   *
   * <p>Cases in which we don't log a {@link PerfMetric} to transport:
   *
   * <ul>
   *   <li>It is a {@link TraceMetric}, the {@link PerfSession} is not verbose and trace metrics are
   *       sampled.
   *   <li>It is a {@link NetworkRequestMetric}, the {@link PerfSession} is not verbose and network
   *       requests are sampled.
   *   <li>The number of metrics being sent exceeds what the rate limiter allows.
   * </ul>
   *
   * @param metric {@link PerfMetric} object.
   * @return true if allowed, false if not allowed.
   */
  boolean check(PerfMetric metric) {
    if (metric.hasTraceMetric()
        && !isDeviceAllowedToSendTraces()
        && !hasVerboseSessions(metric.getTraceMetric().getPerfSessionsList())) {
      return false;
    }

    if (metric.hasNetworkRequestMetric()
        && !isDeviceAllowedToSendNetworkEvents()
        && !hasVerboseSessions(metric.getNetworkRequestMetric().getPerfSessionsList())) {
      return false;
    }

    if (!isRateLimited(metric)) {
      // Do not apply rate limiting on this metric.
      return true;
    }

    if (metric.hasNetworkRequestMetric()) {
      return mNetworkLimiter.check(metric);
    } else if (metric.hasTraceMetric()) {
      return mTraceLimiter.check(metric);
    } else {
      return false;
    }
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
  boolean isRateLimited(@NonNull PerfMetric metric) {
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
    mTraceLimiter.changeRate(isForeground);
    mNetworkLimiter.changeRate(isForeground);
  }

  @VisibleForTesting
  boolean getIsDeviceAllowedToSendTraces() {
    return isDeviceAllowedToSendTraces();
  }

  @VisibleForTesting
  boolean getIsDeviceAllowedToSendNetworkEvents() {
    return isDeviceAllowedToSendNetworkEvents();
  }

  /** The implementation of Token Bucket rate limiter. */
  static class RateLimiterImpl {

    private static final AndroidLogger logger = AndroidLogger.getInstance();

    private static final long MICROS_IN_A_SECOND = TimeUnit.SECONDS.toMicros(1);

    // Token bucket capacity, also the initial number of tokens in the bucket.
    private long mCapacity;
    // Number of new tokens generated per second.
    private double mRate;
    // Last time a token is consumed.
    private Timer mLastTimeTokenConsumed;
    // Number of tokens in the bucket.
    private long mTokenCount;
    private final Clock mClock;

    private double mForegroundRate;
    private long mForegroundCapacity;
    private double mBackgroundRate;
    private long mBackgroundCapacity;
    private final boolean isLogcatEnabled;

    RateLimiterImpl(
        final double rate,
        final long capacity,
        final Clock clock,
        ConfigResolver configResolver,
        final @ResourceType String type,
        boolean isLogcatEnabled) {
      mClock = clock;
      mCapacity = capacity;
      mRate = rate;
      mTokenCount = capacity;
      mLastTimeTokenConsumed = mClock.getTime();
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
      Timer now = mClock.getTime();
      long newTokens =
          Math.max(
              0,
              (long) (mLastTimeTokenConsumed.getDurationMicros(now) * mRate / MICROS_IN_A_SECOND));
      mTokenCount = Math.min(mTokenCount + newTokens, mCapacity);
      if (mTokenCount > 0) {
        mTokenCount--;
        mLastTimeTokenConsumed = now;
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
      mRate = isForeground ? mForegroundRate : mBackgroundRate;
      mCapacity = isForeground ? mForegroundCapacity : mBackgroundCapacity;
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

      mForegroundRate = ((double) fLimitEvents) / fLimitTime;
      mForegroundCapacity = fLimitEvents;
      if (isLogcatEnabled) {
        logger.debug(
            "Foreground %s logging rate:%f, burst capacity:%d",
            type, mForegroundRate, mForegroundCapacity);
      }

      // Calculates background rate limit.
      long bLimitTime = getBlimitSec(configResolver, type);
      long bLimitEvents = getBlimitEvents(configResolver, type);

      mBackgroundRate = ((double) bLimitEvents) / bLimitTime;
      mBackgroundCapacity = bLimitEvents;
      if (isLogcatEnabled) {
        logger.debug(
            "Background %s logging rate:%f, capacity:%d",
            type, mBackgroundRate, mBackgroundCapacity);
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
    double getForegroundRate() {
      return mForegroundRate;
    }

    @VisibleForTesting
    long getForegroundCapacity() {
      return mForegroundCapacity;
    }

    @VisibleForTesting
    double getBackgroundRate() {
      return mBackgroundRate;
    }

    @VisibleForTesting
    long getBackgroundCapacity() {
      return mBackgroundCapacity;
    }

    @VisibleForTesting
    double getRate() {
      return mRate;
    }

    @VisibleForTesting
    void setRate(double newRate) {
      mRate = newRate;
    }
  }
}
