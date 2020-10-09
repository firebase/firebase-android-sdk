// Copyright 2018 Google LLC
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

package com.google.firebase.remoteconfig;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class FirebaseRemoteConfigIntegrationTest {
  private static final String API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String PROJECT_ID = "fake-frc-test-id";

  @Mock private ConfigCacheClient mockFetchedCache;
  @Mock private ConfigCacheClient mockActivatedCache;
  @Mock private ConfigCacheClient mockDefaultsCache;
  @Mock private ConfigFetchHandler mockFetchHandler;
  @Mock private ConfigGetParameterHandler mockGetHandler;
  @Mock private ConfigMetadataClient metadataClient;

  @Mock private ConfigCacheClient mockFireperfFetchedCache;
  @Mock private ConfigCacheClient mockFireperfActivatedCache;

  @Mock private FirebaseABTesting mockFirebaseAbt;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  private FirebaseRemoteConfig frc;

  // We use a HashMap so that Mocking is easier.
  private static final HashMap<String, String> DEFAULTS_MAP = Maps.newHashMap();

  @Before
  public void setUp() {
    DEFAULTS_MAP.put("first_default_key", "first_default_value");
    DEFAULTS_MAP.put("second_default_key", "second_default_value");
    DEFAULTS_MAP.put("third_default_key", "third_default_value");

    MockitoAnnotations.initMocks(this);
    Executor directExecutor = MoreExecutors.directExecutor();

    Context context = getInstrumentation().getTargetContext();
    FirebaseApp.clearInstancesForTest();
    FirebaseApp firebaseApp =
        FirebaseApp.initializeApp(
            context,
            new FirebaseOptions.Builder()
                .setApiKey(API_KEY)
                .setApplicationId(APP_ID)
                .setProjectId(PROJECT_ID)
                .build());

    // Catch all to avoid NPEs (the getters should never return null).
    when(mockFetchedCache.get()).thenReturn(Tasks.forResult(null));
    when(mockActivatedCache.get()).thenReturn(Tasks.forResult(null));
    when(mockFireperfFetchedCache.get()).thenReturn(Tasks.forResult(null));
    when(mockFireperfActivatedCache.get()).thenReturn(Tasks.forResult(null));

    frc =
        new FirebaseRemoteConfig(
            context,
            firebaseApp,
            mockFirebaseInstallations,
            mockFirebaseAbt,
            directExecutor,
            mockFetchedCache,
            mockActivatedCache,
            mockDefaultsCache,
            mockFetchHandler,
            mockGetHandler,
            metadataClient);
  }

  @Test
  public void setDefaultsAsync_goodXml_setsDefaults() throws Exception {
    ConfigContainer goodDefaultsXmlContainer = newDefaultsContainer(DEFAULTS_MAP);
    cachePutReturnsConfig(mockDefaultsCache, goodDefaultsXmlContainer);

    Task<Void> task = frc.setDefaultsAsync(getResourceId("frc_good_defaults"));
    Tasks.await(task);

    // Assert defaults were set correctly.
    ArgumentCaptor<ConfigContainer> captor = ArgumentCaptor.forClass(ConfigContainer.class);
    verify(mockDefaultsCache).put(captor.capture());
    assertThat(captor.getValue()).isEqualTo(goodDefaultsXmlContainer);
  }

  @Test
  public void setDefaultsAsync_emptyXml_setsEmptyDefaults() throws Exception {
    ConfigContainer emptyDefaultsXmlContainer = newDefaultsContainer(ImmutableMap.of());
    cachePutReturnsConfig(mockDefaultsCache, emptyDefaultsXmlContainer);

    Task<Void> task = frc.setDefaultsAsync(getResourceId("frc_empty_defaults"));
    Tasks.await(task);

    ArgumentCaptor<ConfigContainer> captor = ArgumentCaptor.forClass(ConfigContainer.class);
    verify(mockDefaultsCache).put(captor.capture());
    assertThat(captor.getValue()).isEqualTo(emptyDefaultsXmlContainer);
  }

  @Test
  public void setDefaultsAsync_badXml_ignoresBadEntries() throws Exception {
    ConfigContainer badDefaultsXmlContainer =
        newDefaultsContainer(ImmutableMap.of("second_default_key", "second_default_value"));
    cachePutReturnsConfig(mockDefaultsCache, badDefaultsXmlContainer);

    Task<Void> task = frc.setDefaultsAsync(getResourceId("frc_bad_defaults"));
    Tasks.await(task);

    ArgumentCaptor<ConfigContainer> captor = ArgumentCaptor.forClass(ConfigContainer.class);
    verify(mockDefaultsCache).put(captor.capture());
    assertThat(captor.getValue()).isEqualTo(badDefaultsXmlContainer);
  }

  private static void cachePutReturnsConfig(
      ConfigCacheClient cacheClient, ConfigContainer container) {
    when(cacheClient.put(container)).thenReturn(Tasks.forResult(container));
  }

  private static ConfigContainer newDefaultsContainer(Map<String, String> configsMap)
      throws Exception {
    return ConfigContainer.newBuilder()
        .replaceConfigsWith(configsMap)
        .withFetchTime(new Date(0L))
        .build();
  }

  private static int getResourceId(String xmlResourceName) {
    Resources r = getInstrumentation().getTargetContext().getResources();
    return r.getIdentifier(
        xmlResourceName, "xml", getInstrumentation().getTargetContext().getPackageName());
  }
}
