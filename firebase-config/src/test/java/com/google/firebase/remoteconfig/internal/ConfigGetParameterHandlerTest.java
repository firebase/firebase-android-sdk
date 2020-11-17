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
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BOOLEAN;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BYTE_ARRAY;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_DOUBLE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_LONG;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_STRING;
import static com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler.FRC_BYTE_ARRAY_ENCODING;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link ConfigGetParameterHandler}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigGetParameterHandlerTest {
  private static final String STRING_KEY = "string_key";
  private static final String ACTIVATED_STRING_VALUE = "activated_string_value";
  private static final String DEFAULTS_STRING_VALUE = "defaults_string_value";

  private static final String BOOLEAN_KEY = "boolean_key";
  private static final String ACTIVATED_BOOLEAN_STRING_VALUE = "y";
  private static final String DEFAULTS_BOOLEAN_STRING_VALUE = "no";
  private static final boolean ACTIVATED_BOOLEAN_VALUE = true;
  private static final boolean DEFAULTS_BOOLEAN_VALUE = false;

  private static final String BYTE_ARRAY_KEY = "byte_array_key";
  private static final String ACTIVATED_BYTE_ARRAY_STRING_VALUE = "Activated";
  private static final String DEFAULTS_BYTE_ARRAY_STRING_VALUE = "Defaults";
  private static final byte[] ACTIVATED_BYTE_ARRAY_VALUE =
      ACTIVATED_BYTE_ARRAY_STRING_VALUE.getBytes(FRC_BYTE_ARRAY_ENCODING);
  private static final byte[] DEFAULTS_BYTE_ARRAY_VALUE =
      DEFAULTS_BYTE_ARRAY_STRING_VALUE.getBytes(FRC_BYTE_ARRAY_ENCODING);

  private static final String DOUBLE_KEY = "double_key";
  private static final double ACTIVATED_DOUBLE_VALUE = 555.0D;
  private static final double DEFAULTS_DOUBLE_VALUE = 277.5D;

  private static final String LONG_KEY = "long_key";
  private static final long ACTIVATED_LONG_VALUE = 500;
  private static final long DEFAULTS_LONG_VALUE = 250;

  @Mock private ConfigCacheClient mockActivatedCache;
  @Mock private ConfigCacheClient mockDefaultsCache;

  private ConfigGetParameterHandler getHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    getHandler =
        new ConfigGetParameterHandler(
            MoreExecutors.directExecutor(), mockActivatedCache, mockDefaultsCache);
  }

  @Test
  public void getString_getBlockingFails_returnsEmptyString() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEmpty();
  }

  @Test
  public void getString_activatedAndDefaultsKeysExist_returnsActivatedValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(STRING_KEY, ACTIVATED_STRING_VALUE));
    loadDefaultsCacheWithMap(ImmutableMap.of(STRING_KEY, DEFAULTS_STRING_VALUE));

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEqualTo(ACTIVATED_STRING_VALUE);
  }

  @Test
  public void getString_noActivatedKeyButDefaultsKeyExists_returnsDefaultsValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of(STRING_KEY, DEFAULTS_STRING_VALUE));

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEqualTo(DEFAULTS_STRING_VALUE);
  }

  @Test
  public void getString_activatedAndDefaultsKeysDoNotExist_returnsStaticDefault() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of());

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEqualTo(DEFAULT_VALUE_FOR_STRING);
  }

  @Test
  public void getString_activatedKeyExistsAndNoDefaultsConfigs_returnsActivatedValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(STRING_KEY, ACTIVATED_STRING_VALUE));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEqualTo(ACTIVATED_STRING_VALUE);
  }

  @Test
  public void getString_noActivatedConfigsButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of(STRING_KEY, DEFAULTS_STRING_VALUE));

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEqualTo(DEFAULTS_STRING_VALUE);
  }

  @Test
  public void getString_activatedAndDefaultsConfigsDoNotExist_returnsStaticDefault() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEqualTo(DEFAULT_VALUE_FOR_STRING);
  }

  @Test
  public void getString_activatedStillLoadingFromDisk_blocksAndReturnsActivatedValue()
      throws Exception {
    loadActivatedAsyncCacheWithIncompleteTask();
    loadActivatedBlockingCacheWith(ImmutableMap.of(STRING_KEY, ACTIVATED_STRING_VALUE));

    String stringValue = getHandler.getString(STRING_KEY);

    assertThat(stringValue).isEqualTo(ACTIVATED_STRING_VALUE);
  }

  @Test
  public void getBoolean_activatedAndDefaultsKeysExist_returnsActivatedValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(BOOLEAN_KEY, ACTIVATED_BOOLEAN_STRING_VALUE));
    loadDefaultsCacheWithMap(ImmutableMap.of(BOOLEAN_KEY, DEFAULTS_BOOLEAN_STRING_VALUE));

    boolean booleanValue = getHandler.getBoolean(BOOLEAN_KEY);

    assertThat(booleanValue).isEqualTo(ACTIVATED_BOOLEAN_VALUE);
  }

  @Test
  public void getBoolean_noActivatedKeyButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of(BOOLEAN_KEY, DEFAULTS_BOOLEAN_STRING_VALUE));

    boolean booleanValue = getHandler.getBoolean(BOOLEAN_KEY);

    assertThat(booleanValue).isEqualTo(DEFAULTS_BOOLEAN_VALUE);
  }

  @Test
  public void getBoolean_activatedAndDefaultsKeysDoNotExist_returnsStaticDefault()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of());

    boolean booleanValue = getHandler.getBoolean(BOOLEAN_KEY);

    assertThat(booleanValue).isEqualTo(DEFAULT_VALUE_FOR_BOOLEAN);
  }

  @Test
  public void getBoolean_activatedKeyExistsAndNoDefaultsConfigs_returnsActivatedValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(BOOLEAN_KEY, ACTIVATED_BOOLEAN_STRING_VALUE));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    boolean booleanValue = getHandler.getBoolean(BOOLEAN_KEY);

    assertThat(booleanValue).isEqualTo(ACTIVATED_BOOLEAN_VALUE);
  }

  @Test
  public void getBoolean_noActivatedConfigsButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of(BOOLEAN_KEY, DEFAULTS_BOOLEAN_STRING_VALUE));

    boolean booleanValue = getHandler.getBoolean(BOOLEAN_KEY);

    assertThat(booleanValue).isEqualTo(DEFAULTS_BOOLEAN_VALUE);
  }

  @Test
  public void getBoolean_activatedAndDefaultsConfigsDoNotExist_returnsStaticDefault() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    boolean booleanValue = getHandler.getBoolean(BOOLEAN_KEY);

    assertThat(booleanValue).isEqualTo(DEFAULT_VALUE_FOR_BOOLEAN);
  }

  @Test
  public void getBoolean_activatedStillLoadingFromDisk_blocksAndReturnsActivatedValue()
      throws Exception {
    loadActivatedAsyncCacheWithIncompleteTask();
    loadActivatedBlockingCacheWith(ImmutableMap.of(BOOLEAN_KEY, ACTIVATED_BOOLEAN_STRING_VALUE));

    boolean booleanValue = getHandler.getBoolean(BOOLEAN_KEY);

    assertThat(booleanValue).isEqualTo(ACTIVATED_BOOLEAN_VALUE);
  }

  @Test
  public void getByteArray_activatedAndDefaultsKeysExist_returnsActivatedValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(BYTE_ARRAY_KEY, ACTIVATED_BYTE_ARRAY_STRING_VALUE));
    loadDefaultsCacheWithMap(ImmutableMap.of(BYTE_ARRAY_KEY, DEFAULTS_BYTE_ARRAY_STRING_VALUE));

    byte[] byteArrayValue = getHandler.getByteArray(BYTE_ARRAY_KEY);

    assertThat(byteArrayValue).isEqualTo(ACTIVATED_BYTE_ARRAY_VALUE);
  }

  @Test
  public void getByteArray_noActivatedKeyButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of(BYTE_ARRAY_KEY, DEFAULTS_BYTE_ARRAY_STRING_VALUE));

    byte[] byteArrayValue = getHandler.getByteArray(BYTE_ARRAY_KEY);

    assertThat(byteArrayValue).isEqualTo(DEFAULTS_BYTE_ARRAY_VALUE);
  }

  @Test
  public void getByteArray_activatedAndDefaultsKeysDoNotExist_returnsStaticDefault()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of());

    byte[] byteArrayValue = getHandler.getByteArray(BYTE_ARRAY_KEY);

    assertThat(byteArrayValue).isEqualTo(DEFAULT_VALUE_FOR_BYTE_ARRAY);
  }

  @Test
  public void getByteArray_activatedKeyExistsAndNoDefaultsConfigs_returnsActivatedValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(BYTE_ARRAY_KEY, ACTIVATED_BYTE_ARRAY_STRING_VALUE));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    byte[] byteArrayValue = getHandler.getByteArray(BYTE_ARRAY_KEY);

    assertThat(byteArrayValue).isEqualTo(ACTIVATED_BYTE_ARRAY_VALUE);
  }

  @Test
  public void getByteArray_noActivatedConfigsButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of(BYTE_ARRAY_KEY, DEFAULTS_BYTE_ARRAY_STRING_VALUE));

    byte[] byteArrayValue = getHandler.getByteArray(BYTE_ARRAY_KEY);

    assertThat(byteArrayValue).isEqualTo(DEFAULTS_BYTE_ARRAY_VALUE);
  }

  @Test
  public void getByteArray_activatedAndDefaultsConfigsDoNotExist_returnsStaticDefault() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    byte[] byteArrayValue = getHandler.getByteArray(BYTE_ARRAY_KEY);

    assertThat(byteArrayValue).isEqualTo(DEFAULT_VALUE_FOR_BYTE_ARRAY);
  }

  @Test
  public void getByteArray_activatedStillLoadingFromDisk_blocksAndReturnsActivatedValue()
      throws Exception {
    loadActivatedAsyncCacheWithIncompleteTask();
    loadActivatedBlockingCacheWith(
        ImmutableMap.of(BYTE_ARRAY_KEY, ACTIVATED_BYTE_ARRAY_STRING_VALUE));

    byte[] byteArrayValue = getHandler.getByteArray(BYTE_ARRAY_KEY);

    assertThat(byteArrayValue).isEqualTo(ACTIVATED_BYTE_ARRAY_VALUE);
  }

  @Test
  public void getDouble_activatedAndDefaultsKeysExist_returnsActivatedValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(DOUBLE_KEY, ACTIVATED_DOUBLE_VALUE));
    loadDefaultsCacheWithMap(ImmutableMap.of(DOUBLE_KEY, DEFAULTS_DOUBLE_VALUE));

    double doubleValue = getHandler.getDouble(DOUBLE_KEY);

    assertThat(doubleValue).isEqualTo(ACTIVATED_DOUBLE_VALUE);
  }

  @Test
  public void getDouble_noActivatedKeyButDefaultsKeyExists_returnsDefaultsValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of(DOUBLE_KEY, DEFAULTS_DOUBLE_VALUE));

    double doubleValue = getHandler.getDouble(DOUBLE_KEY);

    assertThat(doubleValue).isEqualTo(DEFAULTS_DOUBLE_VALUE);
  }

  @Test
  public void getDouble_activatedAndDefaultsKeysDoNotExist_returnsStaticDefault() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of());

    double doubleValue = getHandler.getDouble(DOUBLE_KEY);

    assertThat(doubleValue).isEqualTo(DEFAULT_VALUE_FOR_DOUBLE);
  }

  @Test
  public void getDouble_activatedKeyExistsAndNoDefaultsConfigs_returnsActivatedValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(DOUBLE_KEY, ACTIVATED_DOUBLE_VALUE));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    double doubleValue = getHandler.getDouble(DOUBLE_KEY);

    assertThat(doubleValue).isEqualTo(ACTIVATED_DOUBLE_VALUE);
  }

  @Test
  public void getDouble_noActivatedConfigsButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of(DOUBLE_KEY, DEFAULTS_DOUBLE_VALUE));

    double doubleValue = getHandler.getDouble(DOUBLE_KEY);

    assertThat(doubleValue).isEqualTo(DEFAULTS_DOUBLE_VALUE);
  }

  @Test
  public void getDouble_activatedAndDefaultsConfigsDoNotExist_returnsStaticDefault() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    double doubleValue = getHandler.getDouble(DOUBLE_KEY);

    assertThat(doubleValue).isEqualTo(DEFAULT_VALUE_FOR_DOUBLE);
  }

  @Test
  public void getDouble_activatedStillLoadingFromDisk_blocksAndReturnsActivatedValue()
      throws Exception {
    loadActivatedAsyncCacheWithIncompleteTask();
    loadActivatedBlockingCacheWith(ImmutableMap.of(DOUBLE_KEY, ACTIVATED_DOUBLE_VALUE));

    double doubleValue = getHandler.getDouble(DOUBLE_KEY);

    assertThat(doubleValue).isEqualTo(ACTIVATED_DOUBLE_VALUE);
  }

  @Test
  public void getLong_activatedAndDefaultsKeysExist_returnsActivatedValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(LONG_KEY, ACTIVATED_LONG_VALUE));
    loadDefaultsCacheWithMap(ImmutableMap.of(LONG_KEY, DEFAULTS_LONG_VALUE));

    long longValue = getHandler.getLong(LONG_KEY);

    assertThat(longValue).isEqualTo(ACTIVATED_LONG_VALUE);
  }

  @Test
  public void getLong_noActivatedKeyButDefaultsKeyExists_returnsDefaultsValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of(LONG_KEY, DEFAULTS_LONG_VALUE));

    long longValue = getHandler.getLong(LONG_KEY);

    assertThat(longValue).isEqualTo(DEFAULTS_LONG_VALUE);
  }

  @Test
  public void getLong_activatedAndDefaultsKeysDoNotExist_returnsStaticDefault() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of());

    long longValue = getHandler.getLong(LONG_KEY);

    assertThat(longValue).isEqualTo(DEFAULT_VALUE_FOR_LONG);
  }

  @Test
  public void getLong_activatedKeyExistsAndNoDefaultsConfigs_returnsActivatedValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(LONG_KEY, ACTIVATED_LONG_VALUE));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    long longValue = getHandler.getLong(LONG_KEY);

    assertThat(longValue).isEqualTo(ACTIVATED_LONG_VALUE);
  }

  @Test
  public void getLong_noActivatedConfigsButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of(LONG_KEY, DEFAULTS_LONG_VALUE));

    long longValue = getHandler.getLong(LONG_KEY);

    assertThat(longValue).isEqualTo(DEFAULTS_LONG_VALUE);
  }

  @Test
  public void getLong_activatedAndDefaultsConfigsDoNotExist_returnsStaticDefault() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    long longValue = getHandler.getLong(LONG_KEY);

    assertThat(longValue).isEqualTo(DEFAULT_VALUE_FOR_LONG);
  }

  @Test
  public void getLong_activatedStillLoadingFromDisk_blocksAndReturnsActivatedValue()
      throws Exception {
    loadActivatedAsyncCacheWithIncompleteTask();
    loadActivatedBlockingCacheWith(ImmutableMap.of(LONG_KEY, ACTIVATED_LONG_VALUE));

    long longValue = getHandler.getLong(LONG_KEY);

    assertThat(longValue).isEqualTo(ACTIVATED_LONG_VALUE);
  }

  @Test
  public void getValue_activatedAndDefaultsKeysExist_returnsActivatedValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(STRING_KEY, ACTIVATED_STRING_VALUE));
    loadDefaultsCacheWithMap(ImmutableMap.of(STRING_KEY, DEFAULTS_STRING_VALUE));

    FirebaseRemoteConfigValue frcValue = getHandler.getValue(STRING_KEY);

    assertThat(frcValue.asString()).isEqualTo(ACTIVATED_STRING_VALUE);
  }

  @Test
  public void getValue_noActivatedKeyButDefaultsKeyExists_returnsDefaultsValue() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of(STRING_KEY, DEFAULTS_STRING_VALUE));

    FirebaseRemoteConfigValue frcValue = getHandler.getValue(STRING_KEY);

    assertThat(frcValue.asString()).isEqualTo(DEFAULTS_STRING_VALUE);
  }

  @Test
  public void getValue_activatedAndDefaultsKeysDoNotExist_returnsStaticDefault() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of());

    FirebaseRemoteConfigValue frcValue = getHandler.getValue(STRING_KEY);

    assertThat(frcValue.asString()).isEqualTo(DEFAULT_VALUE_FOR_STRING);
  }

  @Test
  public void getValue_activatedKeyExistsAndNoDefaultsConfigs_returnsActivatedValue()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of(STRING_KEY, ACTIVATED_STRING_VALUE));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    FirebaseRemoteConfigValue frcValue = getHandler.getValue(STRING_KEY);

    assertThat(frcValue.asString()).isEqualTo(ACTIVATED_STRING_VALUE);
  }

  @Test
  public void getValue_noActivatedConfigsButDefaultsKeyExists_returnsDefaultsValue()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of(STRING_KEY, DEFAULTS_STRING_VALUE));

    FirebaseRemoteConfigValue frcValue = getHandler.getValue(STRING_KEY);

    assertThat(frcValue.asString()).isEqualTo(DEFAULTS_STRING_VALUE);
  }

  @Test
  public void getValue_activatedAndDefaultsConfigsDoNotExist_returnsStaticDefault() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    FirebaseRemoteConfigValue frcValue = getHandler.getValue(STRING_KEY);

    assertThat(frcValue.asString()).isEqualTo(DEFAULT_VALUE_FOR_STRING);
  }

  @Test
  public void getValue_activatedStillLoadingFromDisk_blocksAndReturnsActivatedValue()
      throws Exception {
    loadActivatedAsyncCacheWithIncompleteTask();
    loadActivatedBlockingCacheWith(ImmutableMap.of(STRING_KEY, ACTIVATED_STRING_VALUE));

    FirebaseRemoteConfigValue frcValue = getHandler.getValue(STRING_KEY);

    assertThat(frcValue.asString()).isEqualTo(ACTIVATED_STRING_VALUE);
  }

  @Test
  public void getKeysByPrefix_nullPrefix_returnsAllKeys() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of("pre11", "", "pre21", "", "pre222", ""));
    loadDefaultsCacheWithMap(ImmutableMap.of("pre31", "", "pre122", "", "pre2333", ""));

    Set<String> keys = getHandler.getKeysByPrefix(null);

    assertThat(keys).containsExactly("pre11", "pre122", "pre21", "pre222", "pre2333", "pre31");
  }

  @Test
  public void getKeysByPrefix_emptyPrefix_returnsAllKeys() throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of("pre11", "", "pre21", "", "pre222", ""));
    loadDefaultsCacheWithMap(ImmutableMap.of("pre31", "", "pre122", "", "pre2333", ""));

    Set<String> keys = getHandler.getKeysByPrefix("");

    assertThat(keys).containsExactly("pre11", "pre122", "pre21", "pre222", "pre2333", "pre31");
  }

  @Test
  public void getKeysByPrefix_activatedAndDefaultsKeysExist_returnsAllKeysWithPrefix()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of("pa", "", "pre11", "", "pre21", "", "pre222", ""));
    loadDefaultsCacheWithMap(ImmutableMap.of("pre31", "", "pre122", "", "no", "", "pre2333", ""));

    Set<String> keys = getHandler.getKeysByPrefix("pre2");

    assertThat(keys).containsExactly("pre21", "pre222", "pre2333");
  }

  @Test
  public void getKeysByPrefix_noActivatedKeysButDefaultsKeysExist_returnsDefaultsKeys()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of("pre11", "", "pre21", "", "pre122", ""));

    Set<String> keys = getHandler.getKeysByPrefix("pre2");

    assertThat(keys).containsExactly("pre21");
  }

  @Test
  public void getKeysByPrefix_activatedAndDefaultsKeysDoNotExist_returnsEmptySet()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of());
    loadDefaultsCacheWithMap(ImmutableMap.of());

    Set<String> keys = getHandler.getKeysByPrefix("pre2");

    assertThat(keys).isEmpty();
  }

  @Test
  public void getKeysByPrefix_activatedKeysExistAndNoDefaultsConfigs_returnsActivatedKeys()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of("pre11", "", "pre21", "", "pre122", ""));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    Set<String> keys = getHandler.getKeysByPrefix("pre2");

    assertThat(keys).containsExactly("pre21");
  }

  @Test
  public void getKeysByPrefix_noActivatedConfigsButDefaultsKeysExist_returnsDefaultsKeys()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of("pre11", "", "pre21", "", "pre12", ""));

    Set<String> keys = getHandler.getKeysByPrefix("pre2");

    assertThat(keys).containsExactly("pre21");
  }

  @Test
  public void getKeysByPrefix_activatedAndDefaultsConfigsDoNotExist_returnsEmptySet() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    Set<String> keys = getHandler.getKeysByPrefix("pre2");

    assertThat(keys).isEmpty();
  }

  @Test
  public void getKeysByPrefix_activatedStillLoadingFromDisk_blocksAndReturnsActivatedValue()
      throws Exception {
    loadActivatedAsyncCacheWithIncompleteTask();
    loadActivatedBlockingCacheWith(ImmutableMap.of("pre11", "", "pre21", "", "pre122", ""));

    loadDefaultsCacheWithMap(ImmutableMap.of("pre13", "", "pre31", ""));

    Set<String> keys = getHandler.getKeysByPrefix("pre1");

    assertThat(keys).containsExactly("pre11", "pre122", "pre13");
  }

  @Test
  public void getAll_activatedAndDefaultsConfigsDoNotExist_returnsEmptyMap() {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    Map<String, FirebaseRemoteConfigValue> configs = getHandler.getAll();

    assertThat(configs.keySet()).isEmpty();
  }

  @Test
  public void getAll_activatedKeyExistsAndNoDefaultsConfigs_returnsActivatedValues()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of("pre11", "activated_foo", "pre21", "activated_bar"));
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);

    Map<String, FirebaseRemoteConfigValue> configs = getHandler.getAll();

    assertThat(configs.keySet()).isEqualTo(ImmutableSet.of("pre11", "pre21"));
    assertThat(configs.get("pre11").asString()).isEqualTo("activated_foo");
    assertThat(configs.get("pre21").asString()).isEqualTo("activated_bar");
  }

  @Test
  public void getAll_noActivatedConfigsButDefaultsKeysExist_returnsDefaultsValuess()
      throws Exception {
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadDefaultsCacheWithMap(ImmutableMap.of("pre11", "default_foo", "pre21", "default_bar"));

    Map<String, FirebaseRemoteConfigValue> configs = getHandler.getAll();

    assertThat(configs.keySet()).isEqualTo(ImmutableSet.of("pre11", "pre21"));
    assertThat(configs.get("pre11").asString()).isEqualTo("default_foo");
    assertThat(configs.get("pre21").asString()).isEqualTo("default_bar");
  }

  @Test
  public void getAll_activatedAndDefaultKeysExist_returnsCorrectlyPrioritizedValues()
      throws Exception {
    loadActivatedCacheWithMap(ImmutableMap.of("pre11", "activated_foo"));
    loadDefaultsCacheWithMap(ImmutableMap.of("pre11", "default_foo", "pre21", "default_bar"));

    Map<String, FirebaseRemoteConfigValue> configs = getHandler.getAll();
    assertThat(configs.keySet()).containsAtLeastElementsIn(ImmutableSet.of("pre11", "pre21"));
    assertThat(configs.get("pre11").asString()).isEqualTo("activated_foo");
    assertThat(configs.get("pre21").asString()).isEqualTo("default_bar");
  }

  private void loadActivatedCacheWithMap(ImmutableMap<String, Object> configsMap) throws Exception {
    loadCacheWithConfig(mockActivatedCache, newContainer(convertToStringsMap(configsMap)));
  }

  private void loadDefaultsCacheWithMap(ImmutableMap<String, Object> configsMap) throws Exception {
    loadCacheWithConfig(
        mockDefaultsCache, newDefaultsStringContainer(convertToStringsMap(configsMap)));
  }

  private void loadActivatedBlockingCacheWith(ImmutableMap<String, Object> configsMap)
      throws Exception {
    when(mockActivatedCache.getBlocking())
        .thenReturn(newContainer(convertToStringsMap(configsMap)));
  }

  private void loadActivatedAsyncCacheWithIncompleteTask() {
    TaskCompletionSource<ConfigContainer> taskSource = new TaskCompletionSource<>();
    when(mockActivatedCache.get()).thenReturn(taskSource.getTask());
  }

  private static void loadCacheWithConfig(
      ConfigCacheClient cacheClient, ConfigContainer container) {
    when(cacheClient.getBlocking()).thenReturn(container);
    when(cacheClient.get()).thenReturn(Tasks.forResult(container));
  }

  private static ConfigContainer newContainer(Map<String, String> configsMap) throws Exception {
    return ConfigContainer.newBuilder()
        .replaceConfigsWith(configsMap)
        .withFetchTime(new Date(555L))
        .build();
  }

  private static ConfigContainer newDefaultsStringContainer(Map<String, String> configsMap)
      throws Exception {
    return ConfigContainer.newBuilder()
        .replaceConfigsWith(configsMap)
        .withFetchTime(new Date(0L))
        .build();
  }

  private static Map<String, String> convertToStringsMap(Map<String, Object> objectsMap) {
    Map<String, String> stringsMap = new HashMap<>();
    for (Map.Entry<String, Object> objectEntry : objectsMap.entrySet()) {
      stringsMap.put(objectEntry.getKey(), objectEntry.getValue().toString());
    }
    return stringsMap;
  }
}
