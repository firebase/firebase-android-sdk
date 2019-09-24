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

import android.app.Application;
import com.google.firebase.inappmessaging.internal.injection.modules.ProtoStorageClientModule;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.CampaignCache;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import java.io.File;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Client to store and retrieve the latest version of eligible campaigns fetched from the fiam
 * service
 *
 * <p>Operations performed on the cache are thread safe but non atomic.
 *
 * @hide
 */
@ThreadSafe
@Singleton
public class CampaignCacheClient {
  private final ProtoStorageClient storageClient;
  private final Application application;
  private final Clock clock;
  @Nullable private FetchEligibleCampaignsResponse cachedResponse;

  @Inject
  CampaignCacheClient(
      @CampaignCache ProtoStorageClient storageClient, Application application, Clock clock) {
    this.storageClient = storageClient;
    this.application = application;
    this.clock = clock;
  }

  /**
   * Writes the provided {@link FetchEligibleCampaignsResponse} to file storage and caches it in
   * memory.
   *
   * @param fetchEligibleCampaignsResponse
   * @return
   */
  public Completable put(FetchEligibleCampaignsResponse fetchEligibleCampaignsResponse) {
    return storageClient
        .write(fetchEligibleCampaignsResponse)
        .doOnComplete(() -> cachedResponse = fetchEligibleCampaignsResponse);
  }

  /**
   * Gets the last cached campaign response
   *
   * <p>Returns {@link Maybe#empty()} if any of the following are true
   *
   * <ul>
   *   <li>If the storage client returns {@link Maybe#empty()}.
   *   <li>If the ttl on the cached proto is set and has expired.
   *   <li>If the ttl on the cached proto is not set and the proto file is older than 1 {@link
   *       TimeUnit#DAYS}.
   * </ul>
   *
   * @return
   */
  public Maybe<FetchEligibleCampaignsResponse> get() {
    Maybe<FetchEligibleCampaignsResponse> readFromCache = Maybe.fromCallable(() -> cachedResponse);
    Maybe<FetchEligibleCampaignsResponse> readFromStorage =
        storageClient
            .read(FetchEligibleCampaignsResponse.parser())
            .doOnSuccess(response -> cachedResponse = response);
    return readFromCache
        .switchIfEmpty(readFromStorage)
        .filter(this::isResponseValid)
        .doOnError(s -> cachedResponse = null);
  }

  private boolean isResponseValid(FetchEligibleCampaignsResponse response) {
    long expirationTimestamp = response.getExpirationEpochTimestampMillis();
    long currentTime = clock.now();

    File file =
        new File(
            application.getApplicationContext().getFilesDir(),
            ProtoStorageClientModule.CAMPAIGN_CACHE_FILE);

    if (expirationTimestamp != 0) {
      return currentTime < expirationTimestamp;
    }

    if (file.exists()) {
      return currentTime < (file.lastModified() + TimeUnit.DAYS.toMillis(1));
    }
    return true;
  }
}
