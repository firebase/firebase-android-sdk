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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.inappmessaging.internal.RateLimitProto.Counter;
import com.google.firebase.inappmessaging.internal.time.FakeClock;
import com.google.firebase.inappmessaging.model.RateLimit;
import com.google.protobuf.Parser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RateLimiterClientTest {
  private static final long PAST = 10;
  private static final long NOW = 100;
  private static final long LIMIT_TWO = 2L;
  private static final String LIMITER_KEY = "LIMITER_KEY";
  private static final long TTL = 100000;
  private static final Counter counter =
      Counter.newBuilder().setValue(1).setStartTimeEpoch(PAST).build();

  private static final RateLimit rateLimit =
      RateLimit.builder()
          .setLimit(LIMIT_TWO)
          .setLimiterKey(LIMITER_KEY)
          .setTimeToLiveMillis(TTL)
          .build();

  private static Map<String, Counter> limitsMap;
  private static RateLimitProto.RateLimit storedRateLimit;

  private RateLimiterClient rateLimiterClient;
  private Completable fakeWrite;
  private boolean wasWritten;
  private Maybe<RateLimitProto.RateLimit> fakeRead;

  @Mock private ProtoStorageClient storageClient;

  @Before
  public void setup() throws IOException {
    initMocks(this);

    limitsMap = new HashMap<>();
    limitsMap.put(LIMITER_KEY, counter);
    storedRateLimit = RateLimitProto.RateLimit.newBuilder().putAllLimits(limitsMap).build();

    fakeRead = Maybe.fromCallable(() -> storedRateLimit);
    fakeWrite =
        Completable.fromCallable(
            () -> {
              wasWritten = true;
              return null;
            });

    rateLimiterClient = new RateLimiterClient(storageClient, new FakeClock(NOW));

    when(storageClient.read(any(RateLimitParser.class))).thenReturn(fakeRead);
    when(storageClient.write(any(RateLimitProto.RateLimit.class))).thenReturn(fakeWrite);
  }

  @Test
  public void increment_noErrors_writesToStorage() {
    rateLimiterClient.increment(rateLimit).subscribe();

    assertThat(wasWritten).isTrue();
  }

  @Test
  public void increment_noExistingCounter_writesToStorage() {
    when(storageClient.read(any(RateLimitParser.class))).thenReturn(Maybe.empty());

    rateLimiterClient.increment(rateLimit).subscribe();

    assertThat(wasWritten).isTrue();
  }

  @Test
  public void increment_noErrors_incrementsCounter() {
    ArgumentCaptor<RateLimitProto.RateLimit> rateLimitCaptor =
        ArgumentCaptor.forClass(RateLimitProto.RateLimit.class);

    rateLimiterClient.increment(rateLimit).subscribe();
    verify(storageClient).write(rateLimitCaptor.capture());

    assertThat(rateLimitCaptor.getValue().getLimitsOrThrow(LIMITER_KEY).getValue()).isEqualTo(2);
  }

  @Test
  public void increment_noExistingLimits_initializesLimits() {
    when(storageClient.read(any(RateLimitParser.class))).thenReturn(Maybe.empty());
    ArgumentCaptor<RateLimitProto.RateLimit> rateLimitCaptor =
        ArgumentCaptor.forClass(RateLimitProto.RateLimit.class);

    rateLimiterClient.increment(rateLimit).subscribe();
    verify(storageClient).write(rateLimitCaptor.capture());

    assertThat(rateLimitCaptor.getValue().getLimitsOrThrow(LIMITER_KEY).getValue()).isEqualTo(1);
  }

  @Test
  public void increment_expiredLimit_resetsLimit() {
    RateLimit noTTLLimit =
        RateLimit.builder()
            .setLimit(LIMIT_TWO)
            .setLimiterKey(LIMITER_KEY)
            .setTimeToLiveMillis(1000L)
            .build();
    ArgumentCaptor<RateLimitProto.RateLimit> rateLimitCaptor =
        ArgumentCaptor.forClass(RateLimitProto.RateLimit.class);
    rateLimiterClient = new RateLimiterClient(storageClient, new FakeClock(NOW + 1001));

    rateLimiterClient.increment(noTTLLimit).subscribe();
    verify(storageClient).write(rateLimitCaptor.capture());

    assertThat(rateLimitCaptor.getValue().getLimitsOrThrow(LIMITER_KEY).getValue()).isEqualTo(1);
    assertThat(rateLimitCaptor.getValue().getLimitsOrThrow(LIMITER_KEY).getStartTimeEpoch())
        .isEqualTo(NOW + 1001);
  }

  @Test
  public void increment_noCounterForLimit_initializesCounter() {
    RateLimit otherLimit =
        RateLimit.builder()
            .setLimit(LIMIT_TWO)
            .setLimiterKey("OTHER_KEY")
            .setTimeToLiveMillis(TTL)
            .build();
    ArgumentCaptor<RateLimitProto.RateLimit> rateLimitCaptor =
        ArgumentCaptor.forClass(RateLimitProto.RateLimit.class);

    rateLimiterClient.increment(otherLimit).subscribe();
    verify(storageClient).write(rateLimitCaptor.capture());

    assertThat(rateLimitCaptor.getValue().getLimitsOrThrow(LIMITER_KEY).getValue()).isEqualTo(1);
    assertThat(rateLimitCaptor.getValue().getLimitsOrThrow("OTHER_KEY").getValue()).isEqualTo(1);
  }

  @Test
  public void increment_noErrors_cachesInMemory() {
    rateLimiterClient.increment(rateLimit).subscribe();
    when(storageClient.read(any(RateLimitParser.class))).thenReturn(Maybe.empty());

    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(rateLimit).test();

    assertThat(testObserver.getEvents().get(0)).containsExactly(true);
  }

  @Test
  public void increment_writeErrors_doesNotSetInMemoryCache() {
    RateLimit otherLimit =
        RateLimit.builder().setLimit(2).setLimiterKey("OTHER_KEY").setTimeToLiveMillis(TTL).build();
    when(storageClient.write(any(RateLimitProto.RateLimit.class)))
        .thenReturn(Completable.error(new IOException()));
    when(storageClient.read(any(RateLimitParser.class))).thenReturn(Maybe.empty());
    rateLimiterClient.increment(otherLimit).subscribe();
    rateLimiterClient.increment(otherLimit).subscribe();
    rateLimiterClient.increment(otherLimit).subscribe();

    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(otherLimit).test();

    assertThat(testObserver.getEvents().get(0)).containsExactly(false);
  }

  @Test
  public void increment_writeErrors_notifiesError() {
    when(storageClient.write(any(RateLimitProto.RateLimit.class)))
        .thenReturn(Completable.error(new IOException()));

    TestObserver<Void> testObserver = rateLimiterClient.increment(rateLimit).test();

    testObserver.assertError(IOException.class);
  }

  @Test
  public void increment_readErrors_notifiesError() {
    when(storageClient.read(any(RateLimitParser.class))).thenReturn(Maybe.error(new IOException()));

    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(rateLimit).test();

    testObserver.assertError(IOException.class);
  }

  @Test
  public void isRateLimited_ifRateLimited_isTrue() {
    rateLimiterClient.increment(rateLimit).subscribe();

    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(rateLimit).test();

    assertThat(testObserver.getEvents().get(0)).containsExactly(true);
  }

  @Test
  public void isRateLimited_ifNotRateLimited_isFalse() {
    rateLimiterClient.increment(rateLimit).subscribe();

    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(rateLimit).test();

    assertThat(testObserver.getEvents().get(0)).containsExactly(true);
  }

  @Test
  public void isRateLimited_ifExpired_isFalse() {
    RateLimit noTTLLimit =
        RateLimit.builder()
            .setLimit(LIMIT_TWO)
            .setLimiterKey(LIMITER_KEY)
            .setTimeToLiveMillis(0L)
            .build();

    // check with no ttl limit
    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(noTTLLimit).test();

    assertThat(testObserver.getEvents().get(0)).containsExactly(false);
  }

  @Test
  public void isRateLimited_ifNoLimits_isFalse() {
    when(storageClient.read(any(RateLimitParser.class))).thenReturn(Maybe.empty());
    RateLimit oneLimit =
        RateLimit.builder().setLimit(1).setLimiterKey(LIMITER_KEY).setTimeToLiveMillis(TTL).build();

    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(oneLimit).test();

    assertThat(testObserver.getEvents().get(0)).containsExactly(false);
  }

  @Test
  public void isRateLimited_readError_notifiesError() {
    when(storageClient.read(any(RateLimitParser.class))).thenReturn(Maybe.error(new IOException()));

    TestObserver<Boolean> testObserver = rateLimiterClient.isRateLimited(rateLimit).test();

    testObserver.assertError(IOException.class);
  }

  interface RateLimitParser extends Parser<RateLimitProto.RateLimit> {}
}
