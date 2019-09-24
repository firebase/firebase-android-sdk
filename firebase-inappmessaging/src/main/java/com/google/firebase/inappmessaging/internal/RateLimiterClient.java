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

package com.google.firebase.inappmessaging.internal;

import com.google.firebase.inappmessaging.internal.RateLimitProto.Counter;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.inappmessaging.model.RateLimit;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Client to store rate limits. This works as follows:
 *
 * <ul>
 *   <li>Limits are represented by value objects of type {@link RateLimit}.
 *   <li>Limits can be incremented using {@link #increment(RateLimit)} and checked using the {@link
 *       #isRateLimited(RateLimit)} methods.
 * </ul>
 *
 * @hide
 */
@Singleton
public class RateLimiterClient {
  private static final RateLimitProto.RateLimit EMPTY_RATE_LIMITS =
      RateLimitProto.RateLimit.getDefaultInstance();
  private final ProtoStorageClient storageClient;
  private final Clock clock;
  private Maybe<RateLimitProto.RateLimit> cachedRateLimts = Maybe.empty();

  @Inject
  RateLimiterClient(
      @com.google.firebase.inappmessaging.internal.injection.qualifiers.RateLimit
          ProtoStorageClient storageClient,
      Clock clock) {
    this.storageClient = storageClient;
    this.clock = clock;
  }

  private static Counter increment(Counter current) {
    return Counter.newBuilder(current).clearValue().setValue(current.getValue() + 1).build();
  }

  /**
   * Increment the value associated to the key by 1, initializing if necessary.
   *
   * <p>If the limit has expired, it is reinitialized.
   *
   * <p>Callers are thus expected to check if a limit is reached using {@link
   * #isRateLimited(RateLimit)} before incrementing.
   */
  public Completable increment(RateLimit limit) {
    return getRateLimits()
        .defaultIfEmpty(EMPTY_RATE_LIMITS)
        .flatMapCompletable(
            storedLimits ->
                Observable.just(storedLimits.getLimitsOrDefault(limit.limiterKey(), newCounter()))
                    .filter(counter -> !isLimitExpired(counter, limit))
                    .switchIfEmpty(Observable.just(newCounter()))
                    .map(
                        current -> {
                          Counter incremented = increment(current);
                          return RateLimitProto.RateLimit.newBuilder(storedLimits)
                              .putLimits(limit.limiterKey(), incremented)
                              .build();
                        })
                    .flatMapCompletable(
                        a -> storageClient.write(a).doOnComplete(() -> initInMemCache(a))));
  }

  /** True if the limit has been reached and has not expired. */
  public Single<Boolean> isRateLimited(RateLimit limit) {
    return getRateLimits()
        .switchIfEmpty(Maybe.just(RateLimitProto.RateLimit.getDefaultInstance()))
        .map(storedLimits -> storedLimits.getLimitsOrDefault(limit.limiterKey(), newCounter()))
        .filter(counter -> isLimitExpired(counter, limit) || counter.getValue() < limit.limit())
        .isEmpty();
  }

  private boolean isLimitExpired(Counter counter, RateLimit limit) {
    long currentTime = clock.now();
    return (currentTime - counter.getStartTimeEpoch()) > limit.timeToLiveMillis();
  }

  private Maybe<RateLimitProto.RateLimit> getRateLimits() {
    return cachedRateLimts
        .switchIfEmpty(
            storageClient.read(RateLimitProto.RateLimit.parser()).doOnSuccess(this::initInMemCache))
        .doOnError(ignored -> clearInMemCache());
  }

  private void initInMemCache(RateLimitProto.RateLimit rateLimits) {
    cachedRateLimts = Maybe.just(rateLimits);
  }

  private void clearInMemCache() {
    cachedRateLimts = Maybe.empty();
  }

  private Counter newCounter() {
    return Counter.newBuilder().setValue(0).setStartTimeEpoch(clock.now()).build();
  }
}
