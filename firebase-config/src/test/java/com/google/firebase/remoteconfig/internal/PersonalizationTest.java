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
import static com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse.forBackendUpdatesFetched;
import static com.google.firebase.remoteconfig.internal.Personalization.ANALYTICS_ORIGIN_PERSONALIZATION;
import static com.google.firebase.remoteconfig.internal.Personalization.ANALYTICS_PULL_EVENT;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_KEY;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_VALUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import android.os.Bundle;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit tests for {@link Personalization}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PersonalizationTest {
  private static final String APP_ID = "1:123:android:test";
  private static final String API_KEY = "123";
  private static final String PROJECT_ID = "testApp";

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
                      "{key1: {personalizationId: 'id1'}, key2: {personalizationId: 'id2'}}"))
              .build();
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  private static final List<Bundle> FAKE_LOGS = new ArrayList<>();

  private Personalization personalization;

  @Mock private AnalyticsConnector mockAnalyticsConnector;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private ConfigCacheClient mockFetchedCache;
  @Mock private ConfigCacheClient mockActivatedCache;
  @Mock private ConfigCacheClient mockDefaultsCache;
  @Mock private ConfigFetchHandler mockFetchHandler;
  @Mock private ConfigMetadataClient metadataClient;

  @Before
  public void setUp() {
    initMocks(this);

    doAnswer(invocation -> FAKE_LOGS.add(invocation.getArgument(2)))
        .when(mockAnalyticsConnector)
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));

    personalization = new Personalization(mockAnalyticsConnector);

    FAKE_LOGS.clear();
  }

  @Test
  public void logArmActive_nonPersonalizationKey_notLogged() {
    personalization.logArmActive("key3", CONFIG_CONTAINER);

    verify(mockAnalyticsConnector, times(0))
        .logEvent(
            eq(ANALYTICS_ORIGIN_PERSONALIZATION), eq(ANALYTICS_PULL_EVENT), any(Bundle.class));
    assertThat(FAKE_LOGS).isEmpty();
  }

  @Test
  public void logArmActive_singlePersonalizationKey_loggedOnce() {
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
  public void logArmActive_multiplePersonalizationKeys_loggedMultiple() {
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

  @Test
  public void logArmActive_RemoteConfigIntegration_successful() {
    FirebaseApp.clearInstancesForTest();
    Context context = RuntimeEnvironment.application;

    Executor executor = MoreExecutors.directExecutor();

    ConfigGetParameterHandler parameterHandler =
        new ConfigGetParameterHandler(executor, mockActivatedCache, mockDefaultsCache);
    parameterHandler.addListener(personalization::logArmActive);

    FirebaseRemoteConfig remoteConfig =
        new FirebaseRemoteConfig(
            context,
            FirebaseApp.initializeApp(
                context,
                new FirebaseOptions.Builder()
                    .setApiKey(API_KEY)
                    .setApplicationId(APP_ID)
                    .setProjectId(PROJECT_ID)
                    .build()),
            mockFirebaseInstallations,
            null,
            executor,
            mockFetchedCache,
            mockActivatedCache,
            mockDefaultsCache,
            mockFetchHandler,
            parameterHandler,
            metadataClient);

    when(mockFetchHandler.fetch())
        .thenReturn(Tasks.forResult(forBackendUpdatesFetched(CONFIG_CONTAINER, "Etag")));

    when(mockActivatedCache.getBlocking()).thenReturn(CONFIG_CONTAINER);

    remoteConfig
        .fetchAndActivate()
        .addOnCompleteListener(
            success -> {
              remoteConfig.getString("key1");
              remoteConfig.getString("key2");

              verify(mockAnalyticsConnector, times(2))
                  .logEvent(
                      eq(ANALYTICS_ORIGIN_PERSONALIZATION),
                      eq(ANALYTICS_PULL_EVENT),
                      any(Bundle.class));
              assertThat(FAKE_LOGS).hasSize(2);

              Bundle params1 = new Bundle();
              params1.putString(ARM_KEY, "id1");
              params1.putString(ARM_VALUE, "value1");
              assertThat(FAKE_LOGS.get(0).toString()).isEqualTo(params1.toString());

              Bundle params2 = new Bundle();
              params2.putString(ARM_KEY, "id2");
              params2.putString(ARM_VALUE, "value2");
              assertThat(FAKE_LOGS.get(1).toString()).isEqualTo(params2.toString());
            });
  }
}
