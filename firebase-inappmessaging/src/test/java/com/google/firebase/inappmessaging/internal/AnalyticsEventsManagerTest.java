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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.util.Log;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.inappmessaging.CommonTypesProto.Event;
import com.google.firebase.inappmessaging.CommonTypesProto.Priority;
import com.google.firebase.inappmessaging.CommonTypesProto.TriggeringCondition;
import com.google.firebase.inappmessaging.MessagesProto.BannerMessage;
import com.google.firebase.inappmessaging.MessagesProto.Content;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.VanillaCampaignPayload;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.junit.rules.ExpectedLogMessagesRule;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 22, qualifiers = "es")
public class AnalyticsEventsManagerTest {

  @Rule public final ExpectedLogMessagesRule logged = new ExpectedLogMessagesRule();

  private static final long PAST = 1000000;
  private static final long NOW = PAST + 100000;
  private static final long FUTURE = NOW + 1000000;
  private static final String CAMPAIGN_ID1 = "campaign_id1";
  private static final String CAMPAIGN_NAME1 = "campaign_name1";
  private static final String CAMPAIGN_ID2 = "campaign_id2";
  private static final String CAMPAIGN_NAME2 = "campaign_name2";
  private static final String ANALYTICS_EVENT_1 = "event1";

  private static final TriggeringCondition.Builder onAnalyticsEvent =
      TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(ANALYTICS_EVENT_1));
  private static final TriggeringCondition onForeground =
      TriggeringCondition.newBuilder().setFiamTrigger(ON_FOREGROUND).build();

  private static final Priority PRIORITY_TWO = Priority.newBuilder().setValue(2).build();
  private static final VanillaCampaignPayload.Builder vanillaCampaign1 =
      VanillaCampaignPayload.newBuilder()
          .setCampaignId(CAMPAIGN_ID1)
          .setCampaignName(CAMPAIGN_NAME1)
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
          .setContent(Content.newBuilder().setBanner(BannerMessage.getDefaultInstance()));
  private static final ThickContent.Builder ANALYTICS_EVENT_THICK_CONTENT_BUILDER =
      ThickContent.newBuilder()
          .setPriority(PRIORITY_TWO)
          .addTriggeringConditions(onAnalyticsEvent)
          .setVanillaPayload(vanillaCampaign2)
          .setContent(Content.getDefaultInstance());

  @Mock private AnalyticsConnector analyticsConnector;
  @Mock private AnalyticsConnector.AnalyticsConnectorHandle handle;
  private AnalyticsEventsManager eventsManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(analyticsConnector.registerAnalyticsConnectorListener(
            eq(AnalyticsConstants.ORIGIN_FIAM), any(FiamAnalyticsConnectorListener.class)))
        .thenReturn(handle);
    eventsManager = new AnalyticsEventsManager(analyticsConnector);
  }

  @Test
  public void constuctor_ok() throws Exception {
    // Verifies that the 'subscribe' method is called via the flowable.connect().
    // Otherwise handle == null
    assertThat(eventsManager.getHandle()).isEqualTo(handle);
    assertThat(eventsManager.getAnalyticsEventsFlowable() != null).isTrue();
  }

  @Test
  public void extractAnalyticsEventNames_filtersOutFiamEvents() {
    FetchEligibleCampaignsResponse campaignsResponse =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(ANALYTICS_EVENT_THICK_CONTENT_BUILDER)
            .addMessages(FOREGROUND_THICK_CONTENT_BUILDER)
            .build();

    Set<String> eventNames = AnalyticsEventsManager.extractAnalyticsEventNames(campaignsResponse);
    assertThat(eventNames).containsExactly(ANALYTICS_EVENT_1);
  }

  @Test
  public void extractAnalyticsEventNames_addsEventsWhenResponseHasMultipleTriggers() {
    String event2 = "event2";

    TriggeringCondition.Builder onAnalyticsEvent2 =
        TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(event2));

    ThickContent.Builder event2ContentBuilder =
        ThickContent.newBuilder()
            .setPriority(PRIORITY_TWO)
            .addTriggeringConditions(onAnalyticsEvent2)
            .setVanillaPayload(vanillaCampaign2)
            .setContent(Content.getDefaultInstance());

    FetchEligibleCampaignsResponse campaignsResponse =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(ANALYTICS_EVENT_THICK_CONTENT_BUILDER)
            .addMessages(event2ContentBuilder)
            .build();

    Set<String> expectedNames = new HashSet<>();
    expectedNames.add(ANALYTICS_EVENT_1);
    expectedNames.add(event2);

    Set<String> eventNames = AnalyticsEventsManager.extractAnalyticsEventNames(campaignsResponse);
    assertThat(eventNames).containsExactlyElementsIn(expectedNames);
  }

  @Test
  public void extractAnalyticsEventNames_addsEventsWhenMultipleCampaignsHasMultipleTriggers() {
    String event2 = "event2";
    String event3 = "event3";
    String event4 = "event4";

    TriggeringCondition.Builder onAnalyticsEvent2 =
        TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(event2));
    TriggeringCondition.Builder onAnalyticsEvent3 =
        TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(event3));
    TriggeringCondition.Builder onAnalyticsEvent4 =
        TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(event4));

    ThickContent.Builder event1ContentBuilder =
        ThickContent.newBuilder()
            .setPriority(PRIORITY_TWO)
            .addTriggeringConditions(onAnalyticsEvent)
            .addTriggeringConditions(onAnalyticsEvent2)
            .addTriggeringConditions(onAnalyticsEvent3)
            .setVanillaPayload(vanillaCampaign1)
            .setContent(Content.getDefaultInstance());

    ThickContent.Builder event2ContentBuilder =
        ThickContent.newBuilder()
            .setPriority(PRIORITY_TWO)
            .addTriggeringConditions(onAnalyticsEvent3)
            .addTriggeringConditions(onAnalyticsEvent4)
            .setVanillaPayload(vanillaCampaign2)
            .setContent(Content.getDefaultInstance());

    FetchEligibleCampaignsResponse campaignsResponse =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(event1ContentBuilder)
            .addMessages(event2ContentBuilder)
            .build();
    Set<String> eventNames = AnalyticsEventsManager.extractAnalyticsEventNames(campaignsResponse);
    assertThat(eventNames).containsExactly(ANALYTICS_EVENT_1, event2, event3, event4);
  }

  @Test
  public void updateContextualTriggers_logsWhenMoreThan50Events() {

    ThickContent.Builder contentBuilder =
        ThickContent.newBuilder()
            .setPriority(PRIORITY_TWO)
            .setVanillaPayload(vanillaCampaign1)
            .setContent(Content.getDefaultInstance());
    Set<String> expectedNames = new HashSet<>(51);
    for (int i = 0; i < 51; i++) {

      TriggeringCondition.Builder onAnalyticsEvent =
          TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(String.valueOf(i)));
      contentBuilder.addTriggeringConditions(onAnalyticsEvent);
      expectedNames.add(String.valueOf(i));
    }

    FetchEligibleCampaignsResponse campaignsResponse =
        FetchEligibleCampaignsResponse.newBuilder()
            .setExpirationEpochTimestampMillis(FUTURE)
            .addMessages(contentBuilder)
            .build();
    Set<String> eventNames = AnalyticsEventsManager.extractAnalyticsEventNames(campaignsResponse);
    assertThat(eventNames).containsExactlyElementsIn(expectedNames);
    logged.expectLogMessage(
        Log.INFO, Logging.TAG, AnalyticsEventsManager.TOO_MANY_CONTEXTUAL_TRIGGERS_ERROR);
  }
}
