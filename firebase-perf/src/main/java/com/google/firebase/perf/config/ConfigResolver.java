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

package com.google.firebase.perf.config;

import static com.google.firebase.perf.util.Constants.MAX_SAMPLING_RATE;
import static com.google.firebase.perf.util.Constants.MIN_SAMPLING_RATE;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.BuildConfig;
import com.google.firebase.perf.config.ConfigurationConstants.CollectionDeactivated;
import com.google.firebase.perf.config.ConfigurationConstants.CollectionEnabled;
import com.google.firebase.perf.config.ConfigurationConstants.ExperimentTTID;
import com.google.firebase.perf.config.ConfigurationConstants.FragmentSamplingRate;
import com.google.firebase.perf.config.ConfigurationConstants.LogSourceName;
import com.google.firebase.perf.config.ConfigurationConstants.NetworkEventCountBackground;
import com.google.firebase.perf.config.ConfigurationConstants.NetworkEventCountForeground;
import com.google.firebase.perf.config.ConfigurationConstants.NetworkRequestSamplingRate;
import com.google.firebase.perf.config.ConfigurationConstants.RateLimitSec;
import com.google.firebase.perf.config.ConfigurationConstants.SdkDisabledVersions;
import com.google.firebase.perf.config.ConfigurationConstants.SdkEnabled;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsCpuCaptureFrequencyBackgroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsCpuCaptureFrequencyForegroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsMaxDurationMinutes;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsMemoryCaptureFrequencyBackgroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsMemoryCaptureFrequencyForegroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsSamplingRate;
import com.google.firebase.perf.config.ConfigurationConstants.TraceEventCountBackground;
import com.google.firebase.perf.config.ConfigurationConstants.TraceEventCountForeground;
import com.google.firebase.perf.config.ConfigurationConstants.TraceSamplingRate;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.ImmutableBundle;
import com.google.firebase.perf.util.Optional;
import com.google.firebase.perf.util.Utils;

/**
 * Retrieves configuration value from various config storage sources and returns resolved
 * configuration value to the caller. This class is the single source of truth for all
 * configurations across Firebase Performance.
 */
public class ConfigResolver {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private static volatile ConfigResolver instance;

  // Configuration Storage objects.
  private final RemoteConfigManager remoteConfigManager;
  private ImmutableBundle metadataBundle;
  private DeviceCacheManager deviceCacheManager;

  /**
   * Initializes ConfigResolver object with assigned config storage objects. If config storage
   * object doesn't exist, it will assign the singleton object.
   *
   * @param remoteConfigManager the Remote Config values set by Firebase Performance
   * @param metadataBundle a bundle of metadata values set by app developers in the AndroidManifest
   */
  @VisibleForTesting
  public ConfigResolver(
      @Nullable RemoteConfigManager remoteConfigManager,
      @Nullable ImmutableBundle metadataBundle,
      @Nullable DeviceCacheManager deviceCacheManager) {
    this.remoteConfigManager =
        remoteConfigManager == null ? RemoteConfigManager.getInstance() : remoteConfigManager;
    this.metadataBundle = metadataBundle == null ? new ImmutableBundle() : metadataBundle;
    this.deviceCacheManager =
        deviceCacheManager == null ? DeviceCacheManager.getInstance() : deviceCacheManager;
  }

  public static synchronized ConfigResolver getInstance() {
    if (instance == null) {
      instance = new ConfigResolver(null, null, null);
    }
    return instance;
  }

  @VisibleForTesting
  public static void clearInstance() {
    instance = null;
  }

  @VisibleForTesting
  public void setDeviceCacheManager(DeviceCacheManager deviceCacheManager) {
    this.deviceCacheManager = deviceCacheManager;
  }

  public void setContentProviderContext(Context context) {
    setApplicationContext(context.getApplicationContext());
  }

  public void setApplicationContext(Context appContext) {
    logger.setLogcatEnabled(Utils.isDebugLoggingEnabled(appContext));
    deviceCacheManager.setContext(appContext);
  }

  public void setMetadataBundle(ImmutableBundle bundle) {
    metadataBundle = bundle;
  }

  // region Actual APIs for retrieving specific flags

  /** Default API to call for whether performance monitoring is currently silent. */
  public boolean isPerformanceMonitoringEnabled() {
    Boolean isPerformanceCollectionEnabled = getIsPerformanceCollectionEnabled();
    return (isPerformanceCollectionEnabled == null || isPerformanceCollectionEnabled == true)
        && getIsServiceCollectionEnabled();
  }

  /** Returns whether developers have enabled Firebase Performance event collection. */
  @Nullable
  public Boolean getIsPerformanceCollectionEnabled() {
    // Order of preference is:
    // 1. If developer has deactivated Firebase Performance in Manifest, return false.
    // 2. If developer has enabled/disabled Firebase Performance during runtime and we have stored
    // in device cache, return developer config from cache.
    // 3. If developer has enabled/disabled Firebase Performance during build time in Manifest,
    // return developer config.
    // 4. Else, return null. Because Firebase Performance will read highlevel Firebase flag in this
    // case.
    if (getIsPerformanceCollectionDeactivated()) {
      // 1. If developer has deactivated Firebase Performance in Manifest, return false.
      return false;
    }

    CollectionEnabled collectionEnabled = CollectionEnabled.getInstance();

    // 2. If developer has enabled/disabled Firebase Performance during runtime and we have stored
    // in device cache, return developer config from cache.
    Optional<Boolean> deviceCacheValue = getDeviceCacheBoolean(collectionEnabled);
    if (deviceCacheValue.isAvailable()) {
      return deviceCacheValue.get();
    }

    // 3. If developer has enabled/disabled Firebase Performance during build time in Manifest,
    // return developer config.
    Optional<Boolean> metadataValue = getMetadataBoolean(collectionEnabled);
    if (metadataValue.isAvailable()) {
      return metadataValue.get();
    }

    // 4. Return null. Because Firebase Performance will read high-level Firebase flag in this case.
    return null;
  }

  /**
   * Returns whether data collection flag is fetched either through device cache or remote config.
   */
  public boolean isCollectionEnabledConfigValueAvailable() {
    Optional<Boolean> remoteConfigValue = getRemoteConfigBoolean(SdkEnabled.getInstance());
    Optional<Boolean> deviceCacheValue = getDeviceCacheBoolean(CollectionEnabled.getInstance());
    return deviceCacheValue.isAvailable() || remoteConfigValue.isAvailable();
  }

  /** Returns whether developers have deactivated Firebase Performance event collection. */
  @Nullable
  public Boolean getIsPerformanceCollectionDeactivated() {
    CollectionDeactivated deactivated = CollectionDeactivated.getInstance();

    // Fetches CollectionDeactivated from Android Manifest. If true, always disable collection.
    Optional<Boolean> metadataValue = getMetadataBoolean(deactivated);
    if (metadataValue.isAvailable()) {
      return metadataValue.get();
    }

    return deactivated.getDefault();
  }

  /** Stores developers runtime configuration on Firebase Performance event collection switch. */
  public void setIsPerformanceCollectionEnabled(Boolean isEnabled) {
    // Order of actions is:
    // 1. If developer has deactivated Firebase Performance in Manifest, do nothing.
    // 2. Otherwise, save this configuration in device cache.

    // If collection is deactivated, skip the action to save user configuration.
    if (getIsPerformanceCollectionDeactivated()) {
      return;
    }

    // Saves user configuration on enabling Firebase Performance collection to device cache.
    CollectionEnabled collectionEnabled = CollectionEnabled.getInstance();
    String collectionEnabledCachingFlagName = collectionEnabled.getDeviceCacheFlag();
    if (collectionEnabledCachingFlagName != null) {
      if (isEnabled != null) {
        deviceCacheManager.setValue(
            collectionEnabledCachingFlagName, Boolean.TRUE.equals(isEnabled));
      } else {
        deviceCacheManager.clear(collectionEnabledCachingFlagName);
      }
    }
  }

  /** Returns whether Firebase Performance should collect event based on Remote Config value. */
  public boolean getIsServiceCollectionEnabled() {
    return getIsSdkEnabled() && !getIsSdkVersionDisabled();
  }

  /**
   * If the configuration on Firebase Performance for current app is enabled, return true.
   * Otherwise, return false.
   */
  private boolean getIsSdkEnabled() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config:
    //    a. If Remote Config fetch status is failure, return false. (Only for this SDK enabled
    // flag)
    //    b. Otherwise, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. Otherwise, return default value.
    SdkEnabled config = SdkEnabled.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if fetch status
    // is not failure.
    Optional<Boolean> rcValue = getRemoteConfigBoolean(config);
    if (rcValue.isAvailable()) {
      // a. If Remote Config fetch status is failure, return false. (Only for this SDK enabled flag)
      if (remoteConfigManager.isLastFetchFailed()) {
        return false;
      }
      // b. Cache and return this value.
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. If the value exists in device cache, return this value.
    Optional<Boolean> deviceCacheValue = getDeviceCacheBoolean(config);
    if (deviceCacheValue.isAvailable()) {
      return deviceCacheValue.get();
    }

    // 3. Otherwise, return default value.
    return config.getDefault();
  }

  /**
   * If the configuration on Firebase Performance SDK version is disabled, return true. Otherwise,
   * return false.
   */
  private boolean getIsSdkVersionDisabled() {
    // Please note that this flag is a String which contains version list delimited by ";", but
    // return value is boolean which depends on the current Firebase Performance SDK version.
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. Otherwise, return default value.
    SdkDisabledVersions config = SdkDisabledVersions.getInstance();

    // 1. Reads value from Firebase Remote Config, cache and return this value.
    Optional<String> rcValue = getRemoteConfigString(config);
    if (rcValue.isAvailable()) {
      // Do not check FRC last fetch status because it is the most recent value device can get.
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return isFireperfSdkVersionInList(rcValue.get());
    }

    // 2. If the value exists in device cache, return this value.
    Optional<String> deviceCacheValue = getDeviceCacheString(config);
    if (deviceCacheValue.isAvailable()) {
      return isFireperfSdkVersionInList(deviceCacheValue.get());
    }

    // 3. Otherwise, return default value.
    return isFireperfSdkVersionInList(config.getDefault());
  }

  /**
   * Given a list of versions delimited by ';', returns true if Fireperf SDK version is included.
   */
  private boolean isFireperfSdkVersionInList(String versions) {
    // SDK Versions are ';' separated. If one version matches current SDK version, return false.
    if (versions.trim().isEmpty()) {
      return false;
    }

    for (String version : versions.split(/* regex= */ ";")) {
      if (version.trim().equals(BuildConfig.FIREPERF_VERSION_NAME)) {
        return true;
      }
    }

    return false;
  }

  /** Returns what percentage of Traces should be collected, range is [0.00f, 1.00f]. */
  public double getTraceSamplingRate() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. If the Firebase Remote Config fetch failed, return the default "rc failure" value.
    // 4. Otherwise, return default value.
    TraceSamplingRate config = TraceSamplingRate.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Double> rcValue = getRemoteConfigDouble(config);
    if (rcValue.isAvailable() && isSamplingRateValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. Reads value from cache layer.
    Optional<Double> deviceCacheValue = getDeviceCacheDouble(config);
    if (deviceCacheValue.isAvailable() && isSamplingRateValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 3. Check if RC fetch failed.
    if (remoteConfigManager.isLastFetchFailed()) {
      return config.getDefaultOnRcFetchFail();
    }

    // 4. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /** Returns what percentage of NetworkRequest should be collected, range is [0.00f, 1.00f]. */
  public double getNetworkRequestSamplingRate() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. If the Firebase Remote Config fetch failed, return the default "rc failure" value.
    // 4. Otherwise, return default value.
    NetworkRequestSamplingRate config = NetworkRequestSamplingRate.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Double> rcValue = getRemoteConfigDouble(config);
    if (rcValue.isAvailable() && isSamplingRateValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. Reads value from cache layer.
    Optional<Double> deviceCacheValue = getDeviceCacheDouble(config);
    if (deviceCacheValue.isAvailable() && isSamplingRateValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 3. Check if RC fetch failed.
    if (remoteConfigManager.isLastFetchFailed()) {
      return config.getDefaultOnRcFetchFail();
    }

    // 4. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /** Returns what percentage of Session gauge should be collected, range is [0.00f, 1.00f]. */
  public double getSessionsSamplingRate() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, convert from [0.00f, 100.00f] to [0.00f, 1.00f]
    // and return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. If the Firebase Remote Config fetch failed, return the default "rc failure" value.
    // 5. Otherwise, return default value.
    SessionsSamplingRate config = SessionsSamplingRate.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Double> metadataValue = getMetadataDouble(config);
    if (metadataValue.isAvailable()) {
      // Sampling percentage from metadata needs to convert from [0.00f, 100.00f] to [0.00f, 1.00f].
      double samplingRate = metadataValue.get() / 100;
      if (isSamplingRateValid(samplingRate)) {
        return samplingRate;
      }
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Double> rcValue = getRemoteConfigDouble(config);
    if (rcValue.isAvailable() && isSamplingRateValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Double> deviceCacheValue = getDeviceCacheDouble(config);
    if (deviceCacheValue.isAvailable() && isSamplingRateValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 4. Check if RC fetch failed.
    if (remoteConfigManager.isLastFetchFailed()) {
      return config.getDefaultOnRcFetchFail();
    }

    // 5. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for session CPU capture frequency (in milliseconds) when
   * the app is on foreground.
   */
  public long getSessionsCpuCaptureFrequencyForegroundMs() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. If the Firebase Remote Config fetch failed, return default failure value.
    // 5. Otherwise, return default value.
    SessionsCpuCaptureFrequencyForegroundMs config =
        SessionsCpuCaptureFrequencyForegroundMs.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Long> metadataValue = getMetadataLong(config);
    if (metadataValue.isAvailable() && isGaugeCaptureFrequencyMsValid(metadataValue.get())) {
      return metadataValue.get();
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isGaugeCaptureFrequencyMsValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isGaugeCaptureFrequencyMsValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 4. Check if RC fetch failed.
    if (remoteConfigManager.isLastFetchFailed()) {
      return config.getDefaultOnRcFetchFail();
    }

    // 5. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for session CPU capture frequency (in milliseconds) when
   * the app is on background.
   */
  public long getSessionsCpuCaptureFrequencyBackgroundMs() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. Otherwise, return default value.
    SessionsCpuCaptureFrequencyBackgroundMs config =
        SessionsCpuCaptureFrequencyBackgroundMs.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Long> metadataValue = getMetadataLong(config);
    if (metadataValue.isAvailable() && isGaugeCaptureFrequencyMsValid(metadataValue.get())) {
      return metadataValue.get();
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isGaugeCaptureFrequencyMsValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isGaugeCaptureFrequencyMsValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 4. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for session memory capture frequency (in milliseconds)
   * when the app is on foreground.
   */
  public long getSessionsMemoryCaptureFrequencyForegroundMs() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. If the Firebase Remote Config fetch failed, return default failure value.
    // 5. Otherwise, return default value.
    SessionsMemoryCaptureFrequencyForegroundMs config =
        SessionsMemoryCaptureFrequencyForegroundMs.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Long> metadataValue = getMetadataLong(config);
    if (metadataValue.isAvailable() && isGaugeCaptureFrequencyMsValid(metadataValue.get())) {
      return metadataValue.get();
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isGaugeCaptureFrequencyMsValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isGaugeCaptureFrequencyMsValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 4. Check if RC fetch failed.
    if (remoteConfigManager.isLastFetchFailed()) {
      return config.getDefaultOnRcFetchFail();
    }

    // 5. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for session memory capture frequency (in milliseconds)
   * when the app is on background.
   */
  public long getSessionsMemoryCaptureFrequencyBackgroundMs() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. Otherwise, return default value.
    SessionsMemoryCaptureFrequencyBackgroundMs config =
        SessionsMemoryCaptureFrequencyBackgroundMs.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Long> metadataValue = getMetadataLong(config);
    if (metadataValue.isAvailable() && isGaugeCaptureFrequencyMsValid(metadataValue.get())) {
      return metadataValue.get();
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isGaugeCaptureFrequencyMsValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isGaugeCaptureFrequencyMsValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 4. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /** Returns resolved configuration value for session max duration (in minutes). */
  public long getSessionsMaxDurationMinutes() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. Otherwise, return default value.
    SessionsMaxDurationMinutes config = SessionsMaxDurationMinutes.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Long> metadataValue = getMetadataLong(config);
    if (metadataValue.isAvailable() && isSessionsMaxDurationMinutesValid(metadataValue.get())) {
      return metadataValue.get();
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isSessionsMaxDurationMinutesValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable()
        && isSessionsMaxDurationMinutesValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 4. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for foreground trace event count limit in {@link
   * RateLimitSec}.
   */
  public long getTraceEventCountForeground() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. Otherwise, return default value.
    TraceEventCountForeground config = TraceEventCountForeground.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isEventCountValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isEventCountValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 3. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for background trace event count limit in {@link
   * RateLimitSec}.
   */
  public long getTraceEventCountBackground() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. Otherwise, return default value.
    TraceEventCountBackground config = TraceEventCountBackground.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isEventCountValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isEventCountValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 3. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for foreground network request event count limit in {@link
   * RateLimitSec}.
   */
  public long getNetworkEventCountForeground() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. Otherwise, return default value.
    NetworkEventCountForeground config = NetworkEventCountForeground.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isEventCountValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isEventCountValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 3. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for background network request event count limit in {@link
   * RateLimitSec}.
   */
  public long getNetworkEventCountBackground() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. Otherwise, return default value.
    NetworkEventCountBackground config = NetworkEventCountBackground.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isEventCountValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isEventCountValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 3. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns resolved configuration value for foreground/background trace/network rate limiting time
   * unit in seconds.
   */
  public long getRateLimitSec() {
    // Order of precedence is:
    // 1. If the value exists through Firebase Remote Config, cache and return this value.
    // 2. If the value exists in device cache, return this value.
    // 3. Otherwise, return default value.
    RateLimitSec config = RateLimitSec.getInstance();

    // 1. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Long> rcValue = getRemoteConfigLong(config);
    if (rcValue.isAvailable() && isTimeRangeSecValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 2. Reads value from cache layer.
    Optional<Long> deviceCacheValue = getDeviceCacheLong(config);
    if (deviceCacheValue.isAvailable() && isTimeRangeSecValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 3. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /**
   * Returns log source name for Firebase Performance backend and stores this value in local cache.
   */
  public String getAndCacheLogSourceName() {
    // Order of precedence:
    // 1. If SDK build instrument enforce default value, always use build instrumented log source.
    // 2. If RemoteConfig is available and RC value is valid, return RC value and store in cache.
    // 3. Otherwise, return cache value.
    // 4. If cache value does not exist, return SDK default value.

    LogSourceName configFlag = LogSourceName.getInstance();

    if (BuildConfig.ENFORCE_DEFAULT_LOG_SRC) {
      return configFlag.getDefault();
    }

    // Fetches value from Firebase Remote Config. A value of -1 denotes the value to be invalid.
    String remoteConfigFlag = configFlag.getRemoteConfigFlag();
    long remoteConfigValue =
        remoteConfigFlag == null
            ? -1L
            : remoteConfigManager.getRemoteConfigValueOrDefault(remoteConfigFlag, -1L);

    // Honors valid log source value from Remote Config and saves in persistent data store.
    String deviceCacheFlag = configFlag.getDeviceCacheFlag();
    if (LogSourceName.isLogSourceKnown(remoteConfigValue)) {
      String logSourceName = LogSourceName.getLogSourceName(remoteConfigValue);
      if (logSourceName != null) {
        deviceCacheManager.setValue(deviceCacheFlag, logSourceName);
        return logSourceName;
      }
    }

    // Honors shared preference over SDK default value.
    Optional<String> deviceCacheValue = getDeviceCacheString(configFlag);
    if (deviceCacheValue.isAvailable()) {
      return deviceCacheValue.get();
    }

    return configFlag.getDefault();
  }

  /** Returns what percentage of fragment traces should be collected, range is [0.00f, 1.00f]. */
  public double getFragmentSamplingRate() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, convert from [0.00f, 100.00f] to [0.00f, 1.00f]
    // and return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. Otherwise, return default value.
    FragmentSamplingRate config = FragmentSamplingRate.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Double> metadataValue = getMetadataDouble(config);
    if (metadataValue.isAvailable()) {
      // Sampling percentage from metadata needs to convert from [0.00f, 100.00f] to [0.00f, 1.00f].
      double samplingRate = metadataValue.get() / 100.0f;
      if (isSamplingRateValid(samplingRate)) {
        return samplingRate;
      }
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Double> rcValue = getRemoteConfigDouble(config);
    if (rcValue.isAvailable() && isSamplingRateValid(rcValue.get())) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Double> deviceCacheValue = getDeviceCacheDouble(config);
    if (deviceCacheValue.isAvailable() && isSamplingRateValid(deviceCacheValue.get())) {
      return deviceCacheValue.get();
    }

    // 4. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  /** Returns if _experiment_app_start_ttid should be captured. */
  public boolean getIsExperimentTTIDEnabled() {
    // Order of precedence is:
    // 1. If the value exists in Android Manifest, return this value.
    // 2. If the value exists through Firebase Remote Config, cache and return this value.
    // 3. If the value exists in device cache, return this value.
    // 4. Otherwise, return default value.
    ExperimentTTID config = ExperimentTTID.getInstance();

    // 1. Reads value in Android Manifest (it is set by developers during build time).
    Optional<Boolean> metadataValue = getMetadataBoolean(config);
    if (metadataValue.isAvailable()) {
      return metadataValue.get();
    }

    // 2. Reads value from Firebase Remote Config, saves this value in cache layer if valid.
    Optional<Boolean> rcValue = getRemoteConfigBoolean(config);
    if (rcValue.isAvailable()) {
      deviceCacheManager.setValue(config.getDeviceCacheFlag(), rcValue.get());
      return rcValue.get();
    }

    // 3. Reads value from cache layer.
    Optional<Boolean> deviceCacheValue = getDeviceCacheBoolean(config);
    if (deviceCacheValue.isAvailable()) {
      return deviceCacheValue.get();
    }

    // 4. Returns default value if there is no valid value from above approaches.
    return config.getDefault();
  }

  // endregion

  // Helper functions for interaction with Metadata layer.
  private Optional<Boolean> getMetadataBoolean(ConfigurationFlag<Boolean> config) {
    return metadataBundle.getBoolean(config.getMetadataFlag());
  }

  private Optional<Double> getMetadataDouble(ConfigurationFlag<Double> config) {
    return metadataBundle.getDouble(config.getMetadataFlag());
  }

  private Optional<Long> getMetadataLong(ConfigurationFlag<Long> config) {
    return metadataBundle.getLong(config.getMetadataFlag());
  }

  // Helper functions for interaction with Firebase Remote Config.
  private Optional<Double> getRemoteConfigDouble(ConfigurationFlag<Double> config) {
    return remoteConfigManager.getDouble(config.getRemoteConfigFlag());
  }

  private Optional<Long> getRemoteConfigLong(ConfigurationFlag<Long> config) {
    return remoteConfigManager.getLong(config.getRemoteConfigFlag());
  }

  private Optional<Boolean> getRemoteConfigBoolean(ConfigurationFlag<Boolean> config) {
    return remoteConfigManager.getBoolean(config.getRemoteConfigFlag());
  }

  private Optional<String> getRemoteConfigString(ConfigurationFlag<String> config) {
    return remoteConfigManager.getString(config.getRemoteConfigFlag());
  }

  private Long getRemoteConfigValue(ConfigurationFlag<Long> configFlag) {
    String remoteConfigFlag = configFlag.getRemoteConfigFlag();
    return remoteConfigFlag == null
        ? configFlag.getDefault()
        : remoteConfigManager.getRemoteConfigValueOrDefault(
            remoteConfigFlag, configFlag.getDefault());
  }

  // Helper functions for interaction with Device Caching layer.
  private Optional<Double> getDeviceCacheDouble(ConfigurationFlag<Double> config) {
    return deviceCacheManager.getDouble(config.getDeviceCacheFlag());
  }

  private Optional<Long> getDeviceCacheLong(ConfigurationFlag<Long> config) {
    return deviceCacheManager.getLong(config.getDeviceCacheFlag());
  }

  private Optional<Boolean> getDeviceCacheBoolean(ConfigurationFlag<Boolean> config) {
    return deviceCacheManager.getBoolean(config.getDeviceCacheFlag());
  }

  private Optional<String> getDeviceCacheString(ConfigurationFlag<String> config) {
    return deviceCacheManager.getString(config.getDeviceCacheFlag());
  }

  // Helper functions for config value validation.
  private boolean isSamplingRateValid(double samplingRate) {
    return MIN_SAMPLING_RATE <= samplingRate && samplingRate <= MAX_SAMPLING_RATE;
  }

  private boolean isEventCountValid(long eventCount) {
    return eventCount >= 0;
  }

  private boolean isTimeRangeSecValid(long timeRangeSec) {
    return timeRangeSec > 0;
  }

  private boolean isGaugeCaptureFrequencyMsValid(long frequencyMilliseconds) {
    return frequencyMilliseconds >= 0;
  }

  private boolean isSessionsMaxDurationMinutesValid(long maxDurationMin) {
    return maxDurationMin > 0;
  }
}
