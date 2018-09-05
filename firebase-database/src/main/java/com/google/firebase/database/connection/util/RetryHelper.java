// Copyright 2018 Google LLC
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

package com.google.firebase.database.connection.util;

import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RetryHelper {
  private final ScheduledExecutorService executorService;
  private final LogWrapper logger;
  /** The minimum delay for a retry in ms */
  private final long minRetryDelayAfterFailure;
  /** The maximum retry delay in ms */
  private final long maxRetryDelay;
  /**
   * The range of the delay that will be used at random 0 => no randomness 0.5 => at least half the
   * current delay 1 => any delay between [min, max)
   */
  private final double jitterFactor;
  /** The backoff exponent */
  private final double retryExponent;

  private final Random random = new Random();

  private ScheduledFuture<?> scheduledRetry;

  private long currentRetryDelay;
  private boolean lastWasSuccess = true;

  private RetryHelper(
      ScheduledExecutorService executorService,
      LogWrapper logger,
      long minRetryDelayAfterFailure,
      long maxRetryDelay,
      double retryExponent,
      double jitterFactor) {
    this.executorService = executorService;
    this.logger = logger;
    this.minRetryDelayAfterFailure = minRetryDelayAfterFailure;
    this.maxRetryDelay = maxRetryDelay;
    this.retryExponent = retryExponent;
    this.jitterFactor = jitterFactor;
  }

  public void retry(final Runnable runnable) {
    Runnable wrapped =
        new Runnable() {
          @Override
          public void run() {
            scheduledRetry = null;
            runnable.run();
          }
        };
    long delay;
    if (this.scheduledRetry != null) {
      logger.debug("Cancelling previous scheduled retry");
      this.scheduledRetry.cancel(false);
      this.scheduledRetry = null;
    }
    if (this.lastWasSuccess) {
      delay = 0;
    } else {
      if (this.currentRetryDelay == 0) {
        this.currentRetryDelay = this.minRetryDelayAfterFailure;
      } else {
        long newDelay = (long) (this.currentRetryDelay * this.retryExponent);
        this.currentRetryDelay = Math.min(newDelay, this.maxRetryDelay);
      }
      delay =
          (long)
              (((1 - jitterFactor) * this.currentRetryDelay)
                  + (jitterFactor * currentRetryDelay * random.nextDouble()));
    }
    this.lastWasSuccess = false;
    logger.debug("Scheduling retry in %dms", delay);
    this.scheduledRetry = this.executorService.schedule(wrapped, delay, TimeUnit.MILLISECONDS);
  }

  public void signalSuccess() {
    this.lastWasSuccess = true;
    this.currentRetryDelay = 0;
  }

  public void setMaxDelay() {
    this.currentRetryDelay = this.maxRetryDelay;
  }

  public void cancel() {
    if (this.scheduledRetry != null) {
      logger.debug("Cancelling existing retry attempt");
      this.scheduledRetry.cancel(false);
      this.scheduledRetry = null;
    } else {
      logger.debug("No existing retry attempt to cancel");
    }
    this.currentRetryDelay = 0;
  }

  public static class Builder {
    private final ScheduledExecutorService service;
    private long minRetryDelayAfterFailure = 1000;
    private double jitterFactor = 0.5;
    private long retryMaxDelay = 30 * 1000;
    private double retryExponent = 1.3;
    private final LogWrapper logger;

    public Builder(ScheduledExecutorService service, Logger logger, String tag) {
      this.service = service;
      this.logger = new LogWrapper(logger, tag);
    }

    public Builder withMinDelayAfterFailure(long delay) {
      this.minRetryDelayAfterFailure = delay;
      return this;
    }

    public Builder withMaxDelay(long delay) {
      this.retryMaxDelay = delay;
      return this;
    }

    public Builder withRetryExponent(double exponent) {
      this.retryExponent = exponent;
      return this;
    }

    public Builder withJitterFactor(double random) {
      if (random < 0 || random > 1) {
        throw new IllegalArgumentException("Argument out of range: " + random);
      }
      this.jitterFactor = random;
      return this;
    }

    public RetryHelper build() {
      return new RetryHelper(
          this.service,
          this.logger,
          this.minRetryDelayAfterFailure,
          this.retryMaxDelay,
          this.retryExponent,
          this.jitterFactor);
    }
  }
}
