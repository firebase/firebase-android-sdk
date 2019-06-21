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

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.ACTIVATE_FILE_NAME;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.DEFAULTS_FILE_NAME;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.EXPERIMENT_ID_KEY;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.EXPERIMENT_START_TIME_KEY;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.EXPERIMENT_TIME_TO_LIVE_KEY;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.EXPERIMENT_TRIGGER_EVENT_KEY;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.EXPERIMENT_TRIGGER_TIMEOUT_KEY;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.EXPERIMENT_VARIANT_ID_KEY;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.FETCH_FILE_NAME;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.LEGACY_CONFIGS_FILE_NAME;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.LEGACY_FRC_NAMESPACE_PREFIX;
import static com.google.firebase.remoteconfig.internal.LegacyConfigsHandler.protoTimestampStringParser;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.google.common.collect.ImmutableList;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.ConfigHolder;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.KeyValue;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.NamespaceKeyValue;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.PersistedConfig;
import com.google.protobuf.ByteString;
import developers.mobile.abt.FirebaseAbt.ExperimentPayload;
import java.io.FileOutputStream;
import java.sql.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link LegacyConfigsHandler}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class LegacyConfigsHandlerTest {
  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";

  private static final String FIRST_EXPERIMENT_ID = "55555";
  private static final String SECOND_EXPERIMENT_ID = "22";

  private static final String FIREBASE_NAMESPACE = "firebase";
  private static final String FIREPERF_NAMESPACE = "fireperf";
  private static final String SOVIET_NAMESPACE = "soviet";
  private static final String[] ALL_NAMESPACES = {
    FIREBASE_NAMESPACE, FIREPERF_NAMESPACE, SOVIET_NAMESPACE
  };
  private static final String[] ALL_FILE_NAMES = {
    ACTIVATE_FILE_NAME, FETCH_FILE_NAME, DEFAULTS_FILE_NAME
  };

  private static final String KEY1 = "key1";
  private static final String KEY2 = "key2";
  private static final String SOVIET_ACTIVE_VALUE1 = "soviet_activate_value1";
  private static final String SOVIET_ACTIVE_VALUE2 = "2.0";
  private static final String FIREBASE_FETCHED_VALUE1 = "firebase_fetched_value1";
  private static final String FIREPERF_FETCHED_VALUE1 = "true";
  private static final String SOVIET_FETCHED_VALUE1 = "soviet_fetched_value1";
  private static final String FIREBASE_DEFAULTS_VALUE1 = "50000000000";
  private static final String FIREBASE_DEFAULTS_VALUE2 = "firebase_defaults_value2";

  private static final Date ACTIVATED_TIMESTAMP = new Date(SECONDS.toMillis(20L));
  private static final Date FETCHED_TIMESTAMP = new Date(SECONDS.toMillis(200L));
  private static final Date DEFAULTS_TIMESTAMP = new Date(0L);

  private Context context;
  private LegacyConfigsHandler legacyConfigsHandler;

  private PersistedConfig legacyConfigs;

  /** Used for comparing lists of experiments where order may not match. */
  private Map<String, ExperimentPayload> expectedAbtExperiments;

  private Map<String, Map<String, ConfigCacheClient>> allCacheClientsMaps;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.application;

    expectedAbtExperiments = new HashMap<>();

    ExperimentPayload firstExperiment =
        newExperiment(
            FIRST_EXPERIMENT_ID,
            "1",
            DAYS.toMillis(100L),
            "trigger1",
            MINUTES.toMillis(10L),
            MINUTES.toMillis(100L));
    expectedAbtExperiments.put(FIRST_EXPERIMENT_ID, firstExperiment);

    ExperimentPayload secondExperiment =
        newExperiment(
            SECOND_EXPERIMENT_ID,
            "3",
            DAYS.toMillis(1000L),
            "trigger2",
            MINUTES.toMillis(20L),
            MINUTES.toMillis(200L));
    expectedAbtExperiments.put(SECOND_EXPERIMENT_ID, secondExperiment);

    legacyConfigs =
        newConfigs(
            /*activeHolder=*/ newHolder(
                ACTIVATED_TIMESTAMP.getTime(),
                ImmutableList.of(firstExperiment),
                newNamespace(
                    LEGACY_FRC_NAMESPACE_PREFIX + SOVIET_NAMESPACE,
                    newKeyValue(KEY1, SOVIET_ACTIVE_VALUE1),
                    newKeyValue(KEY2, SOVIET_ACTIVE_VALUE2))),
            /*fetchedHolder=*/ newHolder(
                FETCHED_TIMESTAMP.getTime(),
                ImmutableList.of(secondExperiment, firstExperiment),
                newNamespace(
                    LEGACY_FRC_NAMESPACE_PREFIX + FIREBASE_NAMESPACE,
                    newKeyValue(KEY1, FIREBASE_FETCHED_VALUE1)),
                newNamespace(
                    LEGACY_FRC_NAMESPACE_PREFIX + FIREPERF_NAMESPACE,
                    newKeyValue(KEY1, FIREPERF_FETCHED_VALUE1)),
                newNamespace(
                    LEGACY_FRC_NAMESPACE_PREFIX + SOVIET_NAMESPACE,
                    newKeyValue(KEY1, SOVIET_FETCHED_VALUE1))),
            /*defaultsHolder=*/ newHolder(
                DEFAULTS_TIMESTAMP.getTime(),
                ImmutableList.of(),
                newNamespace(
                    LEGACY_FRC_NAMESPACE_PREFIX + FIREBASE_NAMESPACE,
                    newKeyValue(KEY1, FIREBASE_DEFAULTS_VALUE1),
                    newKeyValue(KEY2, FIREBASE_DEFAULTS_VALUE2))));

    legacyConfigsHandler = spy(new LegacyConfigsHandler(context, APP_ID));

    allCacheClientsMaps = new HashMap<>();
    for (String namespace : ALL_NAMESPACES) {
      Map<String, ConfigCacheClient> namespaceCacheClients = new HashMap<>();
      for (String fileName : ALL_FILE_NAMES) {
        ConfigCacheClient mockCacheClient = mock(ConfigCacheClient.class);
        namespaceCacheClients.put(fileName, mockCacheClient);
        when(legacyConfigsHandler.getCacheClient(namespace, fileName)).thenReturn(mockCacheClient);
      }
      allCacheClientsMaps.put(namespace, namespaceCacheClients);
    }
  }

  @Test
  public void saveLegacyConfigsIfNecessary_hasNoLegacyConfigs_doesNotWriteToCacheClients() {
    assertWithMessage("Did not save legacy configs!")
        .that(legacyConfigsHandler.saveLegacyConfigsIfNecessary())
        .isTrue();

    verifyNoWritesToAnyClient();
  }

  @Test
  public void saveLegacyConfigsIfNecessary_hasLegacyConfigs_writesConfigsToCacheClients()
      throws Exception {
    writeTestDataToLegacyConfigsFile();

    assertWithMessage("Did not save legacy configs!")
        .that(legacyConfigsHandler.saveLegacyConfigsIfNecessary())
        .isTrue();

    validateFirebaseNamespaceConfigs();
    validateSovietNamespaceConfigs();
    validateFireperfNamespaceConfigs();
  }

  @Test
  public void saveLegacyConfigsIfNecessary_calledMultipleTimes_onlyFirstCallWritesToClients()
      throws Exception {
    writeTestDataToLegacyConfigsFile();

    assertWithMessage("Did not save legacy configs!")
        .that(legacyConfigsHandler.saveLegacyConfigsIfNecessary())
        .isTrue();

    assertWithMessage("Saved legacy configs on second call!")
        .that(legacyConfigsHandler.saveLegacyConfigsIfNecessary())
        .isFalse();
    assertWithMessage("Saved legacy configs on third call!")
        .that(legacyConfigsHandler.saveLegacyConfigsIfNecessary())
        .isFalse();
  }

  private void validateFirebaseNamespaceConfigs() throws Exception {
    String namespace = FIREBASE_NAMESPACE;

    verifyNoWriteToClient(namespace, ACTIVATE_FILE_NAME);

    ConfigContainer fetchedConfigsContainer = getCapturedContainer(namespace, FETCH_FILE_NAME);
    JSONObject fetchedConfigs = fetchedConfigsContainer.getConfigs();
    assertThat(fetchedConfigs.length()).isEqualTo(1);
    assertThat(fetchedConfigs.getString(KEY1)).isEqualTo(FIREBASE_FETCHED_VALUE1);
    assertThat(fetchedConfigsContainer.getFetchTime()).isEqualTo(FETCHED_TIMESTAMP);
    validateExperiments(
        fetchedConfigsContainer.getAbtExperiments(), FIRST_EXPERIMENT_ID, SECOND_EXPERIMENT_ID);

    ConfigContainer defaultsConfigsContainer = getCapturedContainer(namespace, DEFAULTS_FILE_NAME);
    JSONObject defaultsConfigs = defaultsConfigsContainer.getConfigs();
    assertThat(defaultsConfigs.length()).isEqualTo(2);
    assertThat(defaultsConfigs.getLong(KEY1)).isEqualTo(Long.valueOf(FIREBASE_DEFAULTS_VALUE1));
    assertThat(defaultsConfigs.getString(KEY2)).isEqualTo(FIREBASE_DEFAULTS_VALUE2);
    assertThat(defaultsConfigsContainer.getFetchTime()).isEqualTo(DEFAULTS_TIMESTAMP);
    validateExperiments(defaultsConfigsContainer.getAbtExperiments());
  }

  private void validateSovietNamespaceConfigs() throws Exception {
    String namespace = SOVIET_NAMESPACE;

    ConfigContainer activatedConfigsContainer = getCapturedContainer(namespace, ACTIVATE_FILE_NAME);
    JSONObject activatedConfigs = activatedConfigsContainer.getConfigs();
    assertThat(activatedConfigs.length()).isEqualTo(2);
    assertThat(activatedConfigs.getString(KEY1)).isEqualTo(SOVIET_ACTIVE_VALUE1);
    assertThat(activatedConfigs.getDouble(KEY2)).isEqualTo(Double.valueOf(SOVIET_ACTIVE_VALUE2));
    assertThat(activatedConfigsContainer.getFetchTime()).isEqualTo(ACTIVATED_TIMESTAMP);
    assertThat(activatedConfigsContainer.getAbtExperiments().length()).isEqualTo(0);

    ConfigContainer fetchedConfigsContainer = getCapturedContainer(namespace, FETCH_FILE_NAME);
    JSONObject fetchedConfigs = fetchedConfigsContainer.getConfigs();
    assertThat(fetchedConfigs.length()).isEqualTo(1);
    assertThat(fetchedConfigs.getString(KEY1)).isEqualTo(SOVIET_FETCHED_VALUE1);
    assertThat(fetchedConfigsContainer.getFetchTime()).isEqualTo(FETCHED_TIMESTAMP);
    assertThat(fetchedConfigsContainer.getAbtExperiments().length()).isEqualTo(0);

    verifyNoWriteToClient(namespace, DEFAULTS_FILE_NAME);
  }

  private void validateFireperfNamespaceConfigs() throws Exception {
    String namespace = FIREPERF_NAMESPACE;

    verifyNoWriteToClient(namespace, ACTIVATE_FILE_NAME);

    ConfigContainer fetchedConfigsContainer = getCapturedContainer(namespace, FETCH_FILE_NAME);
    JSONObject fetchedConfigs = fetchedConfigsContainer.getConfigs();
    assertThat(fetchedConfigs.length()).isEqualTo(1);

    assertThat(fetchedConfigs.getBoolean(KEY1)).isEqualTo(Boolean.valueOf(FIREPERF_FETCHED_VALUE1));
    assertThat(fetchedConfigsContainer.getFetchTime()).isEqualTo(FETCHED_TIMESTAMP);
    assertThat(fetchedConfigsContainer.getAbtExperiments().length()).isEqualTo(0);

    verifyNoWriteToClient(namespace, DEFAULTS_FILE_NAME);
  }

  private void validateExperiments(JSONArray abtExperiments, String... expectedExperimentIds)
      throws Exception {
    int numExpectedExperiments = expectedExperimentIds.length;
    assertThat(abtExperiments.length()).isEqualTo(numExpectedExperiments);

    Set<String> visitedExperimentIds = new HashSet<>();
    for (int experimentIndex = 0; experimentIndex < numExpectedExperiments; experimentIndex++) {
      JSONObject abtExperiment = abtExperiments.getJSONObject(experimentIndex);
      String experimentId = abtExperiment.getString(EXPERIMENT_ID_KEY);

      assertThatExperimentsAreEqual(abtExperiment, expectedAbtExperiments.get(experimentId));
      visitedExperimentIds.add(experimentId);
    }
    assertThat(visitedExperimentIds).containsAtLeastElementsIn(expectedExperimentIds);
  }

  private ConfigContainer getCapturedContainer(String namespace, String fileName) {
    ConfigCacheClient mockCacheClient = allCacheClientsMaps.get(namespace).get(fileName);
    ArgumentCaptor<ConfigContainer> containerCaptor =
        ArgumentCaptor.forClass(ConfigContainer.class);
    verify(mockCacheClient).put(containerCaptor.capture());
    return containerCaptor.getValue();
  }

  private void verifyNoWriteToClient(String namespace, String fileName) {
    verify(allCacheClientsMaps.get(namespace).get(fileName), never()).put(any());
  }

  private void verifyNoWritesToAnyClient() {
    for (String namespace : ALL_NAMESPACES) {
      for (String fileName : ALL_FILE_NAMES) {
        verifyNoWriteToClient(namespace, fileName);
      }
    }
  }

  private void assertThatExperimentsAreEqual(
      JSONObject abtExperiment, ExperimentPayload expectedExperiment) throws Exception {
    assertThat(abtExperiment.getString(EXPERIMENT_ID_KEY))
        .isEqualTo(expectedExperiment.getExperimentId());
    assertThat(abtExperiment.getString(EXPERIMENT_VARIANT_ID_KEY))
        .isEqualTo(expectedExperiment.getVariantId());
    assertThat(
            protoTimestampStringParser
                .get()
                .parse(abtExperiment.getString(EXPERIMENT_START_TIME_KEY))
                .getTime())
        .isEqualTo(expectedExperiment.getExperimentStartTimeMillis());
    assertThat(abtExperiment.getString(EXPERIMENT_TRIGGER_EVENT_KEY))
        .isEqualTo(expectedExperiment.getTriggerEvent());
    assertThat(abtExperiment.getLong(EXPERIMENT_TRIGGER_TIMEOUT_KEY))
        .isEqualTo(expectedExperiment.getTriggerTimeoutMillis());
    assertThat(abtExperiment.getLong(EXPERIMENT_TIME_TO_LIVE_KEY))
        .isEqualTo(expectedExperiment.getTimeToLiveMillis());
  }

  private ExperimentPayload newExperiment(
      String experimentId,
      String variantId,
      long startTimeInMillis,
      String triggerEvent,
      long triggerTimeoutInMillis,
      long timeToLiveInMillis) {
    ExperimentPayload.Builder secondExperimentBuilder = ExperimentPayload.newBuilder();
    secondExperimentBuilder.setExperimentId(experimentId);
    secondExperimentBuilder.setVariantId(variantId);
    secondExperimentBuilder.setExperimentStartTimeMillis(startTimeInMillis);
    secondExperimentBuilder.setTriggerEvent(triggerEvent);
    secondExperimentBuilder.setTriggerTimeoutMillis(triggerTimeoutInMillis);
    secondExperimentBuilder.setTimeToLiveMillis(timeToLiveInMillis);
    return secondExperimentBuilder.build();
  }

  private PersistedConfig newConfigs(
      ConfigHolder activeHolder, ConfigHolder fetchedHolder, ConfigHolder defaultsHolder) {
    PersistedConfig.Builder persistedConfigs = PersistedConfig.newBuilder();
    persistedConfigs.setActiveConfigHolder(activeHolder);
    persistedConfigs.setFetchedConfigHolder(fetchedHolder);
    persistedConfigs.setDefaultsConfigHolder(defaultsHolder);
    return persistedConfigs.build();
  }

  private ConfigHolder newHolder(
      long timestamp, List<ExperimentPayload> experiments, NamespaceKeyValue... namespaces) {
    ConfigHolder.Builder holder = ConfigHolder.newBuilder();

    holder.setTimestamp(timestamp);
    for (ExperimentPayload experiment : experiments) {
      holder.addExperimentPayload(experiment.toByteString());
    }
    for (NamespaceKeyValue namespace : namespaces) {
      holder.addNamespaceKeyValue(namespace);
    }

    return holder.build();
  }

  private NamespaceKeyValue newNamespace(String namespace, KeyValue... values) {
    NamespaceKeyValue.Builder namespaceValues = NamespaceKeyValue.newBuilder();

    namespaceValues.setNamespace(namespace);
    for (KeyValue value : values) {
      namespaceValues.addKeyValue(value);
    }

    return namespaceValues.build();
  }

  private KeyValue newKeyValue(String key, String value) {
    return KeyValue.newBuilder().setKey(key).setValue(ByteString.copyFromUtf8(value)).build();
  }

  private void writeTestDataToLegacyConfigsFile() throws Exception {
    try (FileOutputStream outputStream =
        context.openFileOutput(LEGACY_CONFIGS_FILE_NAME, Context.MODE_PRIVATE)) {
      legacyConfigs.writeTo(outputStream);
    }
  }
}
