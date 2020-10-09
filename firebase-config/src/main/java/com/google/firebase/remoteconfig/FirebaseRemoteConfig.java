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

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.abt.AbtException;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient;
import com.google.firebase.remoteconfig.internal.DefaultsXmlParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Entry point for the Firebase Remote Config (FRC) API.
 *
 * <p>Callers should first get the singleton object using {@link #getInstance()}, and then call
 * operations on that singleton object. The singleton contains the complete set of FRC parameter
 * values available to your app. The singleton also stores values fetched from the FRC Server until
 * they are made available for use with a call to {@link #activate()}.
 *
 * @author Miraziz Yusupov
 */
public class FirebaseRemoteConfig {
  // -------------------------------------------------------------------------------
  // Firebase Android Components logic.

  /**
   * Returns a singleton instance of Firebase Remote Config.
   *
   * <p>{@link FirebaseRemoteConfig} uses the default {@link FirebaseApp}, so if no {@link
   * FirebaseApp} has been initialized yet, this method throws an {@link IllegalStateException}.
   *
   * <p>Note: Also initializes the Firebase installations SDK that creates installation IDs to
   * identify Firebase installations and periodically sends data to Firebase servers. Remote Config
   * requires installation IDs for Fetch requests. To stop the periodic sync, call {@link
   * com.google.firebase.installations.FirebaseInstallations#delete()}. Sending a Fetch request
   * after deletion will create a new installation ID for this Firebase installation and resume the
   * periodic sync.
   *
   * @return A singleton instance of {@link FirebaseRemoteConfig} for the default {@link
   *     FirebaseApp}.
   */
  @NonNull
  public static FirebaseRemoteConfig getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /** Returns an instance of Firebase Remote Config for the given {@link FirebaseApp}. */
  @NonNull
  public static FirebaseRemoteConfig getInstance(@NonNull FirebaseApp app) {
    return app.get(RemoteConfigComponent.class).getDefault();
  }

  // -------------------------------------------------------------------------------
  // Firebase Remote Config logic.

  /** The static default string value for any given key. */
  public static final String DEFAULT_VALUE_FOR_STRING = "";
  /** The static default long value for any given key. */
  public static final long DEFAULT_VALUE_FOR_LONG = 0L;
  /** The static default double value for any given key. */
  public static final double DEFAULT_VALUE_FOR_DOUBLE = 0D;
  /** The static default boolean value for any given key. */
  public static final boolean DEFAULT_VALUE_FOR_BOOLEAN = false;
  /** The static default byte array value for any given key. */
  public static final byte[] DEFAULT_VALUE_FOR_BYTE_ARRAY = new byte[0];

  /** Indicates that the value returned is the static default value. */
  public static final int VALUE_SOURCE_STATIC = 0;
  /** Indicates that the value returned was retrieved from the defaults set by the client. */
  public static final int VALUE_SOURCE_DEFAULT = 1;
  /** Indicates that the value returned was retrieved from the Firebase Remote Config Server. */
  public static final int VALUE_SOURCE_REMOTE = 2;

  /**
   * Indicates that the most recent fetch of parameter values from the Firebase Remote Config Server
   * was completed successfully.
   */
  public static final int LAST_FETCH_STATUS_SUCCESS = -1;
  /**
   * Indicates that the FirebaseRemoteConfig singleton object has not yet attempted to fetch
   * parameter values from the Firebase Remote Config Server.
   */
  public static final int LAST_FETCH_STATUS_NO_FETCH_YET = 0;
  /**
   * Indicates that the most recent attempt to fetch parameter values from the Firebase Remote
   * Config Server has failed.
   */
  public static final int LAST_FETCH_STATUS_FAILURE = 1;
  /**
   * Indicates that the most recent attempt to fetch parameter values from the Firebase Remote
   * Config Server was throttled.
   */
  public static final int LAST_FETCH_STATUS_THROTTLED = 2;

  /**
   * The general logging tag for all Firebase Remote Config logs.
   *
   * @hide
   */
  public static final String TAG = "FirebaseRemoteConfig";

  private final Context context;
  private final FirebaseApp firebaseApp;
  /**
   * Firebase A/B Testing (ABT) is only valid for the 3P namespace, so the ABT variable will be null
   * if the current instance of Firebase Remote Config is using a non-3P namespace.
   */
  @Nullable private final FirebaseABTesting firebaseAbt;

  private final Executor executor;
  private final ConfigCacheClient fetchedConfigsCache;
  private final ConfigCacheClient activatedConfigsCache;
  private final ConfigCacheClient defaultConfigsCache;
  private final ConfigFetchHandler fetchHandler;
  private final ConfigGetParameterHandler getHandler;
  private final ConfigMetadataClient frcMetadata;
  private final FirebaseInstallationsApi firebaseInstallations;

  /**
   * Firebase Remote Config constructor.
   *
   * @hide
   */
  FirebaseRemoteConfig(
      Context context,
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      @Nullable FirebaseABTesting firebaseAbt,
      Executor executor,
      ConfigCacheClient fetchedConfigsCache,
      ConfigCacheClient activatedConfigsCache,
      ConfigCacheClient defaultConfigsCache,
      ConfigFetchHandler fetchHandler,
      ConfigGetParameterHandler getHandler,
      ConfigMetadataClient frcMetadata) {
    this.context = context;
    this.firebaseApp = firebaseApp;
    this.firebaseInstallations = firebaseInstallations;
    this.firebaseAbt = firebaseAbt;
    this.executor = executor;
    this.fetchedConfigsCache = fetchedConfigsCache;
    this.activatedConfigsCache = activatedConfigsCache;
    this.defaultConfigsCache = defaultConfigsCache;
    this.fetchHandler = fetchHandler;
    this.getHandler = getHandler;
    this.frcMetadata = frcMetadata;
  }

  /**
   * Returns a {@link Task} representing the initialization status of this Firebase Remote Config
   * instance.
   */
  @NonNull
  public Task<FirebaseRemoteConfigInfo> ensureInitialized() {
    Task<ConfigContainer> activatedConfigsTask = activatedConfigsCache.get();
    Task<ConfigContainer> defaultsConfigsTask = defaultConfigsCache.get();
    Task<ConfigContainer> fetchedConfigsTask = fetchedConfigsCache.get();
    Task<FirebaseRemoteConfigInfo> metadataTask = Tasks.call(executor, this::getInfo);
    Task<String> installationIdTask = firebaseInstallations.getId();
    Task<InstallationTokenResult> installationTokenTask = firebaseInstallations.getToken(false);

    return Tasks.whenAllComplete(
            activatedConfigsTask,
            defaultsConfigsTask,
            fetchedConfigsTask,
            metadataTask,
            installationIdTask,
            installationTokenTask)
        .continueWith(executor, (unusedListOfCompletedTasks) -> metadataTask.getResult());
  }

  /**
   * Asynchronously fetches and then activates the fetched configs.
   *
   * <p>If the time elapsed since the last fetch from the Firebase Remote Config backend is more
   * than the default minimum fetch interval, configs are fetched from the backend.
   *
   * <p>After the fetch is complete, the configs are activated so that the fetched key value pairs
   * take effect.
   *
   * @return {@link Task} with a {@code true} result if the current call activated the fetched
   *     configs; if no configs were fetched from the backend and the local fetched configs have
   *     already been activated, returns a {@link Task} with a {@code false} result.
   */
  @NonNull
  public Task<Boolean> fetchAndActivate() {
    return fetch().onSuccessTask(executor, (unusedVoid) -> activate());
  }

  /**
   * Asynchronously activates the most recently fetched configs, so that the fetched key value pairs
   * take effect.
   *
   * @return {@link Task} with a {@code true} result if the current call activated the fetched
   *     configs; if the fetched configs were already activated by a previous call, returns a {@link
   *     Task} with a {@code false} result.
   */
  @NonNull
  public Task<Boolean> activate() {
    Task<ConfigContainer> fetchedConfigsTask = fetchedConfigsCache.get();
    Task<ConfigContainer> activatedConfigsTask = activatedConfigsCache.get();

    return Tasks.whenAllComplete(fetchedConfigsTask, activatedConfigsTask)
        .continueWithTask(
            executor,
            (unusedListOfCompletedTasks) -> {
              if (!fetchedConfigsTask.isSuccessful() || fetchedConfigsTask.getResult() == null) {
                return Tasks.forResult(false);
              }
              ConfigContainer fetchedContainer = fetchedConfigsTask.getResult();

              // If the activated configs exist, verify that the fetched configs are fresher.
              if (activatedConfigsTask.isSuccessful()) {
                @Nullable ConfigContainer activatedContainer = activatedConfigsTask.getResult();
                if (!isFetchedFresh(fetchedContainer, activatedContainer)) {
                  return Tasks.forResult(false);
                }
              }

              return activatedConfigsCache
                  .put(fetchedContainer)
                  .continueWith(executor, this::processActivatePutTask);
            });
  }

  /**
   * Starts fetching configs, adhering to the default minimum fetch interval.
   *
   * <p>The fetched configs only take effect after the next {@link #activate} call.
   *
   * <p>Depending on the time elapsed since the last fetch from the Firebase Remote Config backend,
   * configs are either served from local storage, or fetched from the backend. The default minimum
   * fetch interval can be set with {@code
   * FirebaseRemoteConfigSettings.Builder#setMinimumFetchIntervalInSeconds(long)}; the static
   * default is 12 hours.
   *
   * <p>Note: Also initializes the Firebase installations SDK that creates installation IDs to
   * identify Firebase installations and periodically sends data to Firebase servers. Remote Config
   * requires installation IDs for Fetch requests. To stop the periodic sync, call {@link
   * com.google.firebase.installations.FirebaseInstallations#delete()}. Sending a Fetch request
   * after deletion will create a new installation ID for this Firebase installation and resume the
   * periodic sync.
   *
   * @return {@link Task} representing the {@code fetch} call.
   */
  @NonNull
  public Task<Void> fetch() {
    Task<FetchResponse> fetchTask = fetchHandler.fetch();

    // Convert Task type to Void.
    return fetchTask.onSuccessTask((unusedFetchResponse) -> Tasks.forResult(null));
  }

  /**
   * Starts fetching configs, adhering to the specified minimum fetch interval.
   *
   * <p>The fetched configs only take effect after the next {@link #activate()} call.
   *
   * <p>Depending on the time elapsed since the last fetch from the Firebase Remote Config backend,
   * configs are either served from local storage, or fetched from the backend.
   *
   * <p>Note: Also initializes the Firebase installations SDK that creates installation IDs to
   * identify Firebase installations and periodically sends data to Firebase servers. Remote Config
   * requires installation IDs for Fetch requests. To stop the periodic sync, call {@link
   * com.google.firebase.installations.FirebaseInstallations#delete()}. Sending a Fetch request
   * after deletion will create a new installation ID for this Firebase installation and resume the
   * periodic sync.
   *
   * @param minimumFetchIntervalInSeconds If configs in the local storage were fetched more than
   *     this many seconds ago, configs are served from the backend instead of local storage.
   * @return {@link Task} representing the {@code fetch} call.
   */
  @NonNull
  public Task<Void> fetch(long minimumFetchIntervalInSeconds) {
    Task<FetchResponse> fetchTask = fetchHandler.fetch(minimumFetchIntervalInSeconds);

    // Convert Task type to Void.
    return fetchTask.onSuccessTask((unusedFetchResponse) -> Tasks.forResult(null));
  }

  /**
   * Returns the parameter value for the given key as a {@link String}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The activated value, if the last successful {@link #activate()} contained the key.
   *   <li>The default value, if the key was set with {@link #setDefaultsAsync(Map)
   *       setDefaultsAsync}.
   *   <li>{@link #DEFAULT_VALUE_FOR_STRING}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key.
   * @return {@link String} representing the value of the Firebase Remote Config parameter with the
   *     given key.
   */
  @NonNull
  public String getString(@NonNull String key) {
    return getHandler.getString(key);
  }

  /**
   * Returns the parameter value for the given key as a {@code boolean}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The activated value, if the last successful {@link #activate()} contained the key, and
   *       the value can be converted into a {@code boolean}.
   *   <li>The default value, if the key was set with {@link #setDefaultsAsync(Map)
   *       setDefaultsAsync}, and the value can be converted into a {@code boolean}.
   *   <li>{@link #DEFAULT_VALUE_FOR_BOOLEAN}.
   * </ol>
   *
   * <p>"1", "true", "t", "yes", "y", and "on" are strings that are interpreted (case insensitive)
   * as {@code true}, and "0", "false", "f", "no", "n", "off", and empty string are interpreted
   * (case insensitive) as {@code false}.
   *
   * @param key A Firebase Remote Config parameter key with a {@code boolean} parameter value.
   * @return {@code boolean} representing the value of the Firebase Remote Config parameter with the
   *     given key.
   */
  public boolean getBoolean(@NonNull String key) {
    return getHandler.getBoolean(key);
  }

  /**
   * Returns the parameter value for the given key as a {@code double}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The activated value, if the last successful {@link #activate()} contained the key, and
   *       the value can be converted into a {@code double}.
   *   <li>The default value, if the key was set with {@link #setDefaultsAsync(Map)
   *       setDefaultsAsync}, and the value can be converted into a {@code double}.
   *   <li>{@link #DEFAULT_VALUE_FOR_DOUBLE}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key with a {@code double} parameter value.
   * @return {@code double} representing the value of the Firebase Remote Config parameter with the
   *     given key.
   */
  public double getDouble(@NonNull String key) {
    return getHandler.getDouble(key);
  }

  /**
   * Returns the parameter value for the given key as a {@code long}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The activated value, if the last successful {@link #activate()} contained the key, and
   *       the value can be converted into a {@code long}.
   *   <li>The default value, if the key was set with {@link #setDefaultsAsync(Map)
   *       setDefaultsAsync}, and the value can be converted into a {@code long}.
   *   <li>{@link #DEFAULT_VALUE_FOR_LONG}.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key with a {@code long} parameter value.
   * @return {@code long} representing the value of the Firebase Remote Config parameter with the
   *     given key.
   */
  public long getLong(@NonNull String key) {
    return getHandler.getLong(key);
  }

  /**
   * Returns the parameter value for the given key as a {@link FirebaseRemoteConfigValue}.
   *
   * <p>Evaluates the value of the parameter in the following order:
   *
   * <ol>
   *   <li>The activated value, if the last successful {@link #activate()} contained the key.
   *   <li>The default value, if the key was set with {@link #setDefaultsAsync(Map)
   *       setDefaultsAsync}.
   *   <li>A {@link FirebaseRemoteConfigValue} that returns the static value for each type.
   * </ol>
   *
   * @param key A Firebase Remote Config parameter key.
   * @return {@link FirebaseRemoteConfigValue} representing the value of the Firebase Remote Config
   *     parameter with the given key.
   */
  @NonNull
  public FirebaseRemoteConfigValue getValue(@NonNull String key) {
    return getHandler.getValue(key);
  }

  /**
   * Returns a {@link Set} of all Firebase Remote Config parameter keys with the given prefix.
   *
   * @param prefix The key prefix to look for. If the prefix is empty, all keys are returned.
   * @return {@link Set} of Remote Config parameter keys that start with the specified prefix.
   */
  @NonNull
  public Set<String> getKeysByPrefix(@NonNull String prefix) {
    return getHandler.getKeysByPrefix(prefix);
  }

  /**
   * Returns a {@link Map} of Firebase Remote Config key value pairs.
   *
   * <p>Evaluates the values of the parameters in the following order:
   *
   * <ol>
   *   <li>The activated value, if the last successful {@link #activate()} contained the key.
   *   <li>The default value, if the key was set with {@link #setDefaultsAsync(Map)
   *       setDefaultsAsync}.
   * </ol>
   */
  @NonNull
  public Map<String, FirebaseRemoteConfigValue> getAll() {
    return getHandler.getAll();
  }

  /**
   * Returns the state of this {@link FirebaseRemoteConfig} instance as a {@link
   * FirebaseRemoteConfigInfo}.
   */
  @NonNull
  public FirebaseRemoteConfigInfo getInfo() {
    return frcMetadata.getInfo();
  }

  /**
   * Asynchronously changes the settings for this {@link FirebaseRemoteConfig} instance.
   *
   * @param settings The new settings to be applied.
   */
  @NonNull
  public Task<Void> setConfigSettingsAsync(@NonNull FirebaseRemoteConfigSettings settings) {
    return Tasks.call(
        executor,
        () -> {
          frcMetadata.setConfigSettings(settings);

          // Return value required; return null for Void.
          return null;
        });
  }

  /**
   * Asynchronously sets default configs using the given {@link Map}.
   *
   * <p>The values in {@code defaults} must be one of the following types:
   *
   * <ul>
   *   <li><code>byte[]</code>
   *   <li><code>Boolean</code>
   *   <li><code>Double</code>
   *   <li><code>Long</code>
   *   <li><code>String</code>
   * </ul>
   *
   * @param defaults {@link Map} of key value pairs representing Firebase Remote Config parameter
   *     keys and values.
   */
  @NonNull
  public Task<Void> setDefaultsAsync(@NonNull Map<String, Object> defaults) {
    // Fetch values from the server are in the Map<String, String> format, so match that here.
    Map<String, String> defaultsStringMap = new HashMap<>();
    for (Map.Entry<String, Object> defaultsEntry : defaults.entrySet()) {
      Object value = defaultsEntry.getValue();
      if (value instanceof byte[]) {
        defaultsStringMap.put(defaultsEntry.getKey(), new String((byte[]) value));
      } else {
        defaultsStringMap.put(defaultsEntry.getKey(), value.toString());
      }
    }

    return setDefaultsWithStringsMapAsync(defaultsStringMap);
  }

  /**
   * Sets default configs using an XML resource.
   *
   * @param resourceId Id for the XML resource, which should be in your application's {@code
   *     res/xml} folder.
   */
  @NonNull
  public Task<Void> setDefaultsAsync(@XmlRes int resourceId) {
    Map<String, String> xmlDefaults = DefaultsXmlParser.getDefaultsFromXml(context, resourceId);
    return setDefaultsWithStringsMapAsync(xmlDefaults);
  }

  /**
   * Deletes all activated, fetched and defaults configs and resets all Firebase Remote Config
   * settings.
   *
   * @return {@link Task} representing the {@code clear} call.
   */
  @NonNull
  public Task<Void> reset() {
    // Use a Task to avoid throwing potential file I/O errors to the caller and because
    // frcMetadata's clear call is blocking.
    return Tasks.call(
        executor,
        () -> {
          activatedConfigsCache.clear();
          fetchedConfigsCache.clear();
          defaultConfigsCache.clear();
          frcMetadata.clear();
          return null;
        });
  }

  /**
   * Loads all the configs from disk by calling {@link ConfigCacheClient#get} on each cache client.
   *
   * @hide
   */
  void startLoadingConfigsFromDisk() {
    activatedConfigsCache.get();
    defaultConfigsCache.get();
    fetchedConfigsCache.get();
  }

  /**
   * Processes the result of the put task that persists activated configs. If the task is
   * successful, clears the fetched cache and updates the ABT SDK with the current experiments.
   *
   * @param putTask the {@link Task} returned by a {@link ConfigCacheClient#put(ConfigContainer)}
   *     call on {@link #activatedConfigsCache}.
   * @return True if {@code putTask} was successful, false otherwise.
   */
  private boolean processActivatePutTask(Task<ConfigContainer> putTask) {
    if (putTask.isSuccessful()) {
      fetchedConfigsCache.clear();

      // An activate call should only be made if there are fetched values to activate, which are
      // then put into the activated cache. So, if the put is called and succeeds, then the returned
      // values from the put task must be non-null.
      if (putTask.getResult() != null) {
        updateAbtWithActivatedExperiments(putTask.getResult().getAbtExperiments());
      } else {
        // Should never happen.
        Log.e(TAG, "Activated configs written to disk are null.");
      }
      return true;
    }
    return false;
  }

  /**
   * Asynchronously sets the defaults cache to the given default values, and persists the values to
   * disk.
   *
   * @return A task with result {@code null} on failure.
   */
  private Task<Void> setDefaultsWithStringsMapAsync(Map<String, String> defaultsStringMap) {
    ConfigContainer defaultConfigs = null;
    try {
      defaultConfigs = ConfigContainer.newBuilder().replaceConfigsWith(defaultsStringMap).build();
    } catch (JSONException e) {
      Log.e(TAG, "The provided defaults map could not be processed.", e);
      return Tasks.forResult(null);
    }

    Task<ConfigContainer> putTask = defaultConfigsCache.put(defaultConfigs);
    // Convert Task type to Void.
    return putTask.onSuccessTask((unusedContainer) -> Tasks.forResult(null));
  }

  /**
   * Notifies the Firebase A/B Testing SDK about activated experiments.
   *
   * @hide
   */
  // TODO(issues/255): Find a cleaner way to test ABT component dependency without
  // having to make this method visible.
  @VisibleForTesting
  void updateAbtWithActivatedExperiments(@NonNull JSONArray abtExperiments) {
    if (firebaseAbt == null) {
      // If there is no firebaseAbt instance, then this FRC is either in a non-3P namespace or
      // in a non-main FirebaseApp, so there is no reason to call ABT.
      // For more info: RemoteConfigComponent#isAbtSupported.
      return;
    }

    try {
      List<Map<String, String>> experimentInfoMaps = toExperimentInfoMaps(abtExperiments);
      firebaseAbt.replaceAllExperiments(experimentInfoMaps);
    } catch (JSONException e) {
      Log.e(TAG, "Could not parse ABT experiments from the JSON response.", e);
    } catch (AbtException e) {
      // TODO(issues/256): Find a way to log errors for all non-Analytics related exceptions
      // without coupling the FRC and ABT SDKs.
      Log.w(TAG, "Could not update ABT experiments.", e);
    }
  }

  /**
   * Converts a JSON array of Firebase A/B Testing experiments into a Java list of String maps.
   *
   * @param abtExperimentsJson A {@link JSONArray} of {@link JSONObject}s, where each object
   *     represents a single experiment. Each {@link JSONObject} should only contain {@link String}
   *     values.
   * @return A {@link List} of {@code {@link Map}<String, String>}s, where each map represents a
   *     single experiment.
   * @throws JSONException If the {@code abtExperimentsJson} could not be converted into a list of
   *     String maps.
   */
  @VisibleForTesting
  static List<Map<String, String>> toExperimentInfoMaps(JSONArray abtExperimentsJson)
      throws JSONException {
    List<Map<String, String>> experimentInfoMaps = new ArrayList<>();
    for (int index = 0; index < abtExperimentsJson.length(); index++) {
      Map<String, String> experimentInfo = new HashMap<>();

      JSONObject abtExperimentJson = abtExperimentsJson.getJSONObject(index);
      Iterator<String> experimentJsonKeyIterator = abtExperimentJson.keys();
      while (experimentJsonKeyIterator.hasNext()) {
        String key = experimentJsonKeyIterator.next();
        experimentInfo.put(key, abtExperimentJson.getString(key));
      }

      experimentInfoMaps.add(experimentInfo);
    }
    return experimentInfoMaps;
  }

  /** Returns true if the fetched configs are fresher than the activated configs. */
  private static boolean isFetchedFresh(
      ConfigContainer fetched, @Nullable ConfigContainer activated) {
    return activated == null || !fetched.getFetchTime().equals(activated.getFetchTime());
  }
}
