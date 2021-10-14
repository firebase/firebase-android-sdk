package com.google.firebase.appcheck.internal;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.appcheck.internal.util.Clock;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Class to manage when an App Check token exchange can be retried after a failure. */
public class RetryManager {

  private static final long MAX_EXPONENTIAL_BACKOFF_MILLIS = 4 * 60 * 60 * 1000; // 4 hours
  private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000; // 24 hours
  private static final long ONE_SECOND_MILLIS = 1000;
  private static final long UNSET_RETRY_TIME = -1;

  private final Clock clock;

  private long currentRetryCount = 0;
  private long nextRetryTimeMillis = UNSET_RETRY_TIME;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({EXPONENTIAL, ONE_DAY})
  private @interface BackoffStrategyType {}

  private static final int EXPONENTIAL = 0;
  private static final int ONE_DAY = 1;

  public RetryManager() {
    this.clock = new Clock.DefaultClock();
  }

  @VisibleForTesting
  RetryManager(Clock clock) {
    this.clock = clock;
  }

  public boolean canRetry() {
    return nextRetryTimeMillis <= clock.currentTimeMillis();
  }

  public long getNextRetryTimeMillis() {
    return nextRetryTimeMillis;
  }

  public void resetBackoffOnSuccess() {
    currentRetryCount = 0;
    nextRetryTimeMillis = UNSET_RETRY_TIME;
  }

  public void updateBackoffOnFailure(int errorCode) {
    currentRetryCount++;
    if (getBackoffStrategyByErrorCode(errorCode) == ONE_DAY) {
      nextRetryTimeMillis = clock.currentTimeMillis() + ONE_DAY_MILLIS;
      return;
    }
    long exponentialBackoffMillis =
        (long) (Math.pow(2, currentRetryCount * (1 + Math.random() * 0.5)) * ONE_SECOND_MILLIS);
    nextRetryTimeMillis =
        clock.currentTimeMillis()
            + Math.min(exponentialBackoffMillis, MAX_EXPONENTIAL_BACKOFF_MILLIS);
  }

  @BackoffStrategyType
  private static int getBackoffStrategyByErrorCode(int errorCode) {
    if (errorCode == 400 || errorCode == 404) {
      return ONE_DAY;
    }
    return EXPONENTIAL;
  }
}
