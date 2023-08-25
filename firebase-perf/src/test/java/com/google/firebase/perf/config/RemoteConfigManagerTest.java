// Copyright 2021 Google LLC
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

package com.google.firebase.perf.config;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.inject.Provider;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.testing.timing.FakeScheduledExecutorService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link RemoteConfigManager}. */
@RunWith(RobolectricTestRunner.class)
public final class RemoteConfigManagerTest extends FirebasePerformanceTestBase {

  private static final String FIREPERF_FRC_NAMESPACE_NAME = "fireperf";
  private static final FirebaseRemoteConfigValue TRUE_VALUE =
      new RemoteConfigValueImplForTest("true");
  private static final FirebaseRemoteConfigValue FALSE_VALUE =
      new RemoteConfigValueImplForTest("false");

  @Mock private FirebaseRemoteConfig mockFirebaseRemoteConfig;
  @Mock private RemoteConfigComponent mockFirebaseRemoteConfigComponent;
  @Mock private Provider<RemoteConfigComponent> mockFirebaseRemoteConfigProvider;

  private DeviceCacheManager cacheManager;
  private FakeScheduledExecutorService fakeExecutor;

  @Before
  public void setUp() {
    initMocks(this);

    fakeExecutor = new FakeScheduledExecutorService();
    // DeviceCacheManager initialization requires immediate blocking task execution in its executor
    cacheManager = new DeviceCacheManager(MoreExecutors.newDirectExecutorService());

    when(mockFirebaseRemoteConfigProvider.get()).thenReturn(mockFirebaseRemoteConfigComponent);
    when(mockFirebaseRemoteConfigComponent.get(FIREPERF_FRC_NAMESPACE_NAME))
        .thenReturn(mockFirebaseRemoteConfig);
    when(mockFirebaseRemoteConfig.getAll()).thenReturn(new HashMap<>());
  }

  @Test
  public void getInstance_verifiesSingleton() {
    RemoteConfigManager instanceOne = RemoteConfigManager.getInstance();
    RemoteConfigManager instanceTwo = RemoteConfigManager.getInstance();

    assertThat(instanceOne).isSameInstanceAs(instanceTwo);
  }

  // region Tests that verify output of valid/invalid Firebase Remote Config values.

  @Test
  public void getDouble_keyIsNull_returnsEmpty() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getDouble(null).isAvailable()).isFalse();
  }

  @Test
  public void getDouble_invalidFrcType_returnsEmpty() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest(/* throwsException= */ true));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getDouble("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getDouble_validFrcValue_returnsValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key1", new RemoteConfigValueImplForTest("100.0f"));
    configs.put("some_key2", new RemoteConfigValueImplForTest("1.0f"));
    configs.put("some_key3", new RemoteConfigValueImplForTest("0.0f"));
    configs.put("some_key4", new RemoteConfigValueImplForTest("0.001f"));
    configs.put("some_key5", new RemoteConfigValueImplForTest("0.123f"));
    configs.put("some_key6", new RemoteConfigValueImplForTest("0.01f"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getDouble("some_key1").get()).isEqualTo(100.0);
    assertThat(testRemoteConfigManager.getDouble("some_key2").get()).isEqualTo(1.0);
    assertThat(testRemoteConfigManager.getDouble("some_key3").get()).isEqualTo(0.0);
    assertThat(testRemoteConfigManager.getDouble("some_key4").get()).isEqualTo(0.001);
    assertThat(testRemoteConfigManager.getDouble("some_key5").get()).isEqualTo(0.123);
    assertThat(testRemoteConfigManager.getDouble("some_key6").get()).isEqualTo(0.01);
  }

  @Test
  public void getLong_keyIsNull_returnsEmpty() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getLong(null).isAvailable()).isFalse();
  }

  @Test
  public void getLong_invalidFrcType_returnsEmpty() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest(/* throwsException= */ true));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getLong_validFrcValue_returnsValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key1", new RemoteConfigValueImplForTest("-1"));
    configs.put("some_key2", new RemoteConfigValueImplForTest("0"));
    configs.put("some_key3", new RemoteConfigValueImplForTest("1"));
    configs.put("some_key4", new RemoteConfigValueImplForTest("10"));
    configs.put("some_key5", new RemoteConfigValueImplForTest("100"));
    configs.put("some_key6", new RemoteConfigValueImplForTest("123456"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getLong("some_key1").get()).isEqualTo(-1L);
    assertThat(testRemoteConfigManager.getLong("some_key2").get()).isEqualTo(0L);
    assertThat(testRemoteConfigManager.getLong("some_key3").get()).isEqualTo(1L);
    assertThat(testRemoteConfigManager.getLong("some_key4").get()).isEqualTo(10L);
    assertThat(testRemoteConfigManager.getLong("some_key5").get()).isEqualTo(100L);
    assertThat(testRemoteConfigManager.getLong("some_key6").get()).isEqualTo(123456L);
  }

  @Test
  public void getBoolean_keyIsNull_returnsEmpty() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getBoolean(null).isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_invalidFrcType_returnsEmpty() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest(/* throwsException= */ true));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_validFrcValue_returnsValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key1", new RemoteConfigValueImplForTest("true"));
    configs.put("some_key2", new RemoteConfigValueImplForTest("false"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getBoolean("some_key1").get()).isTrue();
    assertThat(testRemoteConfigManager.getBoolean("some_key2").get()).isFalse();
  }

  @Test
  public void getString_keyIsNull_returnsEmpty() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getString(null).isAvailable()).isFalse();
  }

  @Test
  public void getString_validFrcValue_returnsValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key1", new RemoteConfigValueImplForTest(""));
    configs.put("some_key2", new RemoteConfigValueImplForTest("       "));
    configs.put("some_key3", new RemoteConfigValueImplForTest("stringValue"));
    configs.put("some_key4", new RemoteConfigValueImplForTest("1.0.1;1.0.2"));
    configs.put("some_key5", new RemoteConfigValueImplForTest("charAndNumber1234567"));
    configs.put("some_key6", new RemoteConfigValueImplForTest("true"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getString("some_key1").get()).isEmpty();
    assertThat(testRemoteConfigManager.getString("some_key2").get()).isEqualTo("       ");
    assertThat(testRemoteConfigManager.getString("some_key3").get()).isEqualTo("stringValue");
    assertThat(testRemoteConfigManager.getString("some_key4").get()).isEqualTo("1.0.1;1.0.2");
    assertThat(testRemoteConfigManager.getString("some_key5").get())
        .isEqualTo("charAndNumber1234567");
    assertThat(testRemoteConfigManager.getString("some_key6").get()).isEqualTo("true");
  }

  @Test
  public void getString_validFrcValue_updatesWithNewValue() {
    Map<String, FirebaseRemoteConfigValue> configs = new HashMap<>();
    configs.put("some_key1", new RemoteConfigValueImplForTest("key1"));
    configs.put("some_key2", new RemoteConfigValueImplForTest("key2"));
    configs.put("some_key3", new RemoteConfigValueImplForTest("key3"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getString("some_key1").get()).isEqualTo("key1");
    assertThat(testRemoteConfigManager.getString("some_key2").get()).isEqualTo("key2");
    assertThat(testRemoteConfigManager.getString("some_key3").get()).isEqualTo("key3");

    // Merge with new values
    Map<String, FirebaseRemoteConfigValue> newConfigs = new HashMap<>();
    newConfigs.put("some_key1", new RemoteConfigValueImplForTest("newKey1"));
    newConfigs.put("some_key2", new RemoteConfigValueImplForTest("newKey2"));
    newConfigs.put("some_key4", new RemoteConfigValueImplForTest("key4"));

    testRemoteConfigManager.syncConfigValues(newConfigs);
    assertThat(testRemoteConfigManager.getString("some_key1").get()).isEqualTo("newKey1");
    assertThat(testRemoteConfigManager.getString("some_key2").get()).isEqualTo("newKey2");
    assertThat(testRemoteConfigManager.getString("some_key4").get()).isEqualTo("key4");
    assertThat(testRemoteConfigManager.getString("some_key3").isAvailable()).isFalse();
  }

  @Test
  public void syncConfigValues_savesNewlyFetchedValueToDeviceCache() {
    Map<String, FirebaseRemoteConfigValue> configs = new HashMap<>();
    ConfigurationConstants.ExperimentTTID flag =
        ConfigurationConstants.ExperimentTTID.getInstance();
    configs.put(flag.getRemoteConfigFlag(), TRUE_VALUE);
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(cacheManager.getBoolean(flag.getDeviceCacheFlag()).isAvailable()).isFalse();

    configs.put(flag.getRemoteConfigFlag(), FALSE_VALUE);
    testRemoteConfigManager.syncConfigValues(configs);

    assertThat(cacheManager.getBoolean(flag.getDeviceCacheFlag()).isAvailable()).isTrue();
    assertThat(cacheManager.getBoolean(flag.getDeviceCacheFlag()).get()).isFalse();

    configs.put(flag.getRemoteConfigFlag(), TRUE_VALUE);
    testRemoteConfigManager.syncConfigValues(configs);

    assertThat(cacheManager.getBoolean(flag.getDeviceCacheFlag()).isAvailable()).isTrue();
    assertThat(cacheManager.getBoolean(flag.getDeviceCacheFlag()).get()).isTrue();
  }

  @Test
  public void getRemoteConfigValueOrDefaultLong_invalidFrcValue_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest(/* throwsException= */ true));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5L)).isEqualTo(5L);
    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 7L)).isEqualTo(7L);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_invalidFrcValue_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest(/* throwsException= */ true));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isEqualTo(5.0);
    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 7.0))
        .isEqualTo(7.0);
  }

  @Test
  public void getRemoteConfigValueOrDefaultBoolean_invalidFrcValue_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest(/* throwsException= */ true));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", true)).isTrue();
  }

  @Test
  public void getRemoteConfigValueOrDefaultLong_nullFrc_returnsDefaultValue() {
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(
            createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5L)).isEqualTo(5L);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_nullFrc_returnsDefaultValue() {
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(
            createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isEqualTo(5.0);
  }

  @Test
  public void getRemoteConfigValueOrDefaultBoolean_nullFrc_returnsDefaultValue() {
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(
            createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", false)).isFalse();
  }

  @Test
  public void getRemoteConfigValueOrDefaultString_nullFrc_returnsDefaultValue() {
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(
            createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", " "))
        .isEqualTo(" ");
  }

  @Test
  public void getRemoteConfigValueOrDefaultLong_frcInjectedAfterInit_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("25"));
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(configs);

    // Inject FirebaseRemoteConfig after it's available.
    testRemoteConfigManager.setFirebaseRemoteConfigProvider(mockFirebaseRemoteConfigProvider);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5L))
        .isEqualTo(25L);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_frcInjectedAfterInit_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(configs);
    configs.put("some_key", new RemoteConfigValueImplForTest("25.0"));
    testRemoteConfigManager.setFirebaseRemoteConfigProvider(mockFirebaseRemoteConfigProvider);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isEqualTo(25.0);
  }

  @Test
  public void getRemoteConfigValueOrDefaultBoolean_frcInjectedAfterInit_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(configs);
    configs.put("some_key", new RemoteConfigValueImplForTest("true"));
    testRemoteConfigManager.setFirebaseRemoteConfigProvider(mockFirebaseRemoteConfigProvider);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", false)).isTrue();
  }

  @Test
  public void getRemoteConfigValueOrDefaultString_frcInjectedAfterInit_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(configs);
    configs.put("some_key", new RemoteConfigValueImplForTest(" "));
    testRemoteConfigManager.setFirebaseRemoteConfigProvider(mockFirebaseRemoteConfigProvider);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", " "))
        .isEqualTo(" ");
  }

  // endregion

  // region Tests for Firebase Remote Config source of value.

  @Test
  public void getRemoteConfigValueOrDefaultLong_frcValueSourceIsRemote_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("4"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5L)).isEqualTo(4L);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_frcValueSourceIsRemote_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("4.0"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isEqualTo(4.0);
  }

  @Test
  public void getRemoteConfigValueOrDefaultBoolean_frcValueSourceIsRemote_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("false"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", true)).isFalse();
  }

  @Test
  public void getRemoteConfigValueOrDefaultString_frcValueSourceIsRemote_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("1.0.0"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", ""))
        .isEqualTo("1.0.0");
  }

  @Test
  public void getBoolean_frcValueSourceIsRemote_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("false"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getBoolean("some_key").get()).isFalse();
  }

  @Test
  public void getString_frcValueSourceIsRemote_returnsFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("1.0.0"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getString("some_key").get()).isEqualTo("1.0.0");
  }

  @Test
  public void getRemoteConfigValueOrDefaultLong_frcValueSourceIsNotRemote_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    // Pass 1: Test with VALUE_SOURCE_DEFAULT
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("4L", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT));
    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5)).isEqualTo(5);

    // Pass 2: Test with VALUE_SOURCE_STATIC
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("4L", FirebaseRemoteConfig.VALUE_SOURCE_STATIC));
    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5)).isEqualTo(5);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_frcValueSourceIsNotRemote_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    // Pass 1: Test with VALUE_SOURCE_DEFAULT
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("4.0", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT));

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isEqualTo(5.0);

    // Pass 2: Test with VALUE_SOURCE_STATIC
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("4.0", FirebaseRemoteConfig.VALUE_SOURCE_STATIC));

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isEqualTo(5.0);
  }

  @Test
  public void getRemoteConfigValueOrDefaultBoolean_frcValueSourceIsNotRemote_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();

    // Pass 1: Test with VALUE_SOURCE_DEFAULT
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("false", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT));
    RemoteConfigManager testRemoteConfigManager1 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager1.getRemoteConfigValueOrDefault("some_key", true)).isTrue();

    // Pass 2: Test with VALUE_SOURCE_STATIC
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("false", FirebaseRemoteConfig.VALUE_SOURCE_STATIC));
    RemoteConfigManager testRemoteConfigManager2 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager2.getRemoteConfigValueOrDefault("some_key", true)).isTrue();
  }

  @Test
  public void getRemoteConfigValueOrDefaultString_frcValueSourceIsNotRemote_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();

    // Pass 1: Test with VALUE_SOURCE_DEFAULT
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("1.0.0", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT));
    RemoteConfigManager testRemoteConfigManager1 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager1.getRemoteConfigValueOrDefault("some_key", " "))
        .isEqualTo(" ");

    // Pass 2: Test with VALUE_SOURCE_STATIC
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("1.0.0", FirebaseRemoteConfig.VALUE_SOURCE_STATIC));
    RemoteConfigManager testRemoteConfigManager2 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager2.getRemoteConfigValueOrDefault("some_key", " "))
        .isEqualTo(" ");
  }

  @Test
  public void getBoolean_frcValueSourceIsNotRemote_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();

    // Pass 1: Test with VALUE_SOURCE_DEFAULT
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("false", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT));
    RemoteConfigManager testRemoteConfigManager1 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager1.getBoolean("some_key").isAvailable()).isFalse();

    // Pass 2: Test with VALUE_SOURCE_STATIC
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("false", FirebaseRemoteConfig.VALUE_SOURCE_STATIC));
    RemoteConfigManager testRemoteConfigManager2 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager2.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getString_frcValueSourceIsNotRemote_returnsDefaultValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();

    // Pass 1: Test with VALUE_SOURCE_DEFAULT
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("1.0.0", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT));
    RemoteConfigManager testRemoteConfigManager1 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager1.getString("some_key").isAvailable()).isFalse();

    // Pass 2: Test with VALUE_SOURCE_STATIC
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("1.0.0", FirebaseRemoteConfig.VALUE_SOURCE_STATIC));
    RemoteConfigManager testRemoteConfigManager2 = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager2.getString("some_key").isAvailable()).isFalse();
  }
  // endregion.

  // region Tests for testing the quirks of depending on Firebase RC v0.

  @Test
  public void getRemoteConfigValueOrDefaultLong_triggersRcFetchOnceAndOnlyOnce() {
    verifyRcTriggersFetchOnceAndOnlyOnceFor(5L);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_triggersRcFetchOnceAndOnlyOnce() {
    verifyRcTriggersFetchOnceAndOnlyOnceFor(5.0);
  }

  @Test
  public void getRemoteConfigValueOrDefaultBoolean_triggersRcFetchOnceAndOnlyOnce() {
    verifyRcTriggersFetchOnceAndOnlyOnceFor(true);
  }

  @Test
  public void getRemoteConfigValueOrDefaultString_triggersRcFetchOnceAndOnlyOnce() {
    verifyRcTriggersFetchOnceAndOnlyOnceFor("1.0.0");
  }

  @Test
  public void getBoolean_multipleTimes_triggersRcFetchOnlyOnce() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());
    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);

    testRemoteConfigManager.getBoolean("some_key");
    testRemoteConfigManager.getBoolean("some_other_key");

    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig, times(1)).fetchAndActivate();
  }

  @Test
  public void getString_multipleTimes_triggersRcFetchOnlyOnce() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());
    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);

    testRemoteConfigManager.getString("some_key");
    testRemoteConfigManager.getString("some_other_key");

    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig, times(1)).fetchAndActivate();
  }

  @Test
  public void getRemoteConfigValueOrDefaultLong_triggersOneRcFetchIfPreviousFetchWasTooLongAgo() {
    verifyRcTriggersOneFetchIfPreviousFetchWasTooLongAgoFor(5L);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_triggersOneRcFetchIfPreviousFetchWasTooLongAgo() {
    verifyRcTriggersOneFetchIfPreviousFetchWasTooLongAgoFor(5.0);
  }

  @Test
  public void
      getRemoteConfigValueOrDefaultBoolean_triggersOneRcFetchIfPreviousFetchWasTooLongAgo() {
    verifyRcTriggersOneFetchIfPreviousFetchWasTooLongAgoFor(true);
  }

  @Test
  public void getRemoteConfigValueOrDefaultString_triggersOneRcFetchIfPreviousFetchWasTooLongAgo() {
    verifyRcTriggersOneFetchIfPreviousFetchWasTooLongAgoFor("1.0.0");
  }

  @Test
  public void getBoolean_lastFetchIsLongAgo_triggersOneRcFetch() {
    RemoteConfigManager remoteConfigManagerPartialMock =
        spy(setupTestRemoteConfigManager(createDefaultRcConfigMap()));

    // First fetch.
    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);
    remoteConfigManagerPartialMock.getBoolean("some_key");
    remoteConfigManagerPartialMock.getBoolean("some_other_key");
    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig).fetchAndActivate();

    // Simulate time fast forward 12 hours + a 1 ms
    when(remoteConfigManagerPartialMock.getCurrentSystemTimeMillis())
        .thenReturn(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12) + 1);
    remoteConfigManagerPartialMock.getBoolean("some_key");
    remoteConfigManagerPartialMock.getBoolean("some_other_key");

    verify(mockFirebaseRemoteConfig, times(2)).fetchAndActivate();
  }

  @Test
  public void getString_lastFetchIsLongAgo_triggersOneRcFetch() {
    RemoteConfigManager remoteConfigManagerPartialMock =
        spy(setupTestRemoteConfigManager(createDefaultRcConfigMap()));

    // First fetch.
    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);
    remoteConfigManagerPartialMock.getString("some_key");
    remoteConfigManagerPartialMock.getString("some_other_key");
    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig).fetchAndActivate();

    // Simulate time fast forward 12 hours + a 1 ms
    when(remoteConfigManagerPartialMock.getCurrentSystemTimeMillis())
        .thenReturn(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12) + 1);
    remoteConfigManagerPartialMock.getString("some_key");
    remoteConfigManagerPartialMock.getString("some_other_key");

    verify(mockFirebaseRemoteConfig, times(2)).fetchAndActivate();
  }

  // endregion

  @Test
  public void
      getRemoteConfigValueOrDefault_validFrcValueAndNullDefaultValue_returnsFrcValueAsString() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put(
        "some_key",
        new RemoteConfigValueImplForTest("100", FirebaseRemoteConfig.VALUE_SOURCE_REMOTE));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    String rcValue = testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", null);
    assertThat(rcValue).isEqualTo("100");
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_scalesFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("0.5"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isEqualTo(0.5);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_scalesVerySmallFrcValue() {
    Map<String, FirebaseRemoteConfigValue> configs = createDefaultRcConfigMap();
    configs.put("some_key", new RemoteConfigValueImplForTest("0.000005"));
    RemoteConfigManager testRemoteConfigManager = setupTestRemoteConfigManager(configs);

    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isAtMost(0.000005);
    assertThat(testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", 5.0))
        .isAtLeast(0.0000049);
  }

  @Test
  public void getRemoteConfigValueOrDefaultLong_allowsRefetchOnFailure() {
    verifyRcAllowsRefetchOnFailureFor(5L);
  }

  @Test
  public void getRemoteConfigValueOrDefaultDouble_allowsRefetchOnFailure() {
    verifyRcAllowsRefetchOnFailureFor(5.0);
  }

  @Test
  public void getRemoteConfigValueOrDefaultBoolean_allowsRefetchOnFailure() {
    verifyRcAllowsRefetchOnFailureFor(true);
  }

  @Test
  public void getRemoteConfigValueOrDefaultString_allowsRefetchOnFailure() {
    verifyRcAllowsRefetchOnFailureFor("1.0.0");
  }

  @Test
  public void getBoolean_allowsRefetchOnFailure() {
    TaskCompletionSource<Boolean> fakeTaskCompletionSource = new TaskCompletionSource<>();

    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(
            fakeTaskCompletionSource.getTask(),
            /* initializeFrc = */ true,
            createDefaultRcConfigMap());

    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);

    testRemoteConfigManager.getBoolean("some_key");

    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig).fetchAndActivate();

    fakeTaskCompletionSource.setException(new Exception("something bad happened"));
    simulateFirebaseRemoteConfigLastFetchStatus(FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE);
    fakeExecutor.runAll();

    testRemoteConfigManager.getBoolean("some_key");
    fakeExecutor.runAll();

    verify(mockFirebaseRemoteConfig, times(2)).fetchAndActivate();
  }

  @Test
  public void getString_allowsRefetchOnFailure() {
    TaskCompletionSource<Boolean> fakeTaskCompletionSource = new TaskCompletionSource<>();

    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(
            fakeTaskCompletionSource.getTask(),
            /* initializeFrc = */ true,
            createDefaultRcConfigMap());

    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);

    testRemoteConfigManager.getString("some_key");

    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig).fetchAndActivate();

    fakeTaskCompletionSource.setException(new Exception("something bad happened"));
    simulateFirebaseRemoteConfigLastFetchStatus(FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE);
    fakeExecutor.runAll();

    testRemoteConfigManager.getString("some_key");
    fakeExecutor.runAll();

    verify(mockFirebaseRemoteConfig, times(2)).fetchAndActivate();
  }

  @Test
  public void isLastFetchFailed_frcIsNull_returnsTrue() {
    RemoteConfigManager testRemoteConfigManager =
        setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(
            createDefaultRcConfigMap());

    assertThat(testRemoteConfigManager.isLastFetchFailed()).isTrue();
  }

  @Test
  public void isLastFetchFailed_frcIsNonNullAndStatusFailed_returnsTrue() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());
    simulateFirebaseRemoteConfigLastFetchStatus(FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE);

    assertThat(testRemoteConfigManager.isLastFetchFailed()).isTrue();
  }

  @Test
  public void isLastFetchFailed_frcIsNonNullAndStatusThrottled_returnsTrue() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());
    simulateFirebaseRemoteConfigLastFetchStatus(FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED);

    assertThat(testRemoteConfigManager.isLastFetchFailed()).isTrue();
  }

  @Test
  public void isLastFetchFailed_frcIsNonNullAndStatusOtherThanFailedOrThrottled_returnsFalse() {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());

    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);
    assertThat(testRemoteConfigManager.isLastFetchFailed()).isFalse();

    simulateFirebaseRemoteConfigLastFetchStatus(FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS);
    assertThat(testRemoteConfigManager.isLastFetchFailed()).isFalse();
  }

  @Test
  public void triggerRemoteConfigFetchIfNecessary_doesNotFetchBeforeAppStartRandomDelay() {
    long appStartConfigFetchDelay = 5000;
    RemoteConfigManager remoteConfigManagerPartialMock =
        spy(
            setupTestRemoteConfigManager(
                createFakeTaskThatDoesNothing(),
                true,
                createDefaultRcConfigMap(),
                appStartConfigFetchDelay));

    // Simulate time fast forward to some time before fetch time is up
    long appStartTimeInMs = System.currentTimeMillis();
    when(remoteConfigManagerPartialMock.getCurrentSystemTimeMillis())
        .thenReturn(appStartTimeInMs + appStartConfigFetchDelay - 2000);

    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_key", 5L);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_other_key", 5.0);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_other_key_2", true);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_other_key_3", "1.0.0");

    verify(mockFirebaseRemoteConfig, times(0)).fetchAndActivate();
  }

  @Test
  public void triggerRemoteConfigFetchIfNecessary_fetchesAfterAppStartRandomDelay() {
    long appStartConfigFetchDelay = 5000;
    RemoteConfigManager remoteConfigManagerPartialMock =
        spy(
            setupTestRemoteConfigManager(
                createFakeTaskThatDoesNothing(),
                true,
                createDefaultRcConfigMap(),
                appStartConfigFetchDelay));

    // Simulate time fast forward to 2s after fetch delay time is up
    long appStartTimeInMs = System.currentTimeMillis();
    when(remoteConfigManagerPartialMock.getCurrentSystemTimeMillis())
        .thenReturn(appStartTimeInMs + appStartConfigFetchDelay + 2000);

    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_key", 5L);

    verify(mockFirebaseRemoteConfig, times(1)).fetchAndActivate();
  }

  private void simulateFirebaseRemoteConfigLastFetchStatus(int lastFetchStatus) {
    when(mockFirebaseRemoteConfig.getInfo())
        .thenReturn(
            new FirebaseRemoteConfigInfo() {
              @Override
              public long getFetchTimeMillis() {
                return 100;
              }

              @Override
              public int getLastFetchStatus() {
                return lastFetchStatus;
              }

              @Override
              public FirebaseRemoteConfigSettings getConfigSettings() {
                return null;
              }
            });
  }

  private Task<Boolean> createFakeTaskThatDoesNothing() {
    return new TaskCompletionSource<Boolean>().getTask();
  }

  /**
   * @see RemoteConfigManagerTest#setupTestRemoteConfigManager(Map)
   *     <p>This method overload allows you to inject a {@link Task} that you can control to change
   *     the behavior of the class under test. You create a {@link TaskCompletionSource} and pass
   *     the underlying task to this method.
   */
  private RemoteConfigManager setupTestRemoteConfigManager(
      Task<Boolean> fakeTask,
      boolean initializeFrc,
      Map<String, FirebaseRemoteConfigValue> configs,
      long appStartConfigFetchDelayInMs) {
    simulateFirebaseRemoteConfigLastFetchStatus(FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS);
    when(mockFirebaseRemoteConfig.fetchAndActivate()).thenReturn(fakeTask);
    when(mockFirebaseRemoteConfig.getAll()).thenReturn(configs);
    if (initializeFrc) {
      return new RemoteConfigManager(
          cacheManager,
          fakeExecutor,
          mockFirebaseRemoteConfig,
          appStartConfigFetchDelayInMs,
          RemoteConfigManager.getInitialStartupMillis());
    } else {
      return new RemoteConfigManager(
          cacheManager,
          fakeExecutor,
          /* firebaseRemoteConfig= */ null,
          appStartConfigFetchDelayInMs,
          RemoteConfigManager.getInitialStartupMillis());
    }
  }

  private RemoteConfigManager setupTestRemoteConfigManager(
      Task<Boolean> fakeTask,
      boolean initializeFrc,
      Map<String, FirebaseRemoteConfigValue> configs) {
    return setupTestRemoteConfigManager(fakeTask, initializeFrc, configs, 0);
  }

  /**
   * Creates and returns a test instance of RemoteConfigManager that has {@link
   * RemoteConfigManagerTest#fakeExecutor} and {@link
   * RemoteConfigManagerTest#mockFirebaseRemoteConfig} injected into it. It has no Firebase Remote
   * Config values activated. It however assumes that an app developer has successfully called
   * {@link FirebaseRemoteConfig#fetchAndActivate()} - which you can override by calling {@link
   * RemoteConfigManagerTest#simulateFirebaseRemoteConfigLastFetchStatus(int)}.
   */
  private RemoteConfigManager setupTestRemoteConfigManager(
      Map<String, FirebaseRemoteConfigValue> configs) {
    return setupTestRemoteConfigManager(
        createFakeTaskThatDoesNothing(), /* initializeFrc= */ true, configs);
  }

  private static Map<String, FirebaseRemoteConfigValue> createDefaultRcConfigMap() {
    Map<String, FirebaseRemoteConfigValue> configs = new HashMap<>();
    configs.put("some_key", new RemoteConfigValueImplForTest(""));
    configs.put("some_other_key", new RemoteConfigValueImplForTest(""));
    return configs;
  }

  private RemoteConfigManager
      setupRemoteConfigManagerWithUninitializedFirebaseRemoteConfigAndFirebaseApp(
          Map<String, FirebaseRemoteConfigValue> configs) {
    return setupTestRemoteConfigManager(
        createFakeTaskThatDoesNothing(), /* initializeFrc= */ false, configs);
  }

  private void verifyRcTriggersFetchOnceAndOnlyOnceFor(Object defaultValue) {
    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(createDefaultRcConfigMap());
    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);

    testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", defaultValue);
    testRemoteConfigManager.getRemoteConfigValueOrDefault("some_other_key", defaultValue);

    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig, times(1)).fetchAndActivate();
  }

  private void verifyRcTriggersOneFetchIfPreviousFetchWasTooLongAgoFor(Object defaultValue) {
    RemoteConfigManager remoteConfigManagerPartialMock =
        spy(setupTestRemoteConfigManager(createDefaultRcConfigMap()));

    // First fetch.
    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_key", defaultValue);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_other_key", defaultValue);
    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig).fetchAndActivate();

    // Simulate time fast forward 12 hours + a 1 ms
    when(remoteConfigManagerPartialMock.getCurrentSystemTimeMillis())
        .thenReturn(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12) + 1);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_key", defaultValue);
    remoteConfigManagerPartialMock.getRemoteConfigValueOrDefault("some_other_key", defaultValue);

    verify(mockFirebaseRemoteConfig, times(2)).fetchAndActivate();
  }

  private void verifyRcAllowsRefetchOnFailureFor(Object defaultValue) {
    TaskCompletionSource<Boolean> fakeTaskCompletionSource = new TaskCompletionSource<>();

    RemoteConfigManager testRemoteConfigManager =
        setupTestRemoteConfigManager(
            fakeTaskCompletionSource.getTask(),
            /* initializeFrc = */ true,
            createDefaultRcConfigMap());

    simulateFirebaseRemoteConfigLastFetchStatus(
        FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET);

    testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", defaultValue);

    fakeExecutor.runAll();
    verify(mockFirebaseRemoteConfig).fetchAndActivate();

    fakeTaskCompletionSource.setException(new Exception("something bad happened"));
    simulateFirebaseRemoteConfigLastFetchStatus(FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE);
    fakeExecutor.runAll();

    testRemoteConfigManager.getRemoteConfigValueOrDefault("some_key", defaultValue);
    fakeExecutor.runAll();

    verify(mockFirebaseRemoteConfig, times(2)).fetchAndActivate();
  }

  private static class RemoteConfigValueImplForTest implements FirebaseRemoteConfigValue {

    private final String value;
    private final boolean throwsException;
    private final int source;

    RemoteConfigValueImplForTest(String value) {
      this(value, false, FirebaseRemoteConfig.VALUE_SOURCE_REMOTE);
    }

    RemoteConfigValueImplForTest(String value, int source) {
      this(value, false, source);
    }

    RemoteConfigValueImplForTest(boolean throwsException) {
      this("", throwsException, FirebaseRemoteConfig.VALUE_SOURCE_REMOTE);
    }

    RemoteConfigValueImplForTest(String value, boolean throwsException, int source) {
      this.value = value;
      this.throwsException = throwsException;
      this.source = source;
    }

    @Override
    public long asLong() {
      if (throwsException) {
        throw new IllegalArgumentException();
      }
      return Long.parseLong(value);
    }

    @Override
    public double asDouble() {
      if (throwsException) {
        throw new IllegalArgumentException();
      }
      return Double.parseDouble(value);
    }

    @NonNull
    @Override
    public String asString() {
      if (throwsException) {
        return "";
      }
      return value;
    }

    @NonNull
    @Override
    public byte[] asByteArray() {
      if (throwsException) {
        throw new IllegalArgumentException();
      }
      return value.getBytes(UTF_8);
    }

    @Override
    public boolean asBoolean() {
      if (throwsException) {
        throw new IllegalArgumentException();
      }
      return Boolean.parseBoolean(value);
    }

    @Override
    public int getSource() {
      return source;
    }
  }
}
