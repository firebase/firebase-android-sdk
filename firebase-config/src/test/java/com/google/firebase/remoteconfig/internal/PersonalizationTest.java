// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.remoteconfig.internal.Personalization.ANALYTICS_ORIGIN_PERSONALIZATION;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_ARM_INDEX_PARAM;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_ARM_VALUE_PARAM;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_EVENT;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_GROUP_PARAM;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_PERSONALIZATION_ID_PARAM;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_RC_PARAMETER_PARAM;
import static com.google.firebase.remoteconfig.internal.Personalization.INTERNAL_CHOICE_ID_PARAM;
import static com.google.firebase.remoteconfig.internal.Personalization.INTERNAL_EVENT;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.os.Bundle;
import com.google.common.truth.Correspondence;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for {@link Personalization}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PersonalizationTest {
  private static final ConfigContainer CONFIG_CONTAINER;
  private static final Bundle LOG_PARAMS_1 = new Bundle();
  private static final Bundle LOG_PARAMS_2 = new Bundle();
  private static final Bundle INTERNAL_LOG_PARAMS_1 = new Bundle();
  private static final Bundle INTERNAL_LOG_PARAMS_2 = new Bundle();

  static {
    try {
      CONFIG_CONTAINER =
          ConfigContainer.newBuilder()
              .replaceConfigsWith(
                  new JSONObject("{key1: 'value1', key2: 'value2', key3: 'value3'}"))
              .withFetchTime(new Date(1))
              .withPersonalizationMetadata(
                  new JSONObject(
                      "{key1: {personalizationId: 'p13n1', armIndex: 0,"
                          + " choiceId: 'id1', group: 'BASELINE'},"
                          + " key2: {personalizationId: 'p13n2', armIndex: 1,"
                          + " choiceId: 'id2', group: 'P13N'}}"))
              .build();
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }

    LOG_PARAMS_1.putString(EXTERNAL_RC_PARAMETER_PARAM, "key1");
    LOG_PARAMS_1.putString(EXTERNAL_ARM_VALUE_PARAM, "value1");
    LOG_PARAMS_1.putString(EXTERNAL_PERSONALIZATION_ID_PARAM, "p13n1");
    LOG_PARAMS_1.putInt(EXTERNAL_ARM_INDEX_PARAM, 0);
    LOG_PARAMS_1.putString(EXTERNAL_GROUP_PARAM, "BASELINE");

    LOG_PARAMS_2.putString(EXTERNAL_RC_PARAMETER_PARAM, "key2");
    LOG_PARAMS_2.putString(EXTERNAL_ARM_VALUE_PARAM, "value2");
    LOG_PARAMS_2.putString(EXTERNAL_PERSONALIZATION_ID_PARAM, "p13n2");
    LOG_PARAMS_2.putInt(EXTERNAL_ARM_INDEX_PARAM, 1);
    LOG_PARAMS_2.putString(EXTERNAL_GROUP_PARAM, "P13N");

    INTERNAL_LOG_PARAMS_1.putString(INTERNAL_CHOICE_ID_PARAM, "id1");

    INTERNAL_LOG_PARAMS_2.putString(INTERNAL_CHOICE_ID_PARAM, "id2");
  }

  private static final List<Bundle> FAKE_LOGS = new ArrayList<>();

  private static final Correspondence<Bundle, String> TO_STRING =
      Correspondence.transforming(Bundle::toString, "as String is");

  private Personalization personalization;

  @Mock private AnalyticsConnector mockAnalyticsConnector;

  @Before
  public void setUp() {
    initMocks(this);

    doAnswer(invocation -> FAKE_LOGS.add(invocation.getArgument(2)))
        .when(mockAnalyticsConnector)
        .logEvent(eq(ANALYTICS_ORIGIN_PERSONALIZATION), anyString(), any(Bundle.class));

    personalization = new Personalization(() -> mockAnalyticsConnector);

    FAKE_LOGS.clear();
  }

  @Test
  public void logArmActive_nonPersonalizationKey_notLogged() {
    personalization.logArmActive("key3", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(0))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION),
            or(eq(EXTERNAL_EVENT), eq(INTERNAL_EVENT)),
            any(Bundle.class));
    assertThat(FAKE_LOGS).isEmpty();
  }

  @Test
  public void logArmActive_singlePersonalizationKey_loggedInternallyAndExternally() {
    personalization.logArmActive("key1", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(1))
        .logEvent(eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(EXTERNAL_EVENT), any(Bundle.class));
    verify(mockAnalyticsConnector, times(1))
        .logEvent(eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(INTERNAL_EVENT), any(Bundle.class));
    assertThat(FAKE_LOGS).hasSize(2);

    assertThat(FAKE_LOGS)
        .comparingElementsUsing(TO_STRING)
        .containsExactly(LOG_PARAMS_1.toString(), INTERNAL_LOG_PARAMS_1.toString());
  }

  @Test
  public void logArmActive_multiplePersonalizationKeys_loggedInternallyAndExternally() {
    personalization.logArmActive("key1", CONFIG_CONTAINER);
    personalization.logArmActive("key2", CONFIG_CONTAINER);
    personalization.logArmActive("key1", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(2))
        .logEvent(eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(EXTERNAL_EVENT), any(Bundle.class));
    verify(mockAnalyticsConnector, times(2))
        .logEvent(eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(INTERNAL_EVENT), any(Bundle.class));
    assertThat(FAKE_LOGS).hasSize(4);

    assertThat(FAKE_LOGS)
        .comparingElementsUsing(TO_STRING)
        .containsAtLeast(LOG_PARAMS_1.toString(), LOG_PARAMS_2.toString())
        .inOrder();
    assertThat(FAKE_LOGS)
        .comparingElementsUsing(TO_STRING)
        .containsAtLeast(INTERNAL_LOG_PARAMS_1.toString(), INTERNAL_LOG_PARAMS_2.toString())
        .inOrder();
  }
}
