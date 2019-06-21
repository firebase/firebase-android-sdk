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

//  TODO(issues/261): Remove with the next major change release of FRC.

package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.ConfigHolder;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.KeyValue;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.NamespaceKeyValue;
import com.google.firebase.remoteconfig.proto.ConfigPersistence.PersistedConfig;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import developers.mobile.abt.FirebaseAbt.ExperimentPayload;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handler for reading and converting configs stored as protos by the older versions of the FRC SDK,
 * as well as writing those protos to the appropriate {@link ConfigCacheClient}s.
 *
 * <p>The legacy SDK stores configs in the App's disk space as a {@link PersistedConfig}. The new
 * SDK stores values as JSON, so clients will lose their state after updating to the new SDK. To
 * avoid such a breaking change, this class reads the proto in the old SDK's file and writes it to
 * the new SDK's files.
 *
 * <p>To prevent the legacy configs from overwriting new fetched values, the legacy configs will
 * only be written to the new SDK once. After the first write in the new SDK, legacy configs will be
 * permanently ignored.
 *
 * @author Miraziz Yusupov
 */
public class LegacyConfigsHandler {
  @VisibleForTesting public static final String EXPERIMENT_ID_KEY = "experimentId";
  @VisibleForTesting public static final String EXPERIMENT_VARIANT_ID_KEY = "variantId";
  @VisibleForTesting public static final String EXPERIMENT_START_TIME_KEY = "experimentStartTime";
  @VisibleForTesting public static final String EXPERIMENT_TRIGGER_EVENT_KEY = "triggerEvent";

  @VisibleForTesting
  public static final String EXPERIMENT_TRIGGER_TIMEOUT_KEY = "triggerTimeoutMillis";

  @VisibleForTesting public static final String EXPERIMENT_TIME_TO_LIVE_KEY = "timeToLiveMillis";

  /** Name of the file with the legacy configs proto. */
  @VisibleForTesting static final String LEGACY_CONFIGS_FILE_NAME = "persisted_config";
  /**
   * Name of the file with the flag to determine if legacy configs should be read and saved to the
   * new SDK.
   */
  private static final String LEGACY_SETTINGS_FILE_NAME =
      "com.google.firebase.remoteconfig_legacy_settings";

  private static final String SAVE_LEGACY_CONFIGS_FLAG_NAME = "save_legacy_configs";

  /**
   * The legacy FRC server prepended all namespaces with "configns:", while the current FRC server
   * does not. The legacy proto will have namespaces with the legacy format, but the converted
   * configs need to be saved to namespaces without the prefix.
   */
  @VisibleForTesting static final String LEGACY_FRC_NAMESPACE_PREFIX = "configns:";

  /** The default namespace for all 3P configs. */
  private static final String FRC_3P_NAMESPACE = "firebase";

  /** The encoding used to serialize the legacy FRC and ABT protos. */
  private static final Charset PROTO_BYTE_ARRAY_ENCODING = Charset.forName("UTF-8");

  /** Name of the file where activate configs are stored. */
  @VisibleForTesting
  static final String ACTIVATE_FILE_NAME = RemoteConfigComponent.ACTIVATE_FILE_NAME;

  /** Name of the file where fetched configs are stored. */
  @VisibleForTesting static final String FETCH_FILE_NAME = RemoteConfigComponent.FETCH_FILE_NAME;

  /** Name of the file where defaults configs are stored. */
  @VisibleForTesting
  static final String DEFAULTS_FILE_NAME = RemoteConfigComponent.DEFAULTS_FILE_NAME;

  /**
   * The String format of a protobuf Timestamp; the format is ISO 8601 compliant.
   *
   * <p>The protobuf Timestamp field gets converted to an ISO 8601 string when returned as JSON. For
   * example, the Firebase Remote Config backend sends experiment start time as a Timestamp field,
   * which gets converted to an ISO 8601 string when sent as JSON.
   */
  @VisibleForTesting
  static final ThreadLocal<DateFormat> protoTimestampStringParser =
      new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
          return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        }
      };

  private final Context context;
  private final String appId;
  private final SharedPreferences legacySettings;

  /** The Legacy Configs Handler constructor. */
  public LegacyConfigsHandler(Context context, String appId) {
    this.context = context;
    this.appId = appId;

    this.legacySettings =
        context.getSharedPreferences(LEGACY_SETTINGS_FILE_NAME, Context.MODE_PRIVATE);
  }

  /**
   * The first time this method is ever called, any existing legacy configs are read and saved to
   * disk. At the end of the first invocation of this method, a persisted flag will be switched to
   * false and all subsequent calls to this method will be ignored.
   */
  @WorkerThread
  public boolean saveLegacyConfigsIfNecessary() {
    if (legacySettings.getBoolean(SAVE_LEGACY_CONFIGS_FLAG_NAME, true)) {
      saveLegacyConfigs(getConvertedLegacyConfigs());
      legacySettings.edit().putBoolean(SAVE_LEGACY_CONFIGS_FLAG_NAME, false).commit();
      return true;
    }
    return false;
  }

  /**
   * Saves all the configs in {@code legacyConfigsByNamespace} to disk.
   *
   * @param legacyConfigsByNamespace a map from namespaces to {@link NamespaceLegacyConfigs}, each
   *     of which contains the activated, fetched and defaults legacy configs for a single
   *     namespace.
   */
  @WorkerThread
  private void saveLegacyConfigs(Map<String, NamespaceLegacyConfigs> legacyConfigsByNamespace) {
    for (Map.Entry<String, NamespaceLegacyConfigs> legacyConfigsByNamespaceEntry :
        legacyConfigsByNamespace.entrySet()) {
      String namespace = legacyConfigsByNamespaceEntry.getKey();
      NamespaceLegacyConfigs legacyConfigs = legacyConfigsByNamespaceEntry.getValue();

      ConfigCacheClient fetchedCacheClient = getCacheClient(namespace, FETCH_FILE_NAME);
      ConfigCacheClient activatedCacheClient = getCacheClient(namespace, ACTIVATE_FILE_NAME);
      ConfigCacheClient defaultsCacheClient = getCacheClient(namespace, DEFAULTS_FILE_NAME);

      if (legacyConfigs.getFetchedConfigs() != null) {
        fetchedCacheClient.put(legacyConfigs.getFetchedConfigs());
      }
      if (legacyConfigs.getActivatedConfigs() != null) {
        activatedCacheClient.put(legacyConfigs.getActivatedConfigs());
      }
      if (legacyConfigs.getDefaultsConfigs() != null) {
        defaultsCacheClient.put(legacyConfigs.getDefaultsConfigs());
      }
    }
  }

  /**
   * Reads all legacy configs from disk and converts them into {@link ConfigContainer}s.
   *
   * @return A {@link Map} from namespaces to {@link NamespaceLegacyConfigs}, each of which contains
   *     the activated, fetched and defaults legacy configs for a single namespace.
   */
  @WorkerThread
  private Map<String, NamespaceLegacyConfigs> getConvertedLegacyConfigs() {
    PersistedConfig allLegacyConfigs = readPersistedConfig();

    Map<String, NamespaceLegacyConfigs> allConfigsMap = new HashMap<>();
    if (allLegacyConfigs == null) {
      return allConfigsMap;
    }

    Map<String, ConfigContainer> activatedConfigsByNamespace =
        convertConfigHolder(allLegacyConfigs.getActiveConfigHolder());
    Map<String, ConfigContainer> fetchedConfigsByNamespace =
        convertConfigHolder(allLegacyConfigs.getFetchedConfigHolder());
    Map<String, ConfigContainer> defaultsConfigsByNamespace =
        convertConfigHolder(allLegacyConfigs.getDefaultsConfigHolder());

    Set<String> allNamespaces = new HashSet<>();
    allNamespaces.addAll(activatedConfigsByNamespace.keySet());
    allNamespaces.addAll(fetchedConfigsByNamespace.keySet());
    allNamespaces.addAll(defaultsConfigsByNamespace.keySet());

    for (String namespace : allNamespaces) {
      NamespaceLegacyConfigs namespaceLegacyConfigs = new NamespaceLegacyConfigs();
      if (activatedConfigsByNamespace.containsKey(namespace)) {
        namespaceLegacyConfigs.setActivatedConfigs(activatedConfigsByNamespace.get(namespace));
      }
      if (fetchedConfigsByNamespace.containsKey(namespace)) {
        namespaceLegacyConfigs.setFetchedConfigs(fetchedConfigsByNamespace.get(namespace));
      }
      if (defaultsConfigsByNamespace.containsKey(namespace)) {
        namespaceLegacyConfigs.setDefaultsConfigs(defaultsConfigsByNamespace.get(namespace));
      }
      allConfigsMap.put(namespace, namespaceLegacyConfigs);
    }
    return allConfigsMap;
  }

  /** Converts {@link ConfigHolder} into a map from namespaces to their corresponding configs. */
  private Map<String, ConfigContainer> convertConfigHolder(ConfigHolder allNamespaceLegacyConfigs) {
    Map<String, ConfigContainer> convertedLegacyConfigs = new HashMap<>();

    Date fetchTime = new Date(allNamespaceLegacyConfigs.getTimestamp());
    JSONArray abtExperiments =
        convertLegacyAbtExperiments(allNamespaceLegacyConfigs.getExperimentPayloadList());

    List<NamespaceKeyValue> namespaceLegacyConfigsArray =
        allNamespaceLegacyConfigs.getNamespaceKeyValueList();
    for (NamespaceKeyValue namespaceLegacyConfigs : namespaceLegacyConfigsArray) {
      String namespace = namespaceLegacyConfigs.getNamespace();
      if (namespace.startsWith(LEGACY_FRC_NAMESPACE_PREFIX)) {
        namespace = namespace.substring(LEGACY_FRC_NAMESPACE_PREFIX.length());
      }

      ConfigContainer.Builder configsBuilder =
          ConfigContainer.newBuilder()
              .replaceConfigsWith(convertKeyValueList(namespaceLegacyConfigs.getKeyValueList()))
              .withFetchTime(fetchTime);
      if (namespace.equals(FRC_3P_NAMESPACE)) {
        configsBuilder.withAbtExperiments(abtExperiments);
      }

      try {
        convertedLegacyConfigs.put(namespace, configsBuilder.build());
      } catch (JSONException e) {
        // Skip configs that cannot be parsed.
        Log.d(TAG, "A set of legacy configs could not be converted.");
      }
    }
    return convertedLegacyConfigs;
  }

  /**
   * Deserializes ABT experiment payloads and converts them into a {@link JSONArray} of {@link
   * JSONObject}s.
   */
  private JSONArray convertLegacyAbtExperiments(List<ByteString> legacyExperimentPayloads) {
    JSONArray abtExperiments = new JSONArray();
    for (ByteString legacyExperimentPayload : legacyExperimentPayloads) {
      ExperimentPayload deserializedPayload = deserializePayload(legacyExperimentPayload);
      if (deserializedPayload != null) {
        try {
          abtExperiments.put(convertLegacyAbtExperiment(deserializedPayload));
        } catch (JSONException e) {
          // Ignore ABT experiments that cannot be parsed.
          Log.d(TAG, "A legacy ABT experiment could not be parsed.", e);
        }
      }
    }
    return abtExperiments;
  }

  @Nullable
  private ExperimentPayload deserializePayload(ByteString legacyExperimentPayload) {
    try {
      Iterator<Byte> byteIterator = legacyExperimentPayload.iterator();
      byte[] payloadArray = new byte[legacyExperimentPayload.size()];
      for (int index = 0; index < payloadArray.length; index++) {
        payloadArray[index] = byteIterator.next();
      }
      return ExperimentPayload.parseFrom(payloadArray);
    } catch (InvalidProtocolBufferException e) {
      Log.d(TAG, "Payload was not defined or could not be deserialized.", e);
      return null;
    }
  }

  /** Converts {@link ExperimentPayload} into a {@link JSONObject}. */
  private JSONObject convertLegacyAbtExperiment(ExperimentPayload deserializedLegacyPayload)
      throws JSONException {
    JSONObject abtExperiment = new JSONObject();

    abtExperiment.put(EXPERIMENT_ID_KEY, deserializedLegacyPayload.getExperimentId());
    abtExperiment.put(EXPERIMENT_VARIANT_ID_KEY, deserializedLegacyPayload.getVariantId());
    abtExperiment.put(
        EXPERIMENT_START_TIME_KEY,
        protoTimestampStringParser
            .get()
            .format(new Date(deserializedLegacyPayload.getExperimentStartTimeMillis())));
    abtExperiment.put(EXPERIMENT_TRIGGER_EVENT_KEY, deserializedLegacyPayload.getTriggerEvent());
    abtExperiment.put(
        EXPERIMENT_TRIGGER_TIMEOUT_KEY, deserializedLegacyPayload.getTriggerTimeoutMillis());
    abtExperiment.put(EXPERIMENT_TIME_TO_LIVE_KEY, deserializedLegacyPayload.getTimeToLiveMillis());

    return abtExperiment;
  }

  /** Converts a {@link List} of {@link KeyValue}s into a {@link Map} of {@link String} configs. */
  private Map<String, String> convertKeyValueList(List<KeyValue> legacyConfigs) {
    Map<String, String> legacyConfigsMap = new HashMap<>();
    for (KeyValue legacyConfig : legacyConfigs) {
      legacyConfigsMap.put(
          legacyConfig.getKey(), legacyConfig.getValue().toString(PROTO_BYTE_ARRAY_ENCODING));
    }
    return legacyConfigsMap;
  }

  /** Reads the legacy configs, converts them into a proto, and returns the result. */
  @WorkerThread
  private PersistedConfig readPersistedConfig() {
    if (context == null) {
      return null;
    }
    PersistedConfig persistedConfig;
    FileInputStream fileInputStream = null;
    try {
      fileInputStream = context.openFileInput(LEGACY_CONFIGS_FILE_NAME);
      persistedConfig = PersistedConfig.parseFrom(fileInputStream);
    } catch (FileNotFoundException fileNotFoundException) {
      Log.d(TAG, "Persisted config file was not found.", fileNotFoundException);
      return null;
    } catch (IOException ioException) {
      Log.d(TAG, "Cannot initialize from persisted config.", ioException);
      return null;
    } finally {
      try {
        if (fileInputStream != null) {
          fileInputStream.close();
        }
      } catch (IOException ioException) {
        Log.d(TAG, "Failed to close persisted config file.", ioException);
      }
    }
    return persistedConfig;
  }

  /**
   * Gets the cache client for the given {@code namespace} and config storage type (one of {@link
   * #ACTIVATE_FILE_NAME}, {@link #FETCH_FILE_NAME} or {@link #DEFAULTS_FILE_NAME}).
   */
  ConfigCacheClient getCacheClient(String namespace, String configStoreType) {
    return RemoteConfigComponent.getCacheClient(context, appId, namespace, configStoreType);
  }

  /** Container for all the configs in a single namespace. */
  private static class NamespaceLegacyConfigs {
    private ConfigContainer fetchedConfigs;
    private ConfigContainer activatedConfigs;
    private ConfigContainer defaultsConfigs;

    private NamespaceLegacyConfigs() {}

    private void setFetchedConfigs(ConfigContainer fetchedConfigs) {
      this.fetchedConfigs = fetchedConfigs;
    }

    private void setActivatedConfigs(ConfigContainer activatedConfigs) {
      this.activatedConfigs = activatedConfigs;
    }

    private void setDefaultsConfigs(ConfigContainer defaultsConfigs) {
      this.defaultsConfigs = defaultsConfigs;
    }

    private ConfigContainer getFetchedConfigs() {
      return fetchedConfigs;
    }

    private ConfigContainer getActivatedConfigs() {
      return activatedConfigs;
    }

    private ConfigContainer getDefaultsConfigs() {
      return defaultsConfigs;
    }
  }
}
