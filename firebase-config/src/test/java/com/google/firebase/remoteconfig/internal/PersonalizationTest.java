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
import static com.google.firebase.remoteconfig.internal.ConfigContainer.CONFIGS_KEY;
import static com.google.firebase.remoteconfig.internal.ConfigContainer.FETCH_TIME_KEY;
import static com.google.firebase.remoteconfig.internal.ConfigContainer.PERSONALIZATION_METADATA_KEY;
import static com.google.firebase.remoteconfig.internal.Personalization.ANALYTICS_ORIGIN_PERSONALIZATION;
import static com.google.firebase.remoteconfig.internal.Personalization.ANALYTICS_PULL_EVENT;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_KEY;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_VALUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.os.Bundle;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
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
  private static final JSONObject CONFIG_CONTAINER = new JSONObject();

  private static final List<Bundle> FAKE_LOGS = new ArrayList<>();

  private Personalization personalization;

  @Mock private AnalyticsConnector mockAnalyticsConnector;

  @Before
  public void setUp() throws Exception {
    initMocks(this);

    CONFIG_CONTAINER.put(
        CONFIGS_KEY, new JSONObject("{key1: 'value1', key2: 'value2', key3: 'value3'}"));
    CONFIG_CONTAINER.put(FETCH_TIME_KEY, 1);
    CONFIG_CONTAINER.put(
        PERSONALIZATION_METADATA_KEY,
        new JSONArray(
            "[{parameterKey: 'key1', personalizationId: 'id1'}, "
                + "{parameterKey: 'key2', personalizationId: 'id2'}]"));

    doAnswer(invocation -> FAKE_LOGS.add(invocation.getArgument(2)))
        .when(mockAnalyticsConnector)
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));

    personalization = new Personalization(mockAnalyticsConnector);
  }

  @Test
  public void logArmActive_nonPersonalizationKey_notLogged() throws Exception {
    FAKE_LOGS.clear();

    personalization.logArmActive("key3", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(0))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));
    assertThat(FAKE_LOGS).isEmpty();
  }

  @Test
  public void logArmActive_singlePersonalizationKey_loggedOnce() throws Exception {
    FAKE_LOGS.clear();

    personalization.logArmActive("key1", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(1))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));
    assertThat(FAKE_LOGS).hasSize(1);

    Bundle params = new Bundle();
    params.putString(ARM_KEY, "id1");
    params.putString(ARM_VALUE, "value1");
    assertThat(FAKE_LOGS.get(0).toString()).isEqualTo(params.toString());
  }

  @Test
  public void logArmActive_multiplePersonalizationKeys_loggedMultiple() throws Exception {
    FAKE_LOGS.clear();

    personalization.logArmActive("key1", CONFIG_CONTAINER);
    personalization.logArmActive("key2", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(2))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));
    assertThat(FAKE_LOGS).hasSize(2);

    Bundle params1 = new Bundle();
    params1.putString(ARM_KEY, "id1");
    params1.putString(ARM_VALUE, "value1");
    assertThat(FAKE_LOGS.get(0).toString()).isEqualTo(params1.toString());

    Bundle params2 = new Bundle();
    params2.putString(ARM_KEY, "id2");
    params2.putString(ARM_VALUE, "value2");
    assertThat(FAKE_LOGS.get(1).toString()).isEqualTo(params2.toString());
  }
}
