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
import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_MODEL_WITHOUT_URL;
import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_MODEL_WITH_BUTTON;
import static com.google.firebase.inappmessaging.testutil.TestData.ANALYTICS_EVENT_NAME;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_TEST_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_ID_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_NAME_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.INSTALLATION_ID;
import static com.google.firebase.inappmessaging.testutil.TestData.IS_NOT_TEST_MESSAGE;
import static com.google.firebase.inappmessaging.testutil.TestData.MESSAGE_BACKGROUND_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.TITLE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.createBannerMessageCustomMetadata;
import static io.reactivex.BackpressureStrategy.BUFFER;
import static io.reactivex.schedulers.Schedulers.trampoline;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.inappmessaging.CommonTypesProto.Event;
import com.google.firebase.inappmessaging.CommonTypesProto.Priority;
import com.google.firebase.inappmessaging.CommonTypesProto.TriggeringCondition;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingDismissType;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason;
import com.google.firebase.inappmessaging.MessagesProto;
import com.google.firebase.inappmessaging.MessagesProto.Content;
import com.google.firebase.inappmessaging.internal.time.FakeClock;
import com.google.firebase.inappmessaging.model.BannerMessage;
import com.google.firebase.inappmessaging.model.CampaignMetadata;
import com.google.firebase.inappmessaging.model.CardMessage;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.RateLimit;
import com.google.firebase.inappmessaging.model.TriggeredInAppMessage;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.VanillaCampaignPayload;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpression;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Maybe;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DisplayCallbacksImplTest {
  private static final long PAST = 1000000;
  private static final long NOW = PAST + 100000;
  private static final long FUTURE = NOW + 1000000;

  private static final String CAMPAIGN_ID2 = "campaign_id2";
  private static final String CAMPAIGN_NAME2 = "campaign_name2";
  private static final String LIMITER_KEY = "LIMITER_KEY";

  private static final TriggeringCondition.Builder onAnalyticsEvent =
      TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(ANALYTICS_EVENT_NAME));
  private static final TriggeringCondition onForeground =
      TriggeringCondition.newBuilder().setFiamTrigger(ON_FOREGROUND).build();

  private static final Priority PRIORITY_TWO = Priority.newBuilder().setValue(2).build();
  private static final VanillaCampaignPayload.Builder vanillaCampaign1 =
      VanillaCampaignPayload.newBuilder()
          .setCampaignId(CAMPAIGN_ID_STRING)
          .setCampaignName(CAMPAIGN_NAME_STRING)
          .setCampaignStartTimeMillis(PAST)
          .setCampaignEndTimeMillis(FUTURE);
  private static final VanillaCampaignPayload.Builder vanillaCampaign2 =
      VanillaCampaignPayload.newBuilder()
          .setCampaignId(CAMPAIGN_ID2)
          .setCampaignName(CAMPAIGN_NAME2)
          .setCampaignStartTimeMillis(PAST)
          .setCampaignEndTimeMillis(FUTURE);
  private static final ThickContent.Builder FOREGROUND_THICK_CONTENT_BUILDER =
      ThickContent.newBuilder()
          .setPriority(PRIORITY_TWO)
          .addTriggeringConditions(onForeground)
          .setVanillaPayload(vanillaCampaign1)
          .setContent(
              Content.newBuilder().setBanner(MessagesProto.BannerMessage.getDefaultInstance()));
  private static final ThickContent.Builder ANALYTICS_EVENT_THICK_CONTENT_BUILDER =
      ThickContent.newBuilder()
          .setPriority(PRIORITY_TWO)
          .addTriggeringConditions(onAnalyticsEvent)
          .setVanillaPayload(vanillaCampaign2)
          .setContent(Content.getDefaultInstance());

  private static final FetchEligibleCampaignsResponse.Builder campaignsResponseBuilder =
      FetchEligibleCampaignsResponse.newBuilder()
          .setExpirationEpochTimestampMillis(FUTURE)
          .addMessages(FOREGROUND_THICK_CONTENT_BUILDER)
          .addMessages(ANALYTICS_EVENT_THICK_CONTENT_BUILDER);
  private static final FetchEligibleCampaignsResponse campaignsResponse =
      campaignsResponseBuilder.build();

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
          return INSTALLATION_ID;
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

  @Mock private static FirebaseInstallationsApi firebaseInstallations;
  @Mock private static MetricsLoggerClient metricsLoggerClient;
  @Mock private Schedulers schedulers;
  @Mock private ImpressionStorageClient impressionStorageClient;
  @Mock private InAppMessageStreamManager inAppMessageStreamManager;
  @Mock private DataCollectionHelper dataCollectionHelper;
  FirebaseApp firebaseApp1;
  FirebaseOptions options;

  private Application application;

  @Mock private RateLimiterClient rateLimiterClient;
  @Mock private CampaignCacheClient campaignCacheClient;
  private DisplayCallbacksFactory displayCallbacksFactory;
  private FlowableEmitter<TriggeredInAppMessage> emitter;
  private final Flowable<TriggeredInAppMessage> fiamStream =
      Flowable.create(e -> emitter = e, BUFFER);

  private Completable fakeImpressionCompletable;
  private Completable fakeRateLimitCompletable;

  private boolean wasRecorded;
  private boolean wasIncremented;

  private FirebaseInAppMessagingDisplayCallbacks displayCallbacksImpl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    wasRecorded = false;
    wasIncremented = false;
    fakeImpressionCompletable = Completable.fromCallable(() -> wasRecorded = true);
    fakeRateLimitCompletable = Completable.fromCallable(() -> wasIncremented = true);

    application = RuntimeEnvironment.application;

    options =
        new FirebaseOptions.Builder()
            .setGcmSenderId("project_number")
            .setApplicationId("app-id")
            .setApiKey("apiKey")
            .setProjectId("fiam-integration-test")
            .build();

    firebaseApp1 = mock(FirebaseApp.class);

    when(firebaseApp1.getName()).thenReturn("app1");
    when(firebaseApp1.getOptions()).thenReturn(options);
    when(firebaseApp1.getApplicationContext()).thenReturn(application);

    when(schedulers.mainThread()).thenReturn(trampoline());
    when(schedulers.io()).thenReturn(trampoline());
    when(schedulers.computation()).thenReturn(trampoline());

    when(inAppMessageStreamManager.createFirebaseInAppMessageStream()).thenReturn(fiamStream);
    when(impressionStorageClient.storeImpression(any(CampaignImpression.class)))
        .thenReturn(fakeImpressionCompletable);
    when(campaignCacheClient.get()).thenReturn(Maybe.just(campaignsResponse));
    when(rateLimiterClient.increment(appForegroundRateLimit)).thenReturn(fakeRateLimitCompletable);

    when(firebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    when(firebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(true);
    FakeClock clock = new FakeClock(NOW);

    displayCallbacksFactory =
        new DisplayCallbacksFactory(
            impressionStorageClient,
            clock,
            schedulers,
            rateLimiterClient,
            campaignCacheClient,
            appForegroundRateLimit,
            metricsLoggerClient,
            dataCollectionHelper);
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(BANNER_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);
  }

  @Test
  public void logImpression_forVanillaCampaign_completesTask() {
    Task<Void> logActionTask = displayCallbacksImpl.impressionDetected();

    assertThat(logActionTask.isComplete()).isTrue();
  }

  @Test
  public void logImpression_forVanillaCampaign_recordsImpression() {
    displayCallbacksImpl.impressionDetected();

    assertThat(wasRecorded).isTrue();
  }

  @Test
  public void logImpression_forTestCampaign_doesRecordImpression() {
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(
            BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);
    displayCallbacksImpl.impressionDetected();

    assertThat(wasRecorded).isTrue();
  }

  @Test
  public void logImpression_forAppOpenCampaign_incrementsLimiter() {
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(BANNER_MESSAGE_MODEL, ON_FOREGROUND.name());
    displayCallbacksImpl.impressionDetected();

    assertThat(ON_FOREGROUND.name()).isEqualTo(InAppMessageStreamManager.ON_FOREGROUND);
    assertThat(wasIncremented).isTrue();
  }

  @Test
  public void logImpression_forUnknownCampaign_doesNotIncrementsLimiter() {
    BannerMessage inAppMessage =
        createBannerMessageCustomMetadata(
            new CampaignMetadata("SOME_OTHER_CAMPAIGN", "other_name", IS_NOT_TEST_MESSAGE));
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(inAppMessage, ANALYTICS_EVENT_NAME);
    displayCallbacksImpl.impressionDetected();

    assertThat(wasIncremented).isFalse();
  }

  @Test
  public void logImpression_forTestCampaign_doesNotIncrementsLimiter() {
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(
            BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);
    displayCallbacksImpl.impressionDetected();

    assertThat(wasIncremented).isFalse();
  }

  @Test
  public void logImpression_forAnalyticsEventBasedCampaign_doesNotIncrementsLimiter() {
    InAppMessage inAppMessage =
        createBannerMessageCustomMetadata(
            new CampaignMetadata(CAMPAIGN_ID2, CAMPAIGN_NAME2, IS_NOT_TEST_MESSAGE));
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(inAppMessage, ANALYTICS_EVENT_NAME);
    displayCallbacksImpl.impressionDetected();

    assertThat(wasIncremented).isFalse();
  }

  @Test
  public void logImpression_onImpressionError_notifiesError() {
    NullPointerException npe = new NullPointerException("e1");
    when(impressionStorageClient.storeImpression(any(CampaignImpression.class)))
        .thenReturn(Completable.error(npe));
    Task<Void> logActionTask = displayCallbacksImpl.impressionDetected();

    assertThat(logActionTask.getException()).isEqualTo(npe);
  }

  @Test
  public void logImpression_onLimiterError_absorbsError() {
    NullPointerException npe = new NullPointerException("e1");
    when(rateLimiterClient.increment(appForegroundRateLimit)).thenReturn(Completable.error(npe));
    Task<Void> logActionTask = displayCallbacksImpl.impressionDetected();

    assertThat(logActionTask.isComplete()).isTrue();
    assertThat(logActionTask.getException()).isNull();
    assertThat(wasRecorded).isTrue();
  }

  @Test
  public void logImpression_pipesImpressionToEngagementMetrics() {
    displayCallbacksImpl.impressionDetected();
    verify(metricsLoggerClient).logImpression(BANNER_MESSAGE_MODEL);
  }

  @Test
  public void logImpression_doesNothingIfDataCollectionIsDisabled() {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(false);
    displayCallbacksImpl.impressionDetected();

    verify(metricsLoggerClient, times(0)).logImpression(BANNER_MESSAGE_MODEL);
  }

  @Test
  public void logImpression_logsCorrectlyForTestMessage() {
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(
            BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);

    displayCallbacksImpl.impressionDetected();

    verify(metricsLoggerClient, times(1)).logImpression(BANNER_TEST_MESSAGE_MODEL);
  }

  @Test
  public void logMessageClick_pipesActionToEngagementMetrics() {
    displayCallbacksImpl.messageClicked(BANNER_MESSAGE_MODEL.getAction());
    verify(metricsLoggerClient)
        .logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logMessageClick_logsImpressionIfNotAlreadyLogged() {
    displayCallbacksImpl.messageClicked(BANNER_MESSAGE_MODEL.getAction());
    verify(metricsLoggerClient)
        .logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    verify(metricsLoggerClient, times(1)).logImpression(BANNER_MESSAGE_MODEL);
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logMessageClick_doesNotLogImpressionIfAlreadyLogged() {
    displayCallbacksImpl.impressionDetected();
    displayCallbacksImpl.messageClicked(BANNER_MESSAGE_MODEL.getAction());
    verify(metricsLoggerClient)
        .logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    verify(metricsLoggerClient, times(1)).logImpression(BANNER_MESSAGE_MODEL);
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logImpression_willSendToLoggerOneTimeImpressionIsLoggedPerMessage() {
    displayCallbacksImpl.impressionDetected();
    displayCallbacksImpl.impressionDetected();
    verify(metricsLoggerClient, times(1)).logImpression(BANNER_MESSAGE_MODEL);
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logMessageClick_doesNothingIfDataCollectionIsDisabled() {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(false);
    displayCallbacksImpl.messageClicked(BANNER_MESSAGE_MODEL.getAction());
    verify(metricsLoggerClient, times(0))
        .logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    ;
  }

  @Test
  public void logMessageClick_logsAsDismissIfActionWithoutUrlTriggered() {
    CardMessage cardMessage =
        CardMessage.builder()
            .setTitle(TITLE_MODEL)
            .setPortraitImageData(IMAGE_DATA)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setPrimaryAction(ACTION_MODEL_WITH_BUTTON)
            .setSecondaryAction(ACTION_MODEL_WITHOUT_URL)
            .build(
                new CampaignMetadata(CAMPAIGN_ID_STRING, CAMPAIGN_NAME_STRING, IS_NOT_TEST_MESSAGE),
                DATA);

    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(cardMessage, ANALYTICS_EVENT_NAME);

    displayCallbacksImpl.messageClicked(cardMessage.getSecondaryAction());
    verify(metricsLoggerClient, times(0))
        .logMessageClick(cardMessage, cardMessage.getSecondaryAction());
    verify(metricsLoggerClient, times(1)).logDismiss(cardMessage, InAppMessagingDismissType.CLICK);
  }

  @Test
  public void logMessageClick_logsAsClickIfActionWithUrlTriggered() {
    CardMessage cardMessage =
        CardMessage.builder()
            .setTitle(TITLE_MODEL)
            .setPortraitImageData(IMAGE_DATA)
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setPrimaryAction(ACTION_MODEL_WITH_BUTTON)
            .setSecondaryAction(ACTION_MODEL_WITH_BUTTON)
            .build(
                new CampaignMetadata(CAMPAIGN_ID_STRING, CAMPAIGN_NAME_STRING, IS_NOT_TEST_MESSAGE),
                DATA);

    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(cardMessage, ANALYTICS_EVENT_NAME);

    displayCallbacksImpl.messageClicked(cardMessage.getSecondaryAction());
    verify(metricsLoggerClient, times(1))
        .logMessageClick(cardMessage, cardMessage.getSecondaryAction());
    verify(metricsLoggerClient, times(0)).logDismiss(cardMessage, InAppMessagingDismissType.CLICK);
  }

  @Test
  public void logMessageClick_logsCorrectlyForTestMessage() {
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(
            BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);
    displayCallbacksImpl.messageClicked(BANNER_TEST_MESSAGE_MODEL.getAction());

    verify(metricsLoggerClient, times(1))
        .logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    ;
  }

  @Test
  public void logRenderError_pipesErrorToEngagementMetrics() {
    displayCallbacksImpl.displayErrorEncountered(InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
    verify(metricsLoggerClient)
        .logRenderError(BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logRenderError_addsImpressionToStoreButDoesntLogToEngagementMetrics() {
    displayCallbacksImpl.displayErrorEncountered(InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
    verify(metricsLoggerClient)
        .logRenderError(BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
    verify(metricsLoggerClient, never()).logImpression(BANNER_MESSAGE_MODEL);
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logRenderError_doesNothingIfDataCollectionIsDisabled() {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(false);
    displayCallbacksImpl.displayErrorEncountered(InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);

    verify(metricsLoggerClient, times(0))
        .logRenderError(BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
  }

  @Test
  public void logRenderError_logsCorrectlyForTestMessage() {
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(
            BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);
    displayCallbacksImpl.displayErrorEncountered(InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);

    verify(metricsLoggerClient, times(1))
        .logRenderError(BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
  }

  @Test
  public void logDismiss_pipesDismissToEngagementMetrics() {
    displayCallbacksImpl.messageDismissed(InAppMessagingDismissType.SWIPE);
    verify(metricsLoggerClient).logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.SWIPE);
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logDismiss_logsImpressionIfNotAlreadyImpressed() {
    displayCallbacksImpl.messageDismissed(InAppMessagingDismissType.SWIPE);
    verify(metricsLoggerClient).logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.SWIPE);
    verify(metricsLoggerClient, times(1)).logImpression(BANNER_MESSAGE_MODEL);
    assertThat(wasRecorded).isTrue(); // loggedImpression with impression store
  }

  @Test
  public void logDismiss_doesNothingIfDataCollectionIsDisabled() {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(false);
    displayCallbacksImpl.messageDismissed(InAppMessagingDismissType.SWIPE);

    verify(metricsLoggerClient, times(0))
        .logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.SWIPE);
  }

  @Test
  public void logDismiss_logsCorrectlyForTestMessage() {
    displayCallbacksImpl =
        displayCallbacksFactory.generateDisplayCallback(
            BANNER_TEST_MESSAGE_MODEL, ANALYTICS_EVENT_NAME);
    displayCallbacksImpl.messageDismissed(InAppMessagingDismissType.SWIPE);

    verify(metricsLoggerClient, times(1))
        .logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.SWIPE);
  }
}
