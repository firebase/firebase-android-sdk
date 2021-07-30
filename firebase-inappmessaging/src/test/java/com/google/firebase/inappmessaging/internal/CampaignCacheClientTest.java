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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.inappmessaging.internal.time.FakeClock;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import com.google.protobuf.Parser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CampaignCacheClientTest {
  private static final long PAST = 10;
  private static final long NOW = 100;
  private static final long FUTURE = 1000;

  private final FetchEligibleCampaignsResponse fetchEligibleCampaignsResponse2 =
      FetchEligibleCampaignsResponse.newBuilder().setExpirationEpochTimestampMillis(FUTURE).build();

  private final FetchEligibleCampaignsResponse fetchEligibleCampaignsResponse1 =
      FetchEligibleCampaignsResponse.newBuilder().setExpirationEpochTimestampMillis(FUTURE).build();

  private final FetchEligibleCampaignsResponse expiredCampaignResponse =
      FetchEligibleCampaignsResponse.newBuilder().setExpirationEpochTimestampMillis(PAST).build();

  CampaignCacheClient campaignCacheClient;
  TestObserver<FetchEligibleCampaignsResponse> storageWriteObserver;
  Maybe<FetchEligibleCampaignsResponse> fakeRead;
  Completable fakeWrite;
  Boolean wasWritten;
  @Mock private ProtoStorageClient storageClient;

  private static List<Object> getPlainValues(
      TestSubscriber<FetchEligibleCampaignsResponse> subscriber) {
    return subscriber.getEvents().get(0);
  }

  @Before
  public void setup() throws IOException {
    initMocks(this);
    wasWritten = false;

    campaignCacheClient =
        new CampaignCacheClient(
            storageClient,
            (Application) ApplicationProvider.getApplicationContext(),
            new FakeClock(NOW));

    storageWriteObserver = TestObserver.create();
    fakeRead = Maybe.fromCallable(() -> fetchEligibleCampaignsResponse1);
    fakeWrite = Completable.fromCallable(() -> wasWritten = true);
  }

  @Test
  public void put_noErrors_writesToStorage() {
    when(storageClient.write(fetchEligibleCampaignsResponse2)).thenReturn(fakeWrite);

    campaignCacheClient.put(fetchEligibleCampaignsResponse2).subscribe();

    assertThat(wasWritten).isTrue();
  }

  @Test
  public void put_noErrors_cachesInMemory() {
    when(storageClient.write(fetchEligibleCampaignsResponse2)).thenReturn(fakeWrite);
    when(storageClient.read(any(CampaignResponseParser.class)))
        .thenReturn(Maybe.just(fetchEligibleCampaignsResponse1));

    campaignCacheClient.put(fetchEligibleCampaignsResponse2).subscribe();
    TestSubscriber<FetchEligibleCampaignsResponse> subscriber =
        campaignCacheClient.get().toFlowable().test();

    assertThat(getPlainValues(subscriber)).containsExactly(fetchEligibleCampaignsResponse2);
  }

  @Test
  public void put_writeErrors_notifiesError() {
    when(storageClient.write(fetchEligibleCampaignsResponse2))
        .thenReturn(Completable.error(new IOException()));

    TestSubscriber<Object> subscriber =
        campaignCacheClient.put(fetchEligibleCampaignsResponse2).toFlowable().test();

    subscriber.assertError(IOException.class);
  }

  @Test
  public void put_writeErrors_doesNotSetInMemoryCache() {
    when(storageClient.write(fetchEligibleCampaignsResponse1))
        .thenReturn(Completable.error(new IOException()));
    when(storageClient.read(FetchEligibleCampaignsResponse.parser())).thenReturn(fakeRead);

    campaignCacheClient.put(fetchEligibleCampaignsResponse1).subscribe();
    TestSubscriber<FetchEligibleCampaignsResponse> subscriber =
        campaignCacheClient.get().toFlowable().test();

    assertThat(getPlainValues(subscriber)).containsExactly(fetchEligibleCampaignsResponse1);
  }

  @Test
  public void get_noInMemoryCache_fetchesFromStorage() {
    when(storageClient.read(FetchEligibleCampaignsResponse.parser()))
        .thenReturn(Maybe.just(fetchEligibleCampaignsResponse2));

    TestSubscriber<FetchEligibleCampaignsResponse> subscriber =
        campaignCacheClient.get().toFlowable().test();

    assertThat(getPlainValues(subscriber)).containsExactly(fetchEligibleCampaignsResponse2);
  }

  @Test
  public void get_withInMemoryCache_returnInMemValue() {
    when(storageClient.write(fetchEligibleCampaignsResponse2)).thenReturn(fakeWrite);
    when(storageClient.read(FetchEligibleCampaignsResponse.parser())).thenReturn(fakeRead);

    campaignCacheClient.put(fetchEligibleCampaignsResponse2).subscribe();
    TestSubscriber<FetchEligibleCampaignsResponse> subscriber =
        campaignCacheClient.get().toFlowable().test();

    assertThat(getPlainValues(subscriber)).containsExactly(fetchEligibleCampaignsResponse2);
  }

  @Test
  public void get_whenInMemCacheExpired_isEmpty() {
    when(storageClient.write(expiredCampaignResponse)).thenReturn(fakeWrite);
    when(storageClient.read(FetchEligibleCampaignsResponse.parser())).thenReturn(fakeRead);

    campaignCacheClient.put(expiredCampaignResponse).subscribe();
    TestSubscriber<FetchEligibleCampaignsResponse> subscriber =
        campaignCacheClient.get().toFlowable().test();

    subscriber.assertNoValues();
  }

  @Test
  public void get_whenStorageCacheExpired_isEmpty() {
    when(storageClient.read(FetchEligibleCampaignsResponse.parser()))
        .thenReturn(Maybe.just(expiredCampaignResponse));

    TestSubscriber<FetchEligibleCampaignsResponse> subscriber =
        campaignCacheClient.get().toFlowable().test();

    subscriber.assertNoValues();
  }

  @Test
  public void get_whenBothCachesAreEmpty_isEmpty() {
    when(storageClient.read(FetchEligibleCampaignsResponse.parser()))
        .thenReturn(Maybe.error(new FileNotFoundException()));

    TestSubscriber<FetchEligibleCampaignsResponse> subscriber =
        campaignCacheClient.get().toFlowable().test();

    subscriber.assertNoValues();
  }

  interface CampaignResponseParser extends Parser<FetchEligibleCampaignsResponse> {}
}
