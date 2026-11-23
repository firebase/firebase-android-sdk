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
import static com.google.firebase.inappmessaging.testutil.TestProtos.MODAL_MESSAGE_PROTO;
import static io.reactivex.BackpressureStrategy.BUFFER;
import static io.reactivex.schedulers.Schedulers.trampoline;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.inappmessaging.CommonTypesProto.Event;
import com.google.firebase.inappmessaging.CommonTypesProto.Priority;
import com.google.firebase.inappmessaging.CommonTypesProto.TriggeringCondition;
import com.google.firebase.inappmessaging.ExperimentPayloadProto;
import com.google.firebase.inappmessaging.MessagesProto;
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
import org.robolectric.shadows.ShadowLog;

// TODO: Refactor and clean this logic
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class InAppMessageStreamManagerTest {
  private static final long PAST = 1000000;
  private static final long NOW = PAST + 100000;
  private static final long FUTURE = NOW + 1000000;
  private static final String INSTALLATION_ID = "instance_id";
  private static final String INSTALLATION_TOKEN = "instance_token";
  private static final long BLOCKING_EXECUTOR_TIMEOUT = 100;

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

  private static final String MALFORMED_CAMPAIGN_ID = "malformed_campaign_id";
  private static final String HIGH_PRIORITY_CAMPAIGN_ID = "high_priority_campaign_id";
  // Proto3 cannot tell a missing string field from an empty one, so these model campaigns where the
  // backend omits a field that the message models require.
  private static final MessagesProto.Content BANNER_WITHOUT_BACKGROUND_COLOR_PROTO =
      MessagesProto.Content.newBuilder()
          .setBanner(BANNER_MESSAGE_PROTO.getBanner().toBuilder().clearBackgroundHexColor())
          .build();
  private static final MessagesProto.Content MODAL_WITHOUT_TITLE_PROTO =
      MessagesProto.Content.newBuilder()
          .setModal(MODAL_MESSAGE_PROTO.getModal().toBuilder().clearTitle())
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
            abtIntegrationHelper,
            TestOnlyExecutors.blocking());
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
  public void stream_onAppOpen_notifiesSubscriber() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_onAppOpen_dataCollectionDisabled_doesNotFetch() throws InterruptedException {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(false);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    subscriber.assertNoValues();
    verify(mockApiClient, times(0)).getFiams(any(), any());
    verify(campaignCacheClient, times(1)).put(InAppMessageStreamManager.cacheExpiringResponse());
  }

  @Test
  public void stream_onAppOpen_withEmptyIID_doesNotFetch() throws InterruptedException {
    when(firebaseInstallations.getId()).thenReturn(Tasks.forResult(""));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    verify(mockApiClient, times(0)).getFiams(any(), any());
    verify(campaignCacheClient, times(1)).put(InAppMessageStreamManager.cacheExpiringResponse());
  }

  @Test
  public void stream_onAppOpen_withEmptyFIDToken_doesNotFetch() throws InterruptedException {
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
    waitForBlockingExecutor();

    verify(mockApiClient, times(0)).getFiams(any(), any());
    verify(campaignCacheClient, times(1)).put(InAppMessageStreamManager.cacheExpiringResponse());
  }

  @Test
  public void stream_onAnalyticsEvent_notifiesSubscriber() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_onProgrammaticTrigger_notifiesSubscriber() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    programmaticTriggerEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_onAppOpen_remainsOpen() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNotComplete();
  }

  @Test
  public void stream_onUnrelatedForegroundEvent_doesNotTrigger() throws InterruptedException {
    String unrelatedAnalyticsEvent = "some_other_event";
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(unrelatedAnalyticsEvent);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onUnrelatedAnalyticsEvent_doesNotTrigger() throws InterruptedException {
    String unrelatedAnalyticsEvent = "some_other_event";
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    analyticsEmitter.onNext(unrelatedAnalyticsEvent);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onValidCampaign_notifiesSubscriber() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_onExpiredCampaign_doesNotTrigger() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onFutureCampaign_doesNotTrigger() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onValidExperiment_notifiesSubscriber() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_ontestExperiment_doesNotSetExperimentActive() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
    verify(abtIntegrationHelper, never()).setExperimentActive(Mockito.any());
  }

  @Test
  public void stream_onExpiredExperiment_doesNotTrigger() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onFutureExperiment_doesNotTrigger() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onMultipleCampaigns_triggersTestMessage() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(
        subscriber, new TriggeredInAppMessage(BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME));
  }

  @Test
  public void stream_onApiClientFailure_absorbsErrors() throws InterruptedException {
    Throwable t = new StatusRuntimeException(Status.DATA_LOSS);
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenThrow(t);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNotComplete();
    subscriber.assertNoErrors();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onCacheHit_notifiesCachedValue() throws InterruptedException {
    when(campaignCacheClient.get()).thenReturn(Maybe.just(campaignsResponse));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_onServiceFetchSuccess_cachesValue() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient).put(campaignsResponse);
  }

  @Test
  public void stream_onServiceFetchSuccess_updatesContextualTriggers() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    verify(analyticsEventsManager).updateContextualTriggers(campaignsResponse);
  }

  @Test
  public void stream_onServiceFetchFailure_doesNotCacheValue() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenThrow(new RuntimeException("e"));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient, times(0)).put(campaignsResponse);
  }

  @Test
  public void stream_whenAppInstallIsFresh_doesNotCacheValue() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(testDeviceHelper.isAppInstallFresh()).thenReturn(true);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient, times(0)).put(any());
  }

  @Test
  public void stream_whenDeviceIsInTestMode_doesNotCacheValue() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(testDeviceHelper.isDeviceInTestMode()).thenReturn(true);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    verify(campaignCacheClient, times(0)).put(any());
  }

  @Test
  public void stream_onCacheReadFailure_notifiesValueFetchedFromService()
      throws InterruptedException {
    when(campaignCacheClient.get()).thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_onCacheAndApiFail_absorbsFailure() throws InterruptedException {
    when(campaignCacheClient.get()).thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenThrow(new NullPointerException());

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNotComplete();
    subscriber.assertNoErrors();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_onCacheWriteFailure_AbsorbsError() throws InterruptedException {
    when(campaignCacheClient.put(any(FetchEligibleCampaignsResponse.class)))
        .thenReturn(Completable.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressed_filtersCampaign() throws InterruptedException {
    when(impressionStorageClient.isImpressed(any(ThickContent.class)))
        .thenReturn(Single.just(true));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_whenCampaignImpressionStoreFails_doesNotFilterCampaign()
      throws InterruptedException {
    when(impressionStorageClient.isImpressed(any(ThickContent.class)))
        .thenReturn(Single.error(new Exception("e1")));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressionStoreFail_doesNotFilterCampaign()
      throws InterruptedException {
    when(impressionStorageClient.isImpressed(any(ThickContent.class)))
        .thenReturn(Single.error(new Exception("e1")));
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressionStoreFails_absorbsError() throws InterruptedException {
    when(impressionStorageClient.getAllImpressions())
        .thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(any(InstallationIdResult.class), any(CampaignImpressionList.class)))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenCampaignImpressionStoreFails_wiresEmptyImpressionList()
      throws InterruptedException {
    when(impressionStorageClient.getAllImpressions())
        .thenReturn(Maybe.error(new NullPointerException()));
    when(mockApiClient.getFiams(
            any(InstallationIdResult.class), campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertThat(campaignImpressionListArgumentCaptor.getValue())
        .isEqualTo(CampaignImpressionList.getDefaultInstance());
  }

  @Test
  public void stream_whenAppOpenRateLimited_doesNotTrigger() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit)).thenReturn(Single.just(true));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    subscriber.assertNoValues();
  }

  @Test
  public void stream_whenAppOpenRateLimited_stillTriggersTestMessage() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, testTriggered);
  }

  @Test
  public void stream_alwaysTriggersTestMessagesOnAppForeground() throws InterruptedException {
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
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, testTriggered);
  }

  @Test
  public void stream_whenRateLimitingClientFails_triggers() throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit))
        .thenReturn(Single.error(new NullPointerException("e1")));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onForegroundTriggered);
  }

  @Test
  public void stream_whenAppOpenRateLimited_notifiesAnalyticsSubscriber()
      throws InterruptedException {
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(campaignsResponse);
    when(rateLimiterClient.isRateLimited(appForegroundRateLimit)).thenReturn(Single.just(true));

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedGetFiamsCall();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_onCampaignsMissingRequiredFields_skipsThemAndKeepsStreamOpen()
      throws InterruptedException {
    FetchEligibleCampaignsResponse malformedResponse =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(
                ThickContent.newBuilder(thickContent)
                    .setContent(BANNER_WITHOUT_BACKGROUND_COLOR_PROTO))
            .addMessages(
                ThickContent.newBuilder(thickContent).setContent(MODAL_WITHOUT_TITLE_PROTO))
            .build();
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(malformedResponse, campaignsResponse);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    subscriber.assertNoErrors();
    subscriber.assertNotComplete();
    subscriber.assertNoValues();

    // The stream keeps working, so a later valid campaign is still delivered.
    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    subscriber.assertNoErrors();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
  }

  @Test
  public void stream_whenHighestPriorityCampaignIsMalformed_triggersNextEligibleCampaign()
      throws InterruptedException {
    ThickContent malformedHighPriorityContent =
        ThickContent.newBuilder(thickContent)
            .setPriority(Priority.newBuilder().setValue(1))
            .setVanillaPayload(
                thickContent.getVanillaPayload().toBuilder().setCampaignId(MALFORMED_CAMPAIGN_ID))
            .setContent(BANNER_WITHOUT_BACKGROUND_COLOR_PROTO)
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(thickContent)
            .addMessages(malformedHighPriorityContent)
            .build();
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(response);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    subscriber.assertNoErrors();
    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
    assertThat(triggeredCampaignId(subscriber)).isEqualTo(CAMPAIGN_ID_STRING);
    assertThat(hasWarningLogContaining(MALFORMED_CAMPAIGN_ID)).isTrue();
  }

  @Test
  public void stream_onMalformedExperiment_doesNotSetExperimentActive()
      throws InterruptedException {
    ThickContent malformedExperiment =
        ThickContent.newBuilder(experimentalContent)
            .setContent(BANNER_WITHOUT_BACKGROUND_COLOR_PROTO)
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(malformedExperiment)
            .build();
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(response);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    subscriber.assertNoErrors();
    subscriber.assertNoValues();
    verify(abtIntegrationHelper, never()).setExperimentActive(any());
  }

  @Test
  public void stream_onMultipleValidExperiments_setsOnlyTriggeredExperimentActive()
      throws InterruptedException {
    ExperimentPayloadProto.ExperimentPayload highPriorityExperiment =
        ExperimentPayloadProto.ExperimentPayload.newBuilder()
            .setExperimentId("high_priority_experiment")
            .build();
    ExperimentPayloadProto.ExperimentPayload lowPriorityExperiment =
        ExperimentPayloadProto.ExperimentPayload.newBuilder()
            .setExperimentId("low_priority_experiment")
            .build();
    ThickContent highPriorityContent =
        ThickContent.newBuilder(experimentalContent)
            .setPriority(Priority.newBuilder().setValue(1))
            .setExperimentalPayload(
                experimentalContent.getExperimentalPayload().toBuilder()
                    .setCampaignId(HIGH_PRIORITY_CAMPAIGN_ID)
                    .setExperimentPayload(highPriorityExperiment))
            .build();
    ThickContent lowPriorityContent =
        ThickContent.newBuilder(experimentalContent)
            .setExperimentalPayload(
                experimentalContent.getExperimentalPayload().toBuilder()
                    .setExperimentPayload(lowPriorityExperiment))
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(lowPriorityContent)
            .addMessages(highPriorityContent)
            .build();
    when(mockApiClient.getFiams(
            installationIdResultArgumentCaptor.capture(),
            campaignImpressionListArgumentCaptor.capture()))
        .thenReturn(response);

    analyticsEmitter.onNext(ANALYTICS_EVENT_NAME);
    waitForBlockingExecutor();

    assertExpectedMessageTriggered(subscriber, onAnalyticsTriggered);
    assertThat(triggeredCampaignId(subscriber)).isEqualTo(HIGH_PRIORITY_CAMPAIGN_ID);
    verify(abtIntegrationHelper).setExperimentActive(highPriorityExperiment);
    verify(abtIntegrationHelper, never()).setExperimentActive(lowPriorityExperiment);
  }

  @Test
  public void stream_onMalformedCachedCampaign_skipsCampaignOnEveryTrigger()
      throws InterruptedException {
    FetchEligibleCampaignsResponse malformedResponse =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(
                ThickContent.newBuilder(thickContent)
                    .setContent(BANNER_WITHOUT_BACKGROUND_COLOR_PROTO))
            .build();
    when(campaignCacheClient.get()).thenReturn(Maybe.just(malformedResponse));

    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();
    appForegroundEmitter.onNext(ON_FOREGROUND_EVENT_NAME);
    waitForBlockingExecutor();

    subscriber.assertNoErrors();
    subscriber.assertNotComplete();
    subscriber.assertNoValues();
  }

  private static String triggeredCampaignId(TestSubscriber<TriggeredInAppMessage> subscriber) {
    return getPlainValues(subscriber)
        .get(0)
        .getInAppMessage()
        .getCampaignMetadata()
        .getCampaignId();
  }

  private static boolean hasWarningLogContaining(String text) {
    return ShadowLog.getLogsForTag(Logging.TAG).stream()
        .anyMatch(log -> log.type == Log.WARN && log.msg.contains(text));
  }

  private static void waitForBlockingExecutor() throws InterruptedException {
    TestOnlyExecutors.blocking().awaitTermination(BLOCKING_EXECUTOR_TIMEOUT, TimeUnit.MILLISECONDS);
  }
}
