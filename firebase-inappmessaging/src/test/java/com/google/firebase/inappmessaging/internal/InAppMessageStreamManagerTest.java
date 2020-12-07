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
import static com.google.firebase.inappmessaging.CommonTypesProto.Trigger.ON_FOREGROUND;
import static com.google.firebase.inappmessaging.testutil.TestData.ANALYTICS_EVENT_NAME;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_TEST_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_ID_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_NAME_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.ON_FOREGROUND_EVENT_NAME;
import static com.google.firebase.inappmessaging.testutil.TestProtos.BANNER_MESSAGE_PROTO;
import static io.reactivex.BackpressureStrategy.BUFFER;
import static io.reactivex.schedulers.Schedulers.trampoline;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.inappmessaging.CommonTypesProto.Event;
import com.google.firebase.inappmessaging.CommonTypesProto.Priority;
import com.google.firebase.inappmessaging.CommonTypesProto.TriggeringCondition;
import com.google.firebase.inappmessaging.ExperimentPayloadProto;
import com.google.firebase.inappmessaging.internal.time.FakeClock;
import com.google.firebase.inappmessaging.model.RateLimit;
import com.google.firebase.inappmessaging.model.TriggeredInAppMessage;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.VanillaCampaignPayload;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpression;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpressionList;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.subscribers.TestSubscriber;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

// TODO: Refactor and clean this logic
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class InAppMessageStreamManagerTest {
  private static final long PAST = 1000000;
  private static final long NOW = PAST + 100000;
  private static final long FUTURE = NOW + 1000000;
  private static final String INSTALLATION_ID = "instance_id";
  private static final String INSTALLATION_TOKEN = "instance_token";

  private static final TriggeringCondition.Builder ON_ANALYTICS_TRIGGER =
      TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(ANALYTICS_EVENT_NAME));
  private static final TriggeringCondition ON_FOREGROUND_TRIGGER =
      TriggeringCondition.newBuilder().setFiamTrigger(ON_FOREGROUND).build();
  private static final Priority priorityTwo = Priority.newBuilder().setValue(2).build();
  private static final VanillaCampaignPayload.Builder vanillaCampaign =
      VanillaCampaignPayload.newBuilder()
          .setCampaignId(CAMPAIGN_ID_STRING)
          .setCampaignName(CAMPAIGN_NAME_STRING)
          .setCampaignStartTimeMillis(PAST)
          .setCampaignEndTimeMillis(FUTURE);
  private static final CampaignProto.ExperimentalCampaignPayload.Builder experimentalCampaign =
      CampaignProto.ExperimentalCampaignPayload.newBuilder()
          .setCampaignId(CAMPAIGN_ID_STRING)
          .setCampaignName(CAMPAIGN_NAME_STRING)
          .setCampaignStartTimeMillis(PAST)
          .setCampaignEndTimeMillis(FUTURE)
          .setExperimentPayload(ExperimentPayloadProto.ExperimentPayload.getDefaultInstance());
  private static final ThickContent.Builder thickContentBuilder =
      ThickContent.newBuilder()
          .setPriority(priorityTwo)
          .addTriggeringConditions(ON_FOREGROUND_TRIGGER)
          .addTriggeringConditions(ON_ANALYTICS_TRIGGER)
          .setVanillaPayload(vanillaCampaign)
          .setContent(BANNER_MESSAGE_PROTO);
  private static final ThickContent thickContent = thickContentBuilder.build();
  private static final ThickContent experimentalContent =
      thickContentBuilder
          .clearVanillaPayload()
          .setExperimentalPayload(experimentalCampaign)
          .build();

  private static final TriggeredInAppMessage onForegroundTriggered =
      new TriggeredInAppMessage(BANNER_MESSAGE_MODEL, ON_FOREGROUND_EVENT_NAME);
  private static final TriggeredInAppMessage onAnalyticsTriggered =
      new TriggeredInAppMessage(BANNER_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);
  private static final FetchEligibleCampaignsResponse.Builder campaignsResponseBuilder =
      FetchEligibleCampaignsResponse.newBuilder()
          .setExpirationEpochTimestampMillis(FUTURE)
          .addMessages(thickContent);
  private static final FetchEligibleCampaignsResponse campaignsResponse =
      campaignsResponseBuilder.build();
  private static final Schedulers schedulers =
      new Schedulers(trampoline(), trampoline(), trampoline());

  private static final CampaignImpressionList CAMPAIGN_IMPRESSIONS =
      CampaignImpressionList.newBuilder()
          .addAlreadySeenCampaigns(
              CampaignImpression.newBuilder().setCampaignId(CAMPAIGN_ID_STRING).build())
          .build();
  private static final String LIMITER_KEY = "LIMITER_KEY";
  private static final RateLimit appForegroundRateLimit =
      RateLimit.builder()
          .setLimit(1)
          .setLimiterKey(LIMITER_KEY)
          .setTimeToLiveMillis(TimeUnit.DAYS.toMillis(1))
          .build();

  private static final InstallationTokenResult INSTALLATION_TOKEN_RESULT =
      new InstallationTokenResult() {
        @NonNull
        @Override
        public String getToken() {
          return INSTALLATION_TOKEN;
        }

        @Override
        public long getTokenExpirationTimestamp() {
          return 0;
        }

        @Override
        public long getTokenCreationTimestamp() {
          return 0;
        }

        @Override
        public Builder toBuilder() {
          return null;
        }
      };
  private static final InstallationIdResult FID_RESULT =
      InstallationIdResult.create(INSTALLATION_ID, INSTALLATION_TOKEN_RESULT);

  @Mock private ApiClient mockApiClient;
  @Mock private Application application;
  @Mock private CampaignCacheClient campaignCacheClient;
  @Mock private ImpressionStorageClient impressionStorageClient;
  @Mock private TestDeviceHelper testDeviceHelper;
  @Mock private AbtIntegrationHelper abtIntegrationHelper;
  @Mock private RateLimiterClient rateLimiterClient;
  @Mock private AnalyticsEventsManager analyticsEventsManager;
  @Mock private FirebaseInstallationsApi firebaseInstallations;
  @Mock private DataCollectionHelper dataCollectionHelper;
  @Captor private ArgumentCaptor<InstallationIdResult> installationIdResultArgumentCaptor;
  @Captor private ArgumentCaptor<CampaignImpressionList> campaignImpressionListArgumentCaptor;

  private FlowableEmitter<String> appForegroundEmitter;
  private TestSubscriber<TriggeredInAppMessage> subscriber;
  private FlowableEmitter<String> analyticsEmitter;
  private FlowableEmitter<String> programmaticTriggerEmitter;

  private static List<TriggeredInAppMessage> getPlainValues(
      TestSubscriber<TriggeredInAppMessage> subscriber) {
    return subscriber.getEvents().get(0).stream()
        .map(obj -> (TriggeredInAppMessage) obj)
        .collect(Collectors.toList());
  }

  private void assertExpectedGetFiamsCall() {
    assertThat(campaignImpressionListArgumentCaptor.getValue()).isEqualTo(CAMPAIGN_IMPRESSIONS);
    assertThat(installationIdResultArgumentCaptor.getValue().installationId())
        .isEqualTo(FID_RESULT.installationId());
    assertThat(installationIdResultArgumentCaptor.getValue().installationTokenResult().getToken())
        .isEqualTo(FID_RESULT.installationTokenResult().getToken());
  }

  private static void assertExpectedMessageTriggered(
      TestSubscriber<TriggeredInAppMessage> subscriber, TriggeredInAppMessage message) {
    List<TriggeredInAppMessage> values = getPlainValues(subscriber);
    assertThat(values.size()).isEqualTo(1);
    TriggeredInAppMessage actual = values.get(0);
    assertThat(actual.getInAppMessage()).isEqualTo(message.getInAppMessage());
    assertThat(actual.getTriggeringEvent()).isEqualTo(message.getTriggeringEvent());
  }

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ConnectableFlowable<String> appForegroundEventFlowable =
        Flowable.<String>create(e -> appForegroundEmitter = e, BUFFER).publish();
    appForegroundEventFlowable.connect();

    ConnectableFlowable<String> analyticsEventsFlowable =
        Flowable.<String>create(e -> analyticsEmitter = e, BUFFER).publish();
    analyticsEventsFlowable.connect();
    when(analyticsEventsManager.getAnalyticsEventsFlowable()).thenReturn(analyticsEventsFlowable);

    ConnectableFlowable<String> programmaticTriggerFlowable =
        Flowable.<String>create(e -> programmaticTriggerEmitter = e, BUFFER).publish();
    programmaticTriggerFlowable.connect();

    InAppMessageStreamManager streamManager =
        new InAppMessageStreamManager(
            appForegroundEventFlowable,
            programmaticTriggerFlowable,
            campaignCacheClient,
            new FakeClock(NOW),
            mockApiClient,
            analyticsEventsManager,
            schedulers,
            impressionStorageClient,
            rateLimiterClient,
            appForegroundRateLimit,
            testDeviceHelper,
            firebaseInstallations,
            dataCollectionHelper,
            abtIntegrationHelper);
    subscriber = streamManager.createFirebaseInAppMessageStream().test();
    when(application.getApplicationContext()).thenReturn(application);
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(true);
    when(impressionStorageClient.clearImpressions(any(FetchEligibleCampaignsResponse.class)))
        .thenReturn(Completable.complete());
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit)).thenReturn(Single.just(false));
    when(campaignCacheClient.get()).thenReturn(Maybe.empty());
    when(campaignCacheClient.put(any(FetchEligibleCampaignsResponse.class)))
        .thenReturn(Completable.complete());
    when(impressionStorageClient.isImpressed(any(ThickContent.class)))
        .thenReturn(Single.just(false));
    when(impressionStorageClient.getAllImpressions()).thenReturn(Maybe.just(CAMPAIGN_IMPRESSIONS));
    when(firebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    when(firebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));
  }

  @Test
  public void stream_onAppOpen_notifiesSubscriber() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_onAppOpen_dataCollectionDisabled_doesNotFetch() {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(false);
    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    subscriber.assertNoValues();
    verify(mockApiClient, times(0)).getFiams(any(), any());
    verify(campaignCacheClient, times(1)).put(InAppMessageStreamManager.cacheExpiringResponse());
  }

  @Test
  public void stream_onAppOpen_withEmptyIID_doesNotFetch() {
    when(firebaseInstallations.getId()).thenReturn(Tasks.forResult(""));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    verify(mockApiClient, times(0)).getFiams(any(), any());
    verify(campaignCacheClient, times(1)).put(InAppMessageStreamManager.cacheExpiringResponse());
  }

  @Test
  public void stream_onAppOpen_withEmptyFIDToken_doesNotFetch() {
    when(firebaseInstallations.getToken(false))
        .thenReturn(
            Tasks.forResult(
                new InstallationTokenResult() {
                  @Override
                  public String getToken() {
                    return "";
                  }

                  @Override
                  public long getTokenExpirationTimestamp() {
                    return 0;
                  }

                  @Override
                  public long getTokenCreationTimestamp() {
                    return 0;
                  }

                  @Override
                  public Builder toBuilder() {
                    return null;
                  }
                }));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    verify(mockApiClient, times(0)).getFiams(any(), any());
    verify(campaignCacheClient, times(1)).put(InAppMessageStreamManager.cacheExpiringResponse());
  }

  @Test
  public void stream_onAnalyticsEvent_notifiesSubscriber() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_onProgrammaticTrigger_notifiesSubscriber() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    programmaticTriggerEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_onAppOpen_remainsOpen() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNotComplete();
  }

  @Test
  public void stream_onUnrelatedForegroundEvent_doesNotTrigger() {
    String unrelatedAnalyticsEvent = "some_other_event";
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(unrelatedAnalyticsEvent);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onUnrelatedAnalyticsEvent_doesNotTrigger() {
    String unrelatedAnalyticsEvent = "some_other_event";
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    analyticsEmitter.onNext(unrelatedAnalyticsEvent);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onValidCampaign_notifiesSubscriber() {
    ThickContent t =
        thickContentBuilder
            .clearVanillaPayload()
            .setVanillaPayload(
                VanillaCampaignPayload.newBuilder()
                    .setCampaignStartTimeMillis(PAST)
                    .setCampaignEndTimeMillis(FUTURE))
            .build();

    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(t)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_onExpiredCampaign_doesNotTrigger() {
    ThickContent t =
        thickContentBuilder
            .clearVanillaPayload()
            .setVanillaPayload(
                VanillaCampaignPayload.newBuilder()
                    .setCampaignStartTimeMillis(PAST)
                    .setCampaignEndTimeMillis(NOW))
            .build();

    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(t)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onFutureCampaign_doesNotTrigger() {
    ThickContent t =
        thickContentBuilder
            .clearVanillaPayload()
            .setVanillaPayload(
                VanillaCampaignPayload.newBuilder()
                    .setCampaignStartTimeMillis(FUTURE)
                    .setCampaignEndTimeMillis(FUTURE))
            .build();

    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(t)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onValidExperiment_notifiesSubscriber() {
    ThickContent t =
        thickContentBuilder
            .clearVanillaPayload()
            .setExperimentalPayload(
                CampaignProto.ExperimentalCampaignPayload.newBuilder()
                    .setCampaignStartTimeMillis(PAST)
                    .setCampaignEndTimeMillis(FUTURE)
                    .setExperimentPayload(
                        ExperimentPayloadProto.ExperimentPayload.getDefaultInstance()))
            .build();

    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(t)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_ontestExperiment_doesNotSetExperimentActive() {
    ThickContent t =
        thickContentBuilder
            .clearVanillaPayload()
            .setIsTestCampaign(true)
            .setExperimentalPayload(
                CampaignProto.ExperimentalCampaignPayload.newBuilder()
                    .setCampaignStartTimeMillis(PAST)
                    .setCampaignEndTimeMillis(FUTURE)
                    .setExperimentPayload(
                        ExperimentPayloadProto.ExperimentPayload.getDefaultInstance()))
            .build();

    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(t)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
    verify(abtIntegrationHelper, never()).setExperimentActive(Mockito.any());
  }

  @Test
  public void stream_onExpiredExperiment_doesNotTrigger() {
    ThickContent t =
        thickContentBuilder
            .clearVanillaPayload()
            .setExperimentalPayload(
                CampaignProto.ExperimentalCampaignPayload.newBuilder()
                    .setCampaignStartTimeMillis(PAST)
                    .setCampaignEndTimeMillis(NOW)
                    .setExperimentPayload(
                        ExperimentPayloadProto.ExperimentPayload.getDefaultInstance()))
            .build();
    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(t)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onFutureExperiment_doesNotTrigger() {
    ThickContent t =
        thickContentBuilder
            .clearVanillaPayload()
            .setExperimentalPayload(
                CampaignProto.ExperimentalCampaignPayload.newBuilder()
                    .setCampaignStartTimeMillis(FUTURE)
                    .setCampaignEndTimeMillis(FUTURE)
                    .setExperimentPayload(
                        ExperimentPayloadProto.ExperimentPayload.getDefaultInstance()))
            .build();
    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(t)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onMultipleCampaigns_triggersTestMessage() {
    ThickContent highPriorityContent =
        ThickContent.newBuilder(thickContent)
            .setPriority(Priority.newBuilder().setValue(1))
            .build();
    ThickContent testContent =
        ThickContent.newBuilder(thickContent)
            .setPriority(Priority.newBuilder().setValue(2))
            .setIsTestCampaign(true)
            .build();
    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder(campaignsResponse)
            .addMessages(highPriorityContent)
            .addMessages(testContent)
            .build();
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(
        subscriber, new TriggeredInAppMessage(BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME));
  }

  @Test
  public void stream_onApiClientFailure_absorbsErrors() {
    Throwable t = new StatusRuntimeException(Status.DATA_LOSS);
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenThrow(t);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNotComplete();
    subscriber.assertNoErrors();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onCacheHit_notifiesCachedValue() {
    when(campaignCacheClient.get()).thenReturn(Maybe.just(campaignsResponse));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_onServiceFetchSuccess_cachesValue() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient).put(campaignsResponse);
  }

  @Test
  public void stream_onServiceFetchSuccess_updatesContextualTriggers() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    verify(analyticsEventsManager).updateContextualTriggers(campaignsResponse);
  }

  @Test
  public void stream_onServiceFetchFailure_doesNotCacheValue() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenThrow(new RuntimeException("e"));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient, times(0)).put(campaignsResponse);
  }

  @Test
  public void stream_whenAppInstallIsFresh_doesNotCacheValue() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(testDeviceHelper.isAppInstallFresh()).thenReturn(true);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient, times(0)).put(any());
  }

  @Test
  public void stream_whenDeviceIsInTestMode_doesNotCacheValue() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(testDeviceHelper.isDeviceInTestMode()).thenReturn(true);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient, times(0)).put(any());
  }

  @Test
  public void stream_onCacheReadFailure_notifiesValueFetchedFromService() {
    when(campaignCacheClient.get()).thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_onCacheAndApiFail_absorbsFailure() {
    when(campaignCacheClient.get()).thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenThrow(new NullPointerException());

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNotComplete();
    subscriber.assertNoErrors();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onCacheWriteFailure_AbsorbsError() {
    when(campaignCacheClient.put(any(FetchEligibleCampaignsResponse.class)))
        .thenReturn(Completable.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressed_filtersCampaign() {
    when(impressionStorageClient.isImpressed(any(ThickContent.class)))
        .thenReturn(Single.just(true));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_whenCampaignImpressionStoreFails_doesNotFilterCampaign() {
    when(impressionStorageClient.isImpressed(any(ThickContent.class)))
        .thenReturn(Single.error(new Exception("e1")));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressionStoreFail_doesNotFilterCampaign() {
    when(impressionStorageClient.isImpressed(any(ThickContent.class)))
        .thenReturn(Single.error(new Exception("e1")));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressionStoreFails_absorbsError() {
    when(impressionStorageClient.getAllImpressions())
        .thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(any(InstallationIdResult.class), any(CampaignImpressionList.class)))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressionStoreFails_wiresEmptyImpressionList() {
    when(impressionStorageClient.getAllImpressions())
        .thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            any(InstallationIdResult.class), campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertThat(campaignImpressionListArgumentCaptor.getValue())
        .isEqualTo(CampaignImpressionList.getDefaultInstance());
  }

  @Test
  public void stream_whenAppOpenRateLimited_doesNotTrigger() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit)).thenReturn(Single.just(true));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_whenAppOpenRateLimited_stillTriggersTestMessage() {
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit)).thenReturn(Single.just(true));
    when(testDeviceHelper.isDeviceInTestMode()).thenReturn(true);
    ThickContent testMessageContent =
        ThickContent.newBuilder()
            .setPriority(priorityTwo)
            .addTriggeringConditions(ON_FOREGROUND_TRIGGER)
            .addTriggeringConditions(ON_ANALYTICS_TRIGGER)
            .setIsTestCampaign(true)
            .setVanillaPayload(vanillaCampaign)
            .setContent(BANNER_MESSAGE_PROTO)
            .build();

    TriggeredInAppMessage testTriggered =
        new TriggeredInAppMessage(BANNER_TEST_MESSAGE_MODEL, ON_FOREGROUND_EVENT_NAME);
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(testMessageContent)
            .build();
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(response);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, testTriggered);
  }

  @Test
  public void stream_alwaysTriggersTestMessagesOnAppForground() {
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit)).thenReturn(Single.just(false));
    when(testDeviceHelper.isDeviceInTestMode()).thenReturn(true);
    ThickContent testMessageContent =
        ThickContent.newBuilder()
            .setPriority(priorityTwo)
            .addTriggeringConditions(ON_ANALYTICS_TRIGGER)
            .setIsTestCampaign(true)
            .setVanillaPayload(vanillaCampaign)
            .setContent(BANNER_MESSAGE_PROTO)
            .build();

    TriggeredInAppMessage testTriggered =
        new TriggeredInAppMessage(BANNER_TEST_MESSAGE_MODEL, ON_FOREGROUND_EVENT_NAME);
    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(testMessageContent)
            .build();

    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(r);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, testTriggered);
  }

  @Test
  public void stream_whenRateLimitingClientFails_triggers() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit))
        .thenReturn(Single.error(new NullPointerException("e1")));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenAppOpenRateLimited_notifiesAnalyticsSubscriber() {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit)).thenReturn(Single.just(true));

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }
}
