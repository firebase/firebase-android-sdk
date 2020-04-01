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

import com.google.firebase.inappmessaging.internal.injection.qualifiers.ImpressionStore;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpression;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpressionList;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.HashSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Class to store and retrieve in app message impressions
 *
 * @hide
 */
@Singleton
public class ImpressionStorageClient {
  private static final CampaignImpressionList EMPTY_IMPRESSIONS =
      CampaignImpressionList.getDefaultInstance();
  private final ProtoStorageClient storageClient;
  private Maybe<CampaignImpressionList> cachedImpressionsMaybe = Maybe.empty();

  @Inject
  ImpressionStorageClient(@ImpressionStore ProtoStorageClient storageClient) {
    this.storageClient = storageClient;
  }

  private static CampaignImpressionList appendImpression(
      CampaignImpressionList campaignImpressions, CampaignImpression impression) {
    return CampaignImpressionList.newBuilder(campaignImpressions)
        .addAlreadySeenCampaigns(impression)
        .build();
  }

  /** Stores the provided {@link CampaignImpression} to file storage */
  public Completable storeImpression(CampaignImpression impression) {
    return getAllImpressions()
        .defaultIfEmpty(EMPTY_IMPRESSIONS)
        .flatMapCompletable(
            storedImpressions -> {
              CampaignImpressionList appendedImpressions =
                  appendImpression(storedImpressions, impression);
              return storageClient
                  .write(appendedImpressions)
                  .doOnComplete(() -> initInMemCache(appendedImpressions));
            });
  }

  /**
   * Returns the list of impressed campaigns
   *
   * <p>Returns {@link Maybe#empty()} if no campaigns have ever been impressed or if the storage was
   * corrupt.
   */
  public Maybe<CampaignImpressionList> getAllImpressions() {
    return cachedImpressionsMaybe
        .switchIfEmpty(
            storageClient.read(CampaignImpressionList.parser()).doOnSuccess(this::initInMemCache))
        .doOnError(ignored -> clearInMemCache());
  }

  private void initInMemCache(CampaignImpressionList campaignImpressions) {
    cachedImpressionsMaybe = Maybe.just(campaignImpressions);
  }

  private void clearInMemCache() {
    cachedImpressionsMaybe = Maybe.empty();
  }

  /** Returns {@code Single.just(true)} if the campaign has been impressed */
  public Single<Boolean> isImpressed(CampaignProto.ThickContent content) {
    String campaignId =
        content.getPayloadCase().equals(CampaignProto.ThickContent.PayloadCase.VANILLA_PAYLOAD)
            ? content.getVanillaPayload().getCampaignId()
            : content.getExperimentalPayload().getCampaignId();
    return getAllImpressions()
        .map(CampaignImpressionList::getAlreadySeenCampaignsList)
        .flatMapObservable(Observable::fromIterable)
        .map(CampaignImpression::getCampaignId)
        .contains(campaignId);
  }

  /**
   * Clears impressions for all campaigns found in the provided {@link
   * FetchEligibleCampaignsResponse} This is done because we trust the server to deliver campaigns
   * which should be shown again for scheduled campaigns.
   */
  public Completable clearImpressions(FetchEligibleCampaignsResponse response) {
    HashSet<String> idsToClear = new HashSet<>();
    for (CampaignProto.ThickContent content : response.getMessagesList()) {
      String id =
          content.getPayloadCase().equals(CampaignProto.ThickContent.PayloadCase.VANILLA_PAYLOAD)
              ? content.getVanillaPayload().getCampaignId()
              : content.getExperimentalPayload().getCampaignId();
      idsToClear.add(id);
    }
    Logging.logd("Potential impressions to clear: " + idsToClear.toString());
    return getAllImpressions()
        .defaultIfEmpty(EMPTY_IMPRESSIONS)
        .flatMapCompletable(
            (storedImpressions) -> {
              Logging.logd("Existing impressions: " + storedImpressions.toString());
              CampaignImpressionList.Builder clearedImpressionListBuilder =
                  CampaignImpressionList.newBuilder();
              for (CampaignImpression storedImpression :
                  storedImpressions.getAlreadySeenCampaignsList()) {
                if (!idsToClear.contains(storedImpression.getCampaignId())) {
                  clearedImpressionListBuilder.addAlreadySeenCampaigns(storedImpression);
                }
              }
              CampaignImpressionList clearedImpressionList = clearedImpressionListBuilder.build();
              Logging.logd("New cleared impression list: " + clearedImpressionList.toString());
              return storageClient
                  .write(clearedImpressionList)
                  .doOnComplete(() -> initInMemCache(clearedImpressionList));
            });
  }
}
