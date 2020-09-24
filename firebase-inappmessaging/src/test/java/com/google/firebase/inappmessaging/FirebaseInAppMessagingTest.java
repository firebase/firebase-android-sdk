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

package com.google.firebase.inappmessaging;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.inappmessaging.CommonTypesProto.Trigger.ON_FOREGROUND;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_ID_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_NAME_STRING;
import static io.reactivex.BackpressureStrategy.BUFFER;
import static io.reactivex.schedulers.Schedulers.trampoline;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.inappmessaging.CommonTypesProto.Event;
import com.google.firebase.inappmessaging.CommonTypesProto.Priority;
import com.google.firebase.inappmessaging.CommonTypesProto.TriggeringCondition;
import com.google.firebase.inappmessaging.MessagesProto.Content;
import com.google.firebase.inappmessaging.internal.CampaignCacheClient;
import com.google.firebase.inappmessaging.internal.DataCollectionHelper;
import com.google.firebase.inappmessaging.internal.DeveloperListenerManager;
import com.google.firebase.inappmessaging.internal.DisplayCallbacksFactory;
import com.google.firebase.inappmessaging.internal.InAppMessageStreamManager;
import com.google.firebase.inappmessaging.internal.InstallationIdResult;
import com.google.firebase.inappmessaging.internal.ProgramaticContextualTriggers;
import com.google.firebase.inappmessaging.internal.RateLimiterClient;
import com.google.firebase.inappmessaging.internal.Schedulers;
import com.google.firebase.inappmessaging.model.TriggeredInAppMessage;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.VanillaCampaignPayload;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Maybe;
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
public class FirebaseInAppMessagingTest {
  private static final long PAST = 1000000;
  private static final long NOW = PAST + 100000;
  private static final long FUTURE = NOW + 1000000;
  private static final String INSTALLATION_ID = "instance_id";
  private static final String INSTALLATION_TOKEN = "instance_token";
  private static final String CAMPAIGN_ID2 = "campaign_id2";
  private static final String CAMPAIGN_NAME2 = "campaign_name2";
  private static final String ANALYTICS_EVENT_NAME = "event1";

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

  @Mock private FirebaseInstallationsApi firebaseInstallations;
  @Mock private Schedulers schedulers;
  @Mock private InAppMessageStreamManager inAppMessageStreamManager;
  @Mock private FirebaseInAppMessagingDisplay firebaseInAppMessagingDisplay;
  @Mock private DataCollectionHelper dataCollectionHelper;
  @Mock private DisplayCallbacksFactory displayCallbacksFactory;
  @Mock private FirebaseInAppMessagingDisplayCallbacks displayCallbacks;
  @Mock private ProgramaticContextualTriggers programaticContextualTriggers;
  @Mock DeveloperListenerManager developerListenerManager = new DeveloperListenerManager();
  FirebaseApp firebaseApp1;
  FirebaseOptions options;

  private Application application;

  @Mock private RateLimiterClient rateLimiterClient;
  @Mock private CampaignCacheClient campaignCacheClient;
  private FirebaseInAppMessaging firebaseInAppMessaging;
  private FlowableEmitter<TriggeredInAppMessage> emitter;
  private final Flowable<TriggeredInAppMessage> fiamStream =
      Flowable.create(e -> emitter = e, BUFFER);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

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

    when(campaignCacheClient.get()).thenReturn(Maybe.just(campaignsResponse));

    when(firebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    when(firebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));

    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(true);

    when(displayCallbacksFactory.generateDisplayCallback(
            BANNER_MESSAGE_MODEL, ANALYTICS_EVENT_NAME))
        .thenReturn(displayCallbacks);

    firebaseInAppMessaging =
        new FirebaseInAppMessaging(
            inAppMessageStreamManager,
            programaticContextualTriggers,
            dataCollectionHelper,
            firebaseInstallations,
            displayCallbacksFactory,
            developerListenerManager);
  }

  @Test
  public void setDisplayComponent_setsComponent() {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(true);
    when(displayCallbacksFactory.generateDisplayCallback(
            BANNER_MESSAGE_MODEL, ON_FOREGROUND.name()))
        .thenReturn(displayCallbacks);

    firebaseInAppMessaging.setMessageDisplayComponent(firebaseInAppMessagingDisplay);
    emitter.onNext(new TriggeredInAppMessage(BANNER_MESSAGE_MODEL, ON_FOREGROUND.name()));

    verify(firebaseInAppMessagingDisplay).displayMessage(BANNER_MESSAGE_MODEL, displayCallbacks);
  }

  @Test
  public void clearDisplayListener_removesListener() {
    when(dataCollectionHelper.isAutomaticDataCollectionEnabled()).thenReturn(true);
    firebaseInAppMessaging.setMessageDisplayComponent(firebaseInAppMessagingDisplay);
    firebaseInAppMessaging.clearDisplayListener();
    emitter.onNext(new TriggeredInAppMessage(BANNER_MESSAGE_MODEL, ON_FOREGROUND.name()));

    verify(firebaseInAppMessagingDisplay, times(0))
        .displayMessage(
            BANNER_MESSAGE_MODEL,
            displayCallbacksFactory.generateDisplayCallback(
                BANNER_MESSAGE_MODEL, ON_FOREGROUND.name()));
  }

  @Test
  public void automaticDataCollectionEnabling_enablesInDataCollectionHelper() {
    firebaseInAppMessaging.setAutomaticDataCollectionEnabled(Boolean.TRUE);
    verify(dataCollectionHelper).setAutomaticDataCollectionEnabled(Boolean.TRUE);
    assertThat(firebaseInAppMessaging.isAutomaticDataCollectionEnabled()).isTrue();
  }

  @Test
  public void automaticDataCollectionDisabling_disablesInDataCollectionHelper() {
    firebaseInAppMessaging.setAutomaticDataCollectionEnabled(Boolean.FALSE);
    verify(dataCollectionHelper).setAutomaticDataCollectionEnabled(Boolean.FALSE);
    assertThat(firebaseInAppMessaging.isAutomaticDataCollectionEnabled()).isTrue();
  }

  @Test
  public void automaticDataCollectionDisabling_clearsInDataCollectionHelper() {
    firebaseInAppMessaging.setAutomaticDataCollectionEnabled(null);
    verify(dataCollectionHelper).setAutomaticDataCollectionEnabled(null);
  }

  @Test
  public void messagesSuppressed_isFalseOnInitialization() {
    assertThat(firebaseInAppMessaging.areMessagesSuppressed()).isFalse();
  }

  @Test
  public void messagesSuppressed_isTrueWhenUpdated() {
    firebaseInAppMessaging.setMessagesSuppressed(true);
    assertThat(firebaseInAppMessaging.areMessagesSuppressed()).isTrue();
    firebaseInAppMessaging.setMessagesSuppressed(false);
    assertThat(firebaseInAppMessaging.areMessagesSuppressed()).isFalse();
  }

  @Test
  public void addClickListener_forwardsEventListenerRequestsToDeveloperListenerManager() {
    firebaseInAppMessaging.addClickListener((inAppMessage, action) -> {});
    verify(developerListenerManager, times(1)).addClickListener(any());
  }

  @Test
  public void addDismissListener_forwardsEventListenerRequestsToDeveloperListenerManager() {
    firebaseInAppMessaging.addDismissListener(inAppMessage -> {});
    verify(developerListenerManager, times(1)).addDismissListener(any());
  }

  @Test
  public void addImpressionListener_forwardsEventListenerRequestsToDeveloperListenerManager() {
    firebaseInAppMessaging.addImpressionListener(inAppMessage -> {});
    verify(developerListenerManager, times(1)).addImpressionListener(any());
  }

  @Test
  public void addDisplayErrorListener_forwardsEventListenerRequestsToDeveloperListenerManager() {
    firebaseInAppMessaging.addDisplayErrorListener((inAppMessage, error) -> {});
    verify(developerListenerManager, times(1)).addDisplayErrorListener(any());
  }
}
