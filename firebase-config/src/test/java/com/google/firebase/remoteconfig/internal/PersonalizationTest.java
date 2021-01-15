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
import static com.google.firebase.remoteconfig.internal.Personalization.ANALYTICS_PULL_EVENT;
import static com.google.firebase.remoteconfig.internal.Personalization.ANALYTICS_PULL_EVENT_INTERNAL;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_INDEX_LOG_KEY;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_KEY;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_VALUE;
import static com.google.firebase.remoteconfig.internal.Personalization.CHOICE_ID_LOG_KEY;
import static com.google.firebase.remoteconfig.internal.Personalization.GROUP;
import static com.google.firebase.remoteconfig.internal.Personalization.PERSONALIZATION_ID_LOG_KEY;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
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
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION),
            or(eq(ANALYTICS_PULL_EVENT), eq(ANALYTICS_PULL_EVENT_INTERNAL)),
            any(Bundle.class));

    personalization = new Personalization(mockAnalyticsConnector);

    FAKE_LOGS.clear();
  }

  @Test
  public void logArmActive_nonPersonalizationKey_notLogged() {
    personalization.logArmActive("key3", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(0))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION),
            or(eq(ANALYTICS_PULL_EVENT), eq(ANALYTICS_PULL_EVENT_INTERNAL)),
            any(Bundle.class));
    assertThat(FAKE_LOGS).isEmpty();
  }

  @Test
  public void logArmActive_singlePersonalizationKey_loggedInternallyAndExternally() {
    personalization.logArmActive("key1", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(1))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));
    verify(mockAnalyticsConnector, times(1))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION),
            eq(ANALYTICS_PULL_EVENT_INTERNAL),
            any(Bundle.class));
    assertThat(FAKE_LOGS).hasSize(2);

    Bundle logParams = new Bundle();
    logParams.putString(ARM_KEY, "key1");
    logParams.putString(ARM_VALUE, "value1");
    logParams.putString(PERSONALIZATION_ID_LOG_KEY, "p13n1");
    logParams.putInt(ARM_INDEX_LOG_KEY, 0);
    logParams.putString(GROUP, "BASELINE");
    Bundle internalLogParams = new Bundle();
    internalLogParams.putString(CHOICE_ID_LOG_KEY, "id1");
    assertThat(FAKE_LOGS)
        .comparingElementsUsing(TO_STRING)
        .containsExactly(logParams.toString(), internalLogParams.toString());
  }

  @Test
  public void logArmActive_multiplePersonalizationKeys_loggedMultiple() {
    personalization.logArmActive("key1", CONFIG_CONTAINER);
    personalization.logArmActive("key2", CONFIG_CONTAINER);
    personalization.logArmActive("key1", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(2))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));
    verify(mockAnalyticsConnector, times(2))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION),
            eq(ANALYTICS_PULL_EVENT_INTERNAL),
            any(Bundle.class));
    assertThat(FAKE_LOGS).hasSize(4);

    Bundle logParams1 = new Bundle();
    logParams1.putString(ARM_KEY, "key1");
    logParams1.putString(ARM_VALUE, "value1");
    logParams1.putString(PERSONALIZATION_ID_LOG_KEY, "p13n1");
    logParams1.putInt(ARM_INDEX_LOG_KEY, 0);
    logParams1.putString(GROUP, "BASELINE");
    Bundle logParams2 = new Bundle();
    logParams2.putString(ARM_KEY, "key2");
    logParams2.putString(ARM_VALUE, "value2");
    logParams2.putString(PERSONALIZATION_ID_LOG_KEY, "p13n2");
    logParams2.putInt(ARM_INDEX_LOG_KEY, 1);
    logParams2.putString(GROUP, "P13N");
    assertThat(FAKE_LOGS)
        .comparingElementsUsing(TO_STRING)
        .containsAtLeast(logParams1.toString(), logParams2.toString())
        .inOrder();

    Bundle internalLogParams1 = new Bundle();
    internalLogParams1.putString(CHOICE_ID_LOG_KEY, "id1");
    Bundle internalLogParams2 = new Bundle();
    internalLogParams2.putString(CHOICE_ID_LOG_KEY, "id2");
    assertThat(FAKE_LOGS)
        .comparingElementsUsing(TO_STRING)
        .containsAtLeast(internalLogParams1.toString(), internalLogParams2.toString())
        .inOrder();
  }
}
