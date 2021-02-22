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

package com.google.firebase.perf.internal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.inject.Provider;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.Optional;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manages retrieving all the remote config keys and values that the SDK needs.
 *
 * <p>The source of Remote config is currently Firebase Remote Config. Read more at
 * go/fireperf-remote-config-android.
 *
 * @hide
 */
/** @hide */
@Keep // Needed because of b/117526359.
public class RemoteConfigManager {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private static final String FIREPERF_FRC_NAMESPACE_NAME = "fireperf";

  private static final RemoteConfigManager sharedInstance = new RemoteConfigManager();
  private static final long TIME_AFTER_WHICH_A_FETCH_IS_CONSIDERED_STALE_MS =
      TimeUnit.HOURS.toMillis(12);

  private static final long FETCH_NEVER_HAPPENED_TIMESTAMP_MS = 0;
  private long firebaseRemoteConfigLastFetchTimestampMs = FETCH_NEVER_HAPPENED_TIMESTAMP_MS;

  @Nullable private Provider<RemoteConfigComponent> firebaseRemoteConfigProvider;
  @Nullable private FirebaseRemoteConfig firebaseRemoteConfig;

  private final Executor executor;

  private final ConcurrentHashMap<String, FirebaseRemoteConfigValue> allRcConfigMap;

  private RemoteConfigManager() {
    this(
        new ThreadPoolExecutor(
            /* corePoolSize= */ 0,
            /* maximumPoolSize= */ 1,
            /* keepAliveTime= */ 0L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>()),
        /* firebaseRemoteConfig= */ null // set once FirebaseRemoteConfig is initialized
        );
  }

  @VisibleForTesting
  RemoteConfigManager(Executor executor, FirebaseRemoteConfig firebaseRemoteConfig) {
    this.executor = executor;
    this.firebaseRemoteConfig = firebaseRemoteConfig;
    this.allRcConfigMap =
        firebaseRemoteConfig == null
            ? new ConcurrentHashMap<>()
            : new ConcurrentHashMap<>(firebaseRemoteConfig.getAll());
  }

  /** Gets the singleton instance. */
  public static RemoteConfigManager getInstance() {
    return sharedInstance;
  }

  /**
   * Sets the {@link Provider} for {@link RemoteConfigComponent} from which we can extract the
   * {@link FirebaseRemoteConfig} instance whenever it gets available.
   *
   * @implNote The Firebase Components framework initializes all of the Firebase dependencies.
   *     Because of a possible thread contention issue (see
   *     https://github.com/firebase/firebase-android-sdk/issues/1810) we are doing a lazy
   *     initialization for FirebaseRemoteConfig (by requesting a Provider instead of an actual
   *     Instance).
   * @implNote When this class is initialized, it's possible that FirebaseRemoteConfig may not be
   *     initialized. This race condition causes a NoClassDefFoundError when trying to get the
   *     instance of this class. To prevent this, we lazily get access to FirebaseRemoteConfig when
   *     it is available, and this method allows us to do that (see b/117526359).
   */
  public void setFirebaseRemoteConfigProvider(
      @Nullable Provider<RemoteConfigComponent> firebaseRemoteConfigProvider) {
    this.firebaseRemoteConfigProvider = firebaseRemoteConfigProvider;
  }

  /**
   * Retrieves the double value of the given key from the remote config, converts to float type and
   * returns this value.
   *
   * @implNote Triggers a remote config fetch on a background thread if it hasn't yet been fetched.
   * @param key The key to fetch the double value for.
   * @return The float value of the key or not present.
   */
  public Optional<Float> getFloat(String key) {
    if (key == null) {
      logger.debug("The key to get Remote Config float value is null.");
      return Optional.absent();
    }

    FirebaseRemoteConfigValue rcValue = getRemoteConfigValue(key);
    if (rcValue != null) {
      try {
        return Optional.of(Double.valueOf(rcValue.asDouble()).floatValue());
      } catch (IllegalArgumentException e) {
        if (!rcValue.asString().isEmpty()) {
          logger.debug("Could not parse value: '%s' for key: '%s'.", rcValue.asString(), key);
        }
      }
    }
    return Optional.absent();
  }

  /**
   * Retrieves and returns the long value of the given key from the remote config.
   *
   * @implNote Triggers a remote config fetch on a background thread if it hasn't yet been fetched.
   * @param key The key to fetch the long value for.
   * @return The long value of the key or not present.
   */
  public Optional<Long> getLong(String key) {
    if (key == null) {
      logger.debug("The key to get Remote Config long value is null.");
      return Optional.absent();
    }

    FirebaseRemoteConfigValue rcValue = getRemoteConfigValue(key);
    if (rcValue != null) {
      try {
        return Optional.of(rcValue.asLong());
      } catch (IllegalArgumentException e) {
        if (!rcValue.asString().isEmpty()) {
          logger.debug("Could not parse value: '%s' for key: '%s'.", rcValue.asString(), key);
        }
      }
    }
    return Optional.absent();
  }

  /**
   * Retrieves and returns the boolean value of the given key from the remote config.
   *
   * @implNote Triggers a remote config fetch on a background thread if it hasn't yet been fetched.
   * @param key The key to fetch the boolean value for.
   * @return The boolean value of the key or not present.
   */
  public Optional<Boolean> getBoolean(String key) {
    if (key == null) {
      logger.debug("The key to get Remote Config boolean value is null.");
      return Optional.absent();
    }

    FirebaseRemoteConfigValue rcValue = getRemoteConfigValue(key);
    if (rcValue != null) {
      try {
        return Optional.of(rcValue.asBoolean());
      } catch (IllegalArgumentException e) {
        if (!rcValue.asString().isEmpty()) {
          logger.debug("Could not parse value: '%s' for key: '%s'.", rcValue.asString(), key);
        }
      }
    }
    return Optional.absent();
  }

  /**
   * Retrieves and returns the String value of the given key from the remote config.
   *
   * @implNote Triggers a remote config fetch on a background thread if it hasn't yet been fetched.
   * @param key The key to fetch the String value for.
   * @return The String value of the key or not present.
   */
  public Optional<String> getString(String key) {
    if (key == null) {
      logger.debug("The key to get Remote Config String value is null.");
      return Optional.absent();
    }

    FirebaseRemoteConfigValue rcValue = getRemoteConfigValue(key);
    if (rcValue != null) {
      return Optional.of(rcValue.asString());
    }

    return Optional.absent();
  }

  /**
   * Retrieves and returns the value of the given key from the remote config (if exist) or otherwise
   * fallback to the {@code defaultValue}.
   *
   * @implNote Triggers a remote config fetch on a background thread if it hasn't yet been fetched.
   * @param key The key to fetch the value for.
   * @param defaultValue The default value to return if remote config values haven't been fetched
   *     yet or, if there's no value for the given key defined remotely or, the remote defined value
   *     cannot be parsed.
   * @return The value of the key or the default value.
   */
  @SuppressWarnings("unchecked")
  public <T extends Object> T getRemoteConfigValueOrDefault(String key, T defaultValue) {

    Object valueToReturn = defaultValue;
    FirebaseRemoteConfigValue rcValue = getRemoteConfigValue(key);

    if (rcValue != null) {
      try {
        if (defaultValue instanceof Boolean) {
          valueToReturn = rcValue.asBoolean();

        } else if (defaultValue instanceof Float) {
          valueToReturn = Double.valueOf(rcValue.asDouble()).floatValue();

        } else if (defaultValue instanceof Long || defaultValue instanceof Integer) {
          valueToReturn = rcValue.asLong();

        } else if (defaultValue instanceof String) {
          valueToReturn = rcValue.asString();

        } else {
          valueToReturn = rcValue.asString();

          logger.debug(
              "No matching type found for the defaultValue: '%s', using String.", defaultValue);
        }

      } catch (IllegalArgumentException e) {
        if (!rcValue.asString().isEmpty()) {
          logger.debug("Could not parse value: '%s' for key: '%s'.", rcValue.asString(), key);
        }
      }
    }

    return (T) valueToReturn;
  }

  /**
   * Returns the {@link FirebaseRemoteConfigValue} for the remote config key if it's been fetched
   * and the source of value is {@link FirebaseRemoteConfig#VALUE_SOURCE_REMOTE} or {@code null}
   * otherwise.
   */
  private FirebaseRemoteConfigValue getRemoteConfigValue(String key) {
    triggerRemoteConfigFetchIfNecessary();

    if (isFirebaseRemoteConfigAvailable() && allRcConfigMap.containsKey(key)) {
      FirebaseRemoteConfigValue rcValue = allRcConfigMap.get(key);

      if (rcValue.getSource() == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
        logger.debug(
            "Fetched value: '%s' for key: '%s' from Firebase Remote Config.",
            rcValue.asString(), key);

        return rcValue;
      }
    }

    return null;
  }

  /** Returns if the most recent fetch attempt was failed. */
  public boolean isLastFetchFailed() {
    return firebaseRemoteConfig == null
        || (firebaseRemoteConfig.getInfo().getLastFetchStatus()
            == FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE);
  }

  /**
   * Triggers a fetch and async activate from Firebase Remote Config if Firebase Remote Config is
   * available, and at least 12 hours have passed since the previous fetch.
   */
  private void triggerRemoteConfigFetchIfNecessary() {
    if (!isFirebaseRemoteConfigAvailable()) {
      return;
    }
    if (allRcConfigMap.isEmpty()) { // Initial fetch.
      syncConfigValues(firebaseRemoteConfig.getAll());
    }
    if (shouldFetchAndActivateRemoteConfigValues()) {
      triggerFirebaseRemoteConfigFetchAndActivateOnSuccessfulFetch();
    }
  }

  private void triggerFirebaseRemoteConfigFetchAndActivateOnSuccessfulFetch() {
    firebaseRemoteConfigLastFetchTimestampMs = getCurrentSystemTimeMillis();
    firebaseRemoteConfig
        .fetchAndActivate()
        .addOnSuccessListener(executor, result -> syncConfigValues(firebaseRemoteConfig.getAll()))
        .addOnFailureListener(
            executor,
            task -> {
              firebaseRemoteConfigLastFetchTimestampMs = FETCH_NEVER_HAPPENED_TIMESTAMP_MS;
            });
  }

  @VisibleForTesting
  protected void syncConfigValues(Map<String, FirebaseRemoteConfigValue> newlyFetchedMap) {
    allRcConfigMap.putAll(newlyFetchedMap);
    for (String existingKey : allRcConfigMap.keySet()) {
      if (!newlyFetchedMap.containsKey(existingKey)) {
        allRcConfigMap.remove(existingKey);
      }
    }
  }

  @VisibleForTesting
  // This is protected and not static (instead of private and static) so that we can change the
  // current system time through a partial mock.
  protected long getCurrentSystemTimeMillis() {
    return System.currentTimeMillis();
  }

  /** Returns true if Firebase Remote Config is available, false otherwise. */
  public boolean isFirebaseRemoteConfigAvailable() {
    if (firebaseRemoteConfig == null && firebaseRemoteConfigProvider != null) {
      RemoteConfigComponent rcComponent = firebaseRemoteConfigProvider.get();

      if (rcComponent != null) {
        firebaseRemoteConfig = rcComponent.get(FIREPERF_FRC_NAMESPACE_NAME);
      }
    }

    return firebaseRemoteConfig != null;
  }

  // We want to fetch once when the app starts and every 12 hours after that.
  // The reason we maintain our own timestamps and do not use FRC's is because FRC only updates
  // the last successful fetch timestamp AFTER successfully fetching - which might mean that
  // we could potentially fire off multiple fetch requests. This protects against that because
  // we update the timestamp before a successful fetch and reset it back if the fetch was
  // unsuccessful, making sure that a fetch is triggered again.
  // TODO(b/132369190): This shouldn't be needed once the feature is implemented in FRC.
  private boolean shouldFetchAndActivateRemoteConfigValues() {
    return (getCurrentSystemTimeMillis() - firebaseRemoteConfigLastFetchTimestampMs)
        > TIME_AFTER_WHICH_A_FETCH_IS_CONSIDERED_STALE_MS;
  }

  /** Gets the version code of the Android app. */
  @VisibleForTesting
  public static int getVersionCode(Context context) {
    try {
      PackageInfo pi =
          context.getPackageManager().getPackageInfo(context.getPackageName(), /* flags= */ 0);
      return pi.versionCode;
    } catch (NameNotFoundException e) {
      return 0;
    }
  }
}
