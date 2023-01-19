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
import static com.google.firebase.inappmessaging.EventType.CLICK_EVENT_TYPE;
import static com.google.firebase.inappmessaging.EventType.IMPRESSION_EVENT_TYPE;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_NO_ACTION_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_TEST_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_ID_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_NAME_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CARD_MESSAGE_WITHOUT_ACTIONS;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.inappmessaging.CampaignAnalytics;
import com.google.firebase.inappmessaging.DismissType;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingDismissType;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason;
import com.google.firebase.inappmessaging.RenderErrorReason;
import com.google.firebase.inappmessaging.internal.FakeAnalyticsConnector.LoggedEvent;
import com.google.firebase.inappmessaging.internal.FakeAnalyticsConnector.LoggedUserProperty;
import com.google.firebase.inappmessaging.internal.MetricsLoggerClient.EngagementMetricsLoggerInterface;
import com.google.firebase.inappmessaging.internal.time.FakeClock;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class MetricsLoggerClientTest {
  private static final long PAST = 1000000;
  private static final long NOW = PAST + 100000;

  private static final String APPLICATION_ID = "APPLICATION_ID";
  private static final String INSTALLATION_ID = "instance_id";
  private static final String INSTALLATION_TOKEN = "instance_token";
  private static final String PROJECT_NUMBER = "project_number";
  private static final long BLOCKING_EXECUTOR_TIMEOUT = 100;

  private final FirebaseOptions firebaseOptions =
      new FirebaseOptions.Builder()
          .setApplicationId(APPLICATION_ID)
          .setGcmSenderId(PROJECT_NUMBER)
          .setApiKey("apiKey")
          .setProjectId("fiam-integration-test")
          .build();

  @Mock FirebaseApp firebaseApp;
  @Captor ArgumentCaptor<byte[]> byteArrayCaptor;
  @Mock private FirebaseInstallationsApi firebaseInstallations;
  @Mock private MetricsLoggerClient metricsLoggerClient;
  @Mock private EngagementMetricsLoggerInterface engagementMetricsLoggerInterface;
  @Mock private AnalyticsConnector analyticsConnector;
  @Mock private DeveloperListenerManager developerListenerManager;
  private FakeClock clock;
  private FakeAnalyticsConnector analytics;

  @Before
  public void setup() throws NameNotFoundException {
    MockitoAnnotations.initMocks(this);
    when(firebaseApp.getName()).thenReturn("app1");
    when(firebaseApp.getOptions()).thenReturn(firebaseOptions);
    when(firebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    clock = new FakeClock(NOW);
    analytics = new FakeAnalyticsConnector();
    FakeAnalyticsConnector.resetState();
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analyticsConnector,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());
  }

  @Test
  public void logImpression_proxiesRequestToEngagementMetricsClient() throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            null,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(anyObject());
  }

  @Test
  public void logImpression_alertsImpressionListeners() throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            null,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(developerListenerManager, times(1)).impressionDetected(BANNER_MESSAGE_MODEL);
  }

  @Test
  public void logImpression_alertsImpressionListenerAndSetsConversionPropWithoutActions()
      throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analyticsConnector,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logImpression(CARD_MESSAGE_WITHOUT_ACTIONS);
    waitForBlockingExecutor();

    verify(developerListenerManager, times(1)).impressionDetected(CARD_MESSAGE_WITHOUT_ACTIONS);
    // sets conversion user prop
    verify(analyticsConnector, times(1))
        .setUserProperty(
            AnalyticsConstants.ORIGIN_FIAM,
            AnalyticsConstants.USER_PROPERTY_FIREBASE_LAST_NOTIFICATION,
            "fiam:" + CAMPAIGN_ID_STRING);
  }

  @Test
  public void logImpression_failsGracefullyWithNoAnalytics() throws InterruptedException {
    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(anyObject());
  }

  @Test
  public void logImpression_setsCampaignId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getCampaignId()).isEqualTo(CAMPAIGN_ID_STRING);
  }

  @Test
  public void logImpression_setsApplicationId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getGoogleAppId()).isEqualTo(APPLICATION_ID);
  }

  @Test
  public void logImpression_setsInstanceId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getFirebaseInstanceId()).isEqualTo(INSTALLATION_ID);
  }

  @Test
  public void logImpression_setsImpressionEventType()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getEventType()).isEqualTo(IMPRESSION_EVENT_TYPE);
  }

  @Test
  public void logImpression_setsClientTimestamp()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientTimestampMillis()).isEqualTo(NOW);
  }

  @Test
  public void logMessageClick_proxiesRequestToEngagementMetricsClient()
      throws InterruptedException {
    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(anyObject());
  }

  @Test
  public void logMessageClick_notifiesListeners() throws InterruptedException {
    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    verify(developerListenerManager, times(1))
        .messageClicked(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
  }

  @Test
  public void logMessageClick_setsCampaignId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getCampaignId()).isEqualTo(CAMPAIGN_ID_STRING);
  }

  @Test
  public void logMessageClick_setsApplicationId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getGoogleAppId()).isEqualTo(APPLICATION_ID);
  }

  @Test
  public void logMessageClick_setsInstanceId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getFirebaseInstanceId()).isEqualTo(INSTALLATION_ID);
  }

  @Test
  public void logMessageClick_setsClickEventType()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getEventType()).isEqualTo(CLICK_EVENT_TYPE);
  }

  @Test
  public void logMessageClick_setsClientTimestamp()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientTimestampMillis()).isEqualTo(NOW);
  }

  @Test
  public void logRenderError_proxiesRequestToEngagementMetricsClient() throws InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(anyObject());
  }

  @Test
  public void logRenderError_notifiesListeners() throws InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(developerListenerManager, times(1))
        .displayErrorEncountered(
            BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
  }

  @Test
  public void logRenderError_setsCampaignId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getCampaignId()).isEqualTo(CAMPAIGN_ID_STRING);
  }

  @Test
  public void logRenderError_setsApplicationId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getGoogleAppId()).isEqualTo(APPLICATION_ID);
  }

  @Test
  public void logRenderError_setsInstanceId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getFirebaseInstanceId()).isEqualTo(INSTALLATION_ID);
  }

  @Test
  public void logRenderError_withGenericRenderErrorReason_setsEquivalentErrorReason()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getRenderErrorReason())
        .isEqualTo(RenderErrorReason.UNSPECIFIED_RENDER_ERROR);
  }

  @Test
  public void logRenderError_withImageFetchRenderError_setsEquivalentErrorReason()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.IMAGE_FETCH_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getRenderErrorReason())
        .isEqualTo(RenderErrorReason.IMAGE_FETCH_ERROR);
  }

  @Test
  public void logRenderError_withImageDisplayError_setsEquivalentErrorReason()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getRenderErrorReason())
        .isEqualTo(RenderErrorReason.IMAGE_DISPLAY_ERROR);
  }

  @Test
  public void logRenderError_withUnsupportedFiam_setsEquivalentErrorReason()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.IMAGE_UNSUPPORTED_FORMAT);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getRenderErrorReason())
        .isEqualTo(RenderErrorReason.IMAGE_UNSUPPORTED_FORMAT);
  }

  @Test
  public void logRenderError_setsClientTimestamp()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logRenderError(
        BANNER_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientTimestampMillis()).isEqualTo(NOW);
  }

  @Test
  public void logDismiss_proxiesRequestToEngagementMetricsClient() throws InterruptedException {
    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(anyObject());
  }

  @Test
  public void logDismiss_notifiesListeners() throws InterruptedException {
    metricsLoggerClient.logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.CLICK);
    waitForBlockingExecutor();

    verify(developerListenerManager, times(1)).messageDismissed(BANNER_MESSAGE_MODEL);
  }

  @Test
  public void logDismiss_setsCampaignId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getCampaignId()).isEqualTo(CAMPAIGN_ID_STRING);
  }

  @Test
  public void logDismiss_setsApplicationId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getGoogleAppId()).isEqualTo(APPLICATION_ID);
  }

  @Test
  public void logDismiss_setsInstanceId()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientApp().getFirebaseInstanceId()).isEqualTo(INSTALLATION_ID);
  }

  @Test
  public void logDismiss_withUnknownDismissType_setsEquivalentDismissType()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getDismissType()).isEqualTo(DismissType.UNKNOWN_DISMISS_TYPE);
  }

  @Test
  public void logDismiss_withAutoDismissType_setsEquivalentDismissType()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.AUTO);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getDismissType()).isEqualTo(DismissType.AUTO);
  }

  @Test
  public void logDismiss_withClickDismissType_setsEquivalentDismissType()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.CLICK);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getDismissType()).isEqualTo(DismissType.CLICK);
  }

  @Test
  public void logDismiss_withSwipeDismissType_setsEquivalentDismissType()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(BANNER_MESSAGE_MODEL, InAppMessagingDismissType.SWIPE);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getDismissType()).isEqualTo(DismissType.SWIPE);
  }

  @Test
  public void logDismiss_setsClientTimestamp()
      throws InvalidProtocolBufferException, InterruptedException {
    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());

    CampaignAnalytics campaignAnalytics =
        CampaignAnalytics.parser().parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics.getClientTimestampMillis()).isEqualTo(NOW);
  }

  @Test
  public void logImpression_sendsCorrectScionEventWithParams() throws InterruptedException {
    FakeAnalyticsConnector analytics = new FakeAnalyticsConnector();
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analytics,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    LoggedEvent actual = analytics.getLoggedEvent().get(0);

    assertThat(actual.origin).isEqualTo(AnalyticsConstants.ORIGIN_FIAM);
    assertThat(actual.name).isEqualTo(AnalyticsConstants.ANALYTICS_IMPRESSION_EVENT);
    Bundle actualParams = actual.params;
    assertThat(actualParams.getString(AnalyticsConstants.PARAM_MESSAGE_ID))
        .isEqualTo(CAMPAIGN_ID_STRING);
    assertThat(actualParams.getString(AnalyticsConstants.PARAM_MESSAGE_NAME))
        .isEqualTo(CAMPAIGN_NAME_STRING);

    int epochSeconds = (int) (NOW / 1000);
    assertThat(actualParams.getInt(AnalyticsConstants.PARAM_MESSAGE_DEVICE_TIME))
        .isEqualTo(epochSeconds);
  }

  @Test
  public void logClick_sendsCorrectScionEventName() throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analytics,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    assertThat(analytics.getLoggedEvent().size()).isEqualTo(1);
    LoggedEvent actual = analytics.getLoggedEvent().get(0);

    assertThat(actual.origin).isEqualTo(AnalyticsConstants.ORIGIN_FIAM);
    assertThat(actual.name).isEqualTo(AnalyticsConstants.ANALYTICS_ACTION_EVENT);
  }

  @Test
  public void logDismisssendsCorrectScionEventName() throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analytics,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    assertThat(analytics.getLoggedEvent().size()).isEqualTo(1);
    LoggedEvent actual = analytics.getLoggedEvent().get(0);

    assertThat(actual.origin).isEqualTo(AnalyticsConstants.ORIGIN_FIAM);
    assertThat(actual.name).isEqualTo(AnalyticsConstants.ANALYTICS_ACTION_EVENT);
  }

  @Test
  public void logEvent_sendsEventParams() throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analytics,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    waitForBlockingExecutor();

    assertThat(analytics.getLoggedEvent().size()).isEqualTo(1);
    LoggedEvent actual = analytics.getLoggedEvent().get(0);

    assertThat(actual.origin).isEqualTo(AnalyticsConstants.ORIGIN_FIAM);
    assertThat(actual.name).isEqualTo(AnalyticsConstants.ANALYTICS_IMPRESSION_EVENT);
    Bundle actualParams = actual.params;
    assertThat(actualParams.getString(AnalyticsConstants.PARAM_MESSAGE_ID))
        .isEqualTo(CAMPAIGN_ID_STRING);
    assertThat(actualParams.getString(AnalyticsConstants.PARAM_MESSAGE_NAME))
        .isEqualTo(CAMPAIGN_NAME_STRING);

    int epochSeconds = (int) (NOW / 1000);
    assertThat(actualParams.getInt(AnalyticsConstants.PARAM_MESSAGE_DEVICE_TIME))
        .isEqualTo(epochSeconds);
  }

  @Test
  public void logEvent_addsConversionTrackingOnAction() throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analytics,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logImpression(BANNER_MESSAGE_MODEL);
    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    assertThat(analytics.getSetUserProperty().size()).isEqualTo(0);

    metricsLoggerClient.logMessageClick(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
    waitForBlockingExecutor();

    assertThat(analytics.getSetUserProperty().size()).isEqualTo(1);
    LoggedUserProperty actual = analytics.getSetUserProperty().get(0);

    assertThat(actual.origin).isEqualTo(AnalyticsConstants.ORIGIN_FIAM);
    assertThat(actual.name).isEqualTo(AnalyticsConstants.USER_PROPERTY_FIREBASE_LAST_NOTIFICATION);
    assertThat(actual.value).isEqualTo("fiam:" + CAMPAIGN_ID_STRING);
  }

  @Test
  public void logEvent_addsConversionTrackingOnClickWhenNoAction() throws InterruptedException {
    metricsLoggerClient =
        new MetricsLoggerClient(
            engagementMetricsLoggerInterface,
            analytics,
            firebaseApp,
            firebaseInstallations,
            clock,
            developerListenerManager,
            TestOnlyExecutors.blocking());

    metricsLoggerClient.logDismiss(
        BANNER_MESSAGE_NO_ACTION_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    waitForBlockingExecutor();

    assertThat(analytics.getSetUserProperty().size()).isEqualTo(0);

    metricsLoggerClient.logImpression(BANNER_MESSAGE_NO_ACTION_MODEL);
    waitForBlockingExecutor();

    assertThat(analytics.getSetUserProperty().size()).isEqualTo(1);
    LoggedUserProperty actual = analytics.getSetUserProperty().get(0);

    assertThat(actual.origin).isEqualTo(AnalyticsConstants.ORIGIN_FIAM);
    assertThat(actual.name).isEqualTo(AnalyticsConstants.USER_PROPERTY_FIREBASE_LAST_NOTIFICATION);
    assertThat(actual.value).isEqualTo("fiam:" + CAMPAIGN_ID_STRING);
  }

  @Test
  public void metricsLoggerClient_doesNotLogTestCampaigns() throws InterruptedException {
    metricsLoggerClient.logImpression(BANNER_TEST_MESSAGE_MODEL);
    metricsLoggerClient.logMessageClick(
        BANNER_TEST_MESSAGE_MODEL, BANNER_TEST_MESSAGE_MODEL.getAction());
    metricsLoggerClient.logDismiss(
        BANNER_TEST_MESSAGE_MODEL, InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
    metricsLoggerClient.logRenderError(
        BANNER_TEST_MESSAGE_MODEL, InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);
    waitForBlockingExecutor();

    verify(engagementMetricsLoggerInterface, never()).logEvent(anyObject());
  }

  private static void waitForBlockingExecutor() throws InterruptedException {
    TestOnlyExecutors.blocking().awaitTermination(BLOCKING_EXECUTOR_TIMEOUT, TimeUnit.MILLISECONDS);
  }
}
