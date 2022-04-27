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
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_ID_STRING;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpression;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpressionList;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import com.google.protobuf.Parser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.subscribers.TestSubscriber;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ImpressionStorageClientTest {
  private static final ThickContent vanillaCampaign =
      ThickContent.newBuilder()
          .setVanillaPayload(
              CampaignProto.VanillaCampaignPayload.newBuilder().setCampaignId(CAMPAIGN_ID_STRING))
          .build();
  private static final ThickContent experimentalCampaign =
      ThickContent.newBuilder()
          .setExperimentalPayload(
              CampaignProto.ExperimentalCampaignPayload.newBuilder()
                  .setCampaignId(CAMPAIGN_ID_STRING + "2"))
          .build();

  private static final CampaignImpression campaignImpression =
      CampaignImpression.newBuilder()
          .setCampaignId(vanillaCampaign.getVanillaPayload().getCampaignId())
          .build();
  private static final CampaignImpressionList campaignImpressionList =
      CampaignImpressionList.newBuilder().addAlreadySeenCampaigns(campaignImpression).build();

  private static final CampaignImpression experimentImpression =
      CampaignImpression.newBuilder()
          .setCampaignId(experimentalCampaign.getExperimentalPayload().getCampaignId())
          .build();
  private static final CampaignImpressionList experimentImpressionList =
      CampaignImpressionList.newBuilder().addAlreadySeenCampaigns(experimentImpression).build();

  @Mock private ProtoStorageClient storageClient;
  private ImpressionStorageClient impressionStorageClient;
  private Completable fakeWrite;
  private boolean wasWritten;
  private Maybe<CampaignImpressionList> fakeRead;

  private static List<Object> getPlainValues(TestSubscriber<CampaignImpressionList> subscriber) {
    return subscriber.getEvents().get(0);
  }

  @Before
  public void setup() throws IOException {
    initMocks(this);
    impressionStorageClient = new ImpressionStorageClient(storageClient);
    fakeRead = Maybe.fromCallable(() -> campaignImpressionList);
    wasWritten = false;
    fakeWrite =
        Completable.fromCallable(
            () -> {
              wasWritten = true;
              return null;
            });

    when(storageClient.read(any(CampaignImpressionsParser.class))).thenReturn(fakeRead);
    when(storageClient.write(any(CampaignImpressionList.class))).thenReturn(fakeWrite);
  }

  @Test
  public void storeImpression_noErrors_writesToStorage() {
    impressionStorageClient.storeImpression(campaignImpression).subscribe();

    assertThat(wasWritten).isTrue();
  }

  @Test
  public void storeImpression_noExistingImpressions_writesToStorage() {
    when(storageClient.read(any(CampaignImpressionsParser.class))).thenReturn(Maybe.empty());

    impressionStorageClient.storeImpression(campaignImpression).subscribe();

    assertThat(wasWritten).isTrue();
  }

  @Test
  public void storeImpression_noErrors_storesAppendedCampaigns() {
    when(storageClient.read(any(CampaignImpressionsParser.class)))
        .thenReturn(
            Maybe.just(
                CampaignImpressionList.newBuilder()
                    .addAlreadySeenCampaigns(campaignImpression)
                    .build()));
    ArgumentCaptor<CampaignImpressionList> campaignImpressionListArgumentCaptor =
        ArgumentCaptor.forClass(CampaignImpressionList.class);
    impressionStorageClient.storeImpression(campaignImpression).subscribe();
    verify(storageClient).write(campaignImpressionListArgumentCaptor.capture());

    assertThat(campaignImpressionListArgumentCaptor.getValue().getAlreadySeenCampaignsList())
        .containsExactly(campaignImpression, campaignImpression);
  }

  @Test
  public void storeImpression_noExistingCampaigns_storesAppendedCampaigns() {
    when(storageClient.read(any(CampaignImpressionsParser.class))).thenReturn(Maybe.empty());
    ArgumentCaptor<CampaignImpressionList> campaignImpressionListArgumentCaptor =
        ArgumentCaptor.forClass(CampaignImpressionList.class);
    impressionStorageClient.storeImpression(campaignImpression).subscribe();
    verify(storageClient).write(campaignImpressionListArgumentCaptor.capture());

    assertThat(campaignImpressionListArgumentCaptor.getValue().getAlreadySeenCampaignsList())
        .containsExactly(campaignImpression);
  }

  @Test
  public void storeImpression_noErrors_cachesInMemory() {
    CampaignImpressionList otherCampaignImpressionList =
        CampaignImpressionList.getDefaultInstance();
    when(storageClient.read(any(CampaignImpressionsParser.class))).thenReturn(Maybe.empty());
    impressionStorageClient.storeImpression(campaignImpression).subscribe();
    when(storageClient.read(any(CampaignImpressionsParser.class)))
        .thenReturn(Maybe.just(otherCampaignImpressionList));

    TestSubscriber<CampaignImpressionList> subscriber =
        impressionStorageClient.getAllImpressions().toFlowable().test();

    assertThat(
            ((CampaignImpressionList) getPlainValues(subscriber).get(0))
                .getAlreadySeenCampaignsList())
        .containsExactly(campaignImpression);
  }

  @Test
  public void storeImpression_writeErrors_doesNotSetInMemoryCache() {
    CampaignImpressionList otherCampaignImpressionList =
        CampaignImpressionList.getDefaultInstance();
    when(storageClient.write(any(CampaignImpressionList.class)))
        .thenReturn(Completable.error(new IOException()));
    when(storageClient.read(any(CampaignImpressionsParser.class))).thenReturn(Maybe.empty());
    impressionStorageClient.storeImpression(campaignImpression).subscribe();
    when(storageClient.read(any(CampaignImpressionsParser.class)))
        .thenReturn(Maybe.just(otherCampaignImpressionList));

    TestSubscriber<CampaignImpressionList> subscriber =
        impressionStorageClient.getAllImpressions().toFlowable().test();

    assertThat(getPlainValues(subscriber)).containsExactly(otherCampaignImpressionList);
  }

  @Test
  public void storeImpression_writeErrors_notifiesError() {
    when(storageClient.write(any(CampaignImpressionList.class)))
        .thenReturn(Completable.error(new IOException()));

    TestSubscriber<Object> subscriber =
        impressionStorageClient.storeImpression(campaignImpression).toFlowable().test();

    subscriber.assertError(IOException.class);
  }

  @Test
  public void storeImpression_readErrors_notifiesError() {
    when(storageClient.read(any(CampaignImpressionsParser.class)))
        .thenReturn(Maybe.error(new IOException()));

    TestSubscriber<Object> subscriber =
        impressionStorageClient.storeImpression(campaignImpression).toFlowable().test();

    subscriber.assertError(IOException.class);
  }

  @Test
  public void getAllImpressions_noErrors_returnsCampaignsList() {
    TestSubscriber<CampaignImpressionList> subscriber =
        impressionStorageClient.getAllImpressions().toFlowable().test();

    assertThat(getPlainValues(subscriber)).containsExactly(campaignImpressionList);
  }

  @Test
  public void getAllImpressions_readError_notifiesError() {
    when(storageClient.read(any(CampaignImpressionsParser.class)))
        .thenReturn(Maybe.error(new IOException()));

    TestSubscriber<CampaignImpressionList> subscriber =
        impressionStorageClient.getAllImpressions().toFlowable().test();

    subscriber.assertError(IOException.class);
  }

  @Test
  public void isImpressed_ifCampaignImpressed_isTrue() {
    TestSubscriber<Boolean> subscriber =
        impressionStorageClient.isImpressed(vanillaCampaign).toFlowable().test();

    assertThat(subscriber.getEvents().get(0)).containsExactly(true);
  }

  @Test
  public void isImpressed_ifCampaignNotImpressed_isFalse() {
    TestSubscriber<Boolean> subscriber =
        impressionStorageClient
            .isImpressed(
                ThickContent.newBuilder()
                    .setVanillaPayload(
                        CampaignProto.VanillaCampaignPayload.newBuilder().setCampaignId("WALRUS"))
                    .build())
            .toFlowable()
            .test();

    assertThat(subscriber.getEvents().get(0)).containsExactly(false);
  }

  @Test
  public void isImpressed_ifExperimentImpressed_isTrue() {
    when(storageClient.read(any(CampaignImpressionsParser.class)))
        .thenReturn(Maybe.fromCallable(() -> experimentImpressionList));
    TestSubscriber<Boolean> subscriber =
        impressionStorageClient.isImpressed(experimentalCampaign).toFlowable().test();

    assertThat(subscriber.getEvents().get(0)).containsExactly(true);
  }

  @Test
  public void isImpressed_ifExperimentNotImpressed_isFalse() {
    TestSubscriber<Boolean> subscriber =
        impressionStorageClient.isImpressed(experimentalCampaign).toFlowable().test();

    assertThat(subscriber.getEvents().get(0)).containsExactly(false);
  }

  @Test
  public void isImpressed_ifNoCampaigns_isFalse() {
    when(storageClient.read(any(CampaignImpressionsParser.class))).thenReturn(Maybe.empty());

    TestSubscriber<Boolean> subscriber =
        impressionStorageClient.isImpressed(vanillaCampaign).toFlowable().test();

    assertThat(subscriber.getEvents().get(0)).containsExactly(false);
  }

  @Test
  public void isImpressed_onError_notifiesError() {
    when(storageClient.read(any(CampaignImpressionsParser.class)))
        .thenReturn(Maybe.error(new IOException()));

    TestSubscriber<Boolean> subscriber =
        impressionStorageClient.isImpressed(vanillaCampaign).toFlowable().test();

    subscriber.assertError(IOException.class);
  }

  @Test
  public void clearImpressions_clearsImpressionsForFetchedCampaign() {
    // verify campaign is impressed.
    TestSubscriber<Boolean> subscriber =
        impressionStorageClient.isImpressed(vanillaCampaign).toFlowable().test();
    assertThat(subscriber.getEvents().get(0)).containsExactly(true);

    // clear impressions for a fetch response containing that campaign.
    // This simulates having received the campaign again from the server.
    impressionStorageClient
        .clearImpressions(
            FetchEligibleCampaignsResponse.newBuilder().addMessages(vanillaCampaign).build())
        .subscribe();

    // Verify campaign is no longer impressed.
    TestSubscriber<Boolean> subscriber2 =
        impressionStorageClient.isImpressed(vanillaCampaign).toFlowable().test();
    assertThat(subscriber2.getEvents().get(0)).containsExactly(false);
  }

  @Test
  public void clearImpressions_doesNotClearImpressionForUnfetchedCampaign() {
    // verify initial campaign is impressed.
    TestSubscriber<Boolean> subscriber =
        impressionStorageClient.isImpressed(vanillaCampaign).toFlowable().test();
    assertThat(subscriber.getEvents().get(0)).containsExactly(true);

    // clear impressions for a fetch response containing a different campaign.
    // This simulates having received the campaign again from the server.
    impressionStorageClient
        .clearImpressions(
            FetchEligibleCampaignsResponse.newBuilder().addMessages(experimentalCampaign).build())
        .subscribe();

    // Verify campaign is still impressed.
    TestSubscriber<Boolean> subscriber2 =
        impressionStorageClient.isImpressed(vanillaCampaign).toFlowable().test();
    assertThat(subscriber2.getEvents().get(0)).containsExactly(true);
  }

  interface CampaignImpressionsParser extends Parser<CampaignImpressionList> {}
}
