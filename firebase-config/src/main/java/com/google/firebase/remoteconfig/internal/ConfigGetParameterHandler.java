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

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BOOLEAN;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BYTE_ARRAY;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_DOUBLE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_LONG;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_STRING;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_REMOTE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_STATIC;

import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.util.BiConsumer;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A handler for getting values stored in the Firebase Remote Config (FRC) SDK.
 *
 * <p>Provides methods to return parameter values as one of the six types supported by FRC: {@code
 * booolean}, {@code byte[]}, {@code double}, {@code long}, {@link FirebaseRemoteConfigValue}, and
 * {@link String}.
 *
 * <p>Evaluates the value of a parameter in the following order:
 *
 * <ol>
 *   <li>The activated value, if the activated {@link ConfigCacheClient} contains the key.
 *   <li>The default value, if the defaults {@link ConfigCacheClient} contains the key.
 *   <li>The static default value for the given type, as defined in the static constants of {@link
 *       FirebaseRemoteConfig}.
 * </ol>
 *
 * @author Miraziz Yusupov
 */
public class ConfigGetParameterHandler {
  /** Byte arrays in FRC are encoded as UTF-8 Strings. */
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public static final Charset FRC_BYTE_ARRAY_ENCODING = Charset.forName("UTF-8");
  /** Regular expressions that will evaluate to a "true" boolean. */
  static final Pattern TRUE_REGEX =
      Pattern.compile("^(1|true|t|yes|y|on)$", Pattern.CASE_INSENSITIVE);
  /** Regular expressions that will evaluate to a "false" boolean. */
  static final Pattern FALSE_REGEX =
      Pattern.compile("^(0|false|f|no|n|off|)$", Pattern.CASE_INSENSITIVE);

  private final Set<BiConsumer<String, ConfigContainer>> listeners = new HashSet<>();

  private final Executor executor;
  private final ConfigCacheClient activatedConfigsCache;
  private final ConfigCacheClient defaultConfigsCache;

  public ConfigGetParameterHandler(
      Executor executor,
      ConfigCacheClient activatedConfigsCache,
      ConfigCacheClient defaultConfigsCache) {
    this.executor = executor;
    this.activatedConfigsCache = activatedConfigsCache;
    this.defaultConfigsCache = defaultConfigsCache;
  }

  /**
   * Returns the parameter value of the given parameter key as a {@link String}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The value in the activated cache, if the key exists.
   *   <li>The value in the defaults cache, if the key exists.
   *   <li>{@link FirebaseRemoteConfig#DEFAULT_VALUE_FOR_STRING}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key.
   */
  public String getString(String key) {
    String activatedString = getStringFromCache(activatedConfigsCache, key);
    if (activatedString != null) {
      callListeners(key, getConfigsFromCache(activatedConfigsCache));
      return activatedString;
    }

    String defaultsString = getStringFromCache(defaultConfigsCache, key);
    if (defaultsString != null) {
      return defaultsString;
    }

    logParameterValueDoesNotExist(key, "String");
    return DEFAULT_VALUE_FOR_STRING;
  }

  /**
   * Returns the parameter value of the given parameter key as a {@code boolean}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The value in the activated cache, if the key exists and the value can be converted into a
   *       {@code boolean}.
   *   <li>The value in the defaults cache, if the key exists and the value can be converted into a
   *       {@code boolean}.
   *   <li>{@link FirebaseRemoteConfig#DEFAULT_VALUE_FOR_BOOLEAN}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key with a {@code boolean} parameter value.
   */
  public boolean getBoolean(String key) {
    String activatedString = getStringFromCache(activatedConfigsCache, key);
    if (activatedString != null) {
      if (TRUE_REGEX.matcher(activatedString).matches()) {
        callListeners(key, getConfigsFromCache(activatedConfigsCache));
        return true;
      } else if (FALSE_REGEX.matcher(activatedString).matches()) {
        callListeners(key, getConfigsFromCache(activatedConfigsCache));
        return false;
      }
    }

    String defaultsString = getStringFromCache(defaultConfigsCache, key);
    if (defaultsString != null) {
      if (TRUE_REGEX.matcher(defaultsString).matches()) {
        return true;
      } else if (FALSE_REGEX.matcher(defaultsString).matches()) {
        return false;
      }
    }

    logParameterValueDoesNotExist(key, "Boolean");
    return DEFAULT_VALUE_FOR_BOOLEAN;
  }

  /**
   * Returns the parameter value of the given parameter key as a {@code byte[]}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The value in the activated cache, if the key exists.
   *   <li>The value in the defaults cache, if the key exists.
   *   <li>{@link FirebaseRemoteConfig#DEFAULT_VALUE_FOR_BYTE_ARRAY}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key.
   */
  public byte[] getByteArray(String key) {
    String activatedString = getStringFromCache(activatedConfigsCache, key);
    if (activatedString != null) {
      callListeners(key, getConfigsFromCache(activatedConfigsCache));
      return activatedString.getBytes(FRC_BYTE_ARRAY_ENCODING);
    }

    String defaultsString = getStringFromCache(defaultConfigsCache, key);
    if (defaultsString != null) {
      return defaultsString.getBytes(FRC_BYTE_ARRAY_ENCODING);
    }

    logParameterValueDoesNotExist(key, "ByteArray");
    return DEFAULT_VALUE_FOR_BYTE_ARRAY;
  }

  /**
   * Returns the parameter value of the given parameter key as a {@code double}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The value in the activated cache, if the key exists and the value can be converted into a
   *       {@code double}.
   *   <li>The value in the defaults cache, if the key exists and the value can be converted into a
   *       {@code double}.
   *   <li>{@link FirebaseRemoteConfig#DEFAULT_VALUE_FOR_DOUBLE}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key with a {@code double} parameter value.
   */
  public double getDouble(String key) {
    Double activatedDouble = getDoubleFromCache(activatedConfigsCache, key);
    if (activatedDouble != null) {
      callListeners(key, getConfigsFromCache(activatedConfigsCache));
      return activatedDouble;
    }

    Double defaultsDouble = getDoubleFromCache(defaultConfigsCache, key);
    if (defaultsDouble != null) {
      return defaultsDouble;
    }

    logParameterValueDoesNotExist(key, "Double");
    return DEFAULT_VALUE_FOR_DOUBLE;
  }

  /**
   * Returns the parameter value of the given parameter key as a {@code long}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The value in the activated cache, if the key exists and the value can be converted into a
   *       {@code long}.
   *   <li>The value in the defaults cache, if the key exists and the value can be converted into a
   *       {@code long}.
   *   <li>{@link FirebaseRemoteConfig#DEFAULT_VALUE_FOR_LONG}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key with a {@code long} parameter value.
   */
  public long getLong(String key) {
    Long activatedLong = getLongFromCache(activatedConfigsCache, key);
    if (activatedLong != null) {
      callListeners(key, getConfigsFromCache(activatedConfigsCache));
      return activatedLong;
    }

    Long defaultsLong = getLongFromCache(defaultConfigsCache, key);
    if (defaultsLong != null) {
      return defaultsLong;
    }

    logParameterValueDoesNotExist(key, "Long");
    return DEFAULT_VALUE_FOR_LONG;
  }

  /**
   * Returns the parameter value of the given parameter key as a {@link FirebaseRemoteConfigValue}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The value in the activated cache, if the key exists.
   *   <li>The value in the defaults cache, if the key exists.
   *   <li>A {@link FirebaseRemoteConfigValue} that returns the static value for each type.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key.
   */
  public FirebaseRemoteConfigValue getValue(String key) {
    String activatedString = getStringFromCache(activatedConfigsCache, key);
    if (activatedString != null) {
      callListeners(key, getConfigsFromCache(activatedConfigsCache));
      return new FirebaseRemoteConfigValueImpl(activatedString, VALUE_SOURCE_REMOTE);
    }

    String defaultsString = getStringFromCache(defaultConfigsCache, key);
    if (defaultsString != null) {
      return new FirebaseRemoteConfigValueImpl(defaultsString, VALUE_SOURCE_DEFAULT);
    }

    logParameterValueDoesNotExist(key, "FirebaseRemoteConfigValue");
    return new FirebaseRemoteConfigValueImpl(DEFAULT_VALUE_FOR_STRING, VALUE_SOURCE_STATIC);
  }

  /**
   * Returns an ordered {@link Set} of all the FRC keys with the given prefix.
   *
   * <p>The set will contain all the keys in the activated and defaults configs with the given
   * prefix.
   *
   * @param prefix A prefix for FRC keys. If the prefix is empty, all keys are returned.
   */
  public Set<String> getKeysByPrefix(String prefix) {
    if (prefix == null) {
      prefix = "";
    }

    TreeSet<String> keysWithPrefix = new TreeSet<>();

    ConfigContainer activatedConfigs = getConfigsFromCache(activatedConfigsCache);
    if (activatedConfigs != null) {
      keysWithPrefix.addAll(getKeysByPrefix(prefix, activatedConfigs));
    }

    ConfigContainer defaultsConfigs = getConfigsFromCache(defaultConfigsCache);
    if (defaultsConfigs != null) {
      keysWithPrefix.addAll(getKeysByPrefix(prefix, defaultsConfigs));
    }

    return keysWithPrefix;
  }

  /** Returns a {@link TreeSet} of the keys in {@code configs} with the given prefix. */
  private static TreeSet<String> getKeysByPrefix(String prefix, ConfigContainer configs) {
    TreeSet<String> keysWithPrefix = new TreeSet<>();

    Iterator<String> stringIterator = configs.getConfigs().keys();
    while (stringIterator.hasNext()) {
      String currentKey = stringIterator.next();
      if (currentKey.startsWith(prefix)) {
        keysWithPrefix.add(currentKey);
      }
    }

    return keysWithPrefix;
  }

  /**
   * Returns {@link Map} of FRC key value pairs.
   *
   * <p>Evaluates the values of the parameters in the following order:
   *
   * <ol>
   *   <li>The value in the activated cache, if the key exists.
   *   <li>The value in the defaults cache, if the key exists.
   * </ol>
   */
  public Map<String, FirebaseRemoteConfigValue> getAll() {
    Set<String> keySet = new HashSet<>();
    keySet.addAll(getKeySetFromCache(activatedConfigsCache));
    keySet.addAll(getKeySetFromCache(defaultConfigsCache));

    HashMap<String, FirebaseRemoteConfigValue> allConfigs = new HashMap<>();
    for (String key : keySet) {
      allConfigs.put(key, getValue(key));
    }
    return allConfigs;
  }

  /**
   * Adds a listener that will be called whenever one of the get methods is called, passing in the
   * retrieved key from a get*() call and the config containing the key.
   *
   * @param listener function that takes in the parameter key and the {@link ConfigContainer}
   */
  public void addListener(BiConsumer<String, ConfigContainer> listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  /**
   * Calls all listeners added to {@link #listeners} with the retrieved key from a get*() call and
   * the config containing the key.
   *
   * @param key Remote Config parameter key
   * @param container {@link ConfigContainer} containing {@code key}
   */
  private void callListeners(String key, ConfigContainer container) {
    if (container == null) {
      return;
    }

    synchronized (listeners) {
      for (BiConsumer<String, ConfigContainer> listener : listeners) {
        executor.execute(() -> listener.accept(key, container));
      }
    }
  }

  /**
   * Returns the FRC parameter value for the given key in the given cache as a {@link String}, or
   * {@code null} if the key does not exist in the cache.
   *
   * @param cacheClient the cache client the parameter is stored in.
   * @param key the FRC parameter key.
   */
  @Nullable
  private static String getStringFromCache(ConfigCacheClient cacheClient, String key) {
    ConfigContainer cachedContainer = getConfigsFromCache(cacheClient);
    if (cachedContainer == null) {
      return null;
    }

    try {
      return cachedContainer.getConfigs().getString(key);
    } catch (JSONException ignored) {
      return null;
    }
  }

  /**
   * Returns the FRC parameter value for the given key in the given cache as a {@link Double}, or
   * {@code null} if the key does not have a {@code double} value in the cache.
   */
  @Nullable
  private static Double getDoubleFromCache(ConfigCacheClient cacheClient, String key) {
    ConfigContainer cachedContainer = getConfigsFromCache(cacheClient);
    if (cachedContainer == null) {
      return null;
    }

    try {
      return cachedContainer.getConfigs().getDouble(key);
    } catch (JSONException ignored) {
      return null;
    }
  }

  /**
   * Returns the FRC parameter value for the given key in the given cache as a {@link Long}, or
   * {@code null} if the key does not have a {@code long} value in the cache.
   */
  @Nullable
  private static Long getLongFromCache(ConfigCacheClient cacheClient, String key) {
    ConfigContainer cachedContainer = getConfigsFromCache(cacheClient);
    if (cachedContainer == null) {
      return null;
    }

    try {
      return cachedContainer.getConfigs().getLong(key);
    } catch (JSONException ignored) {
      return null;
    }
  }

  /** Returns all FRC parameter keys in the given cache. */
  private static Set<String> getKeySetFromCache(ConfigCacheClient cacheClient) {
    Set<String> keySet = new HashSet<>();
    ConfigContainer configContainer = getConfigsFromCache(cacheClient);
    if (configContainer == null) {
      return keySet;
    }

    JSONObject configs = configContainer.getConfigs();
    Iterator<String> keyIterator = configs.keys();
    while (keyIterator.hasNext()) {
      keySet.add(keyIterator.next());
    }
    return keySet;
  }

  /**
   * Returns the FRC configs in the given cache as {@link ConfigContainer} or {@code null} if there
   * are no configs in the cache.
   */
  @Nullable
  private static ConfigContainer getConfigsFromCache(ConfigCacheClient cacheClient) {
    return cacheClient.getBlocking();
  }

  private static void logParameterValueDoesNotExist(String key, String valueType) {
    Log.w(
        TAG, String.format("No value of type '%s' exists for parameter key '%s'.", valueType, key));
  }
}
