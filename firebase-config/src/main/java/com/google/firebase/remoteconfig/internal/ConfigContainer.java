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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The wrapper class for a JSON object that contains Firebase Remote Config (FRC) configs as well as
 * their metadata.
 *
 * @author Miraziz Yusupov
 */
public class ConfigContainer {
  private static final String CONFIGS_KEY = "configs_key";
  private static final String FETCH_TIME_KEY = "fetch_time_key";
  private static final String ABT_EXPERIMENTS_KEY = "abt_experiments_key";
  private static final String ACTIVE_ROLLOUTS_KEY = "active_rollouts_key";
  private static final String ENABLED_FEATURE_KEYS_KEY = "enabled_feature_keys_key";

  private static final Date DEFAULTS_FETCH_TIME = new Date(0L);

  /**
   * The object stored in disk and wrapped by this class; contains a set of configs and any relevant
   * metadata.
   *
   * <p>Used by the storage client to write this container to file.
   */
  private JSONObject containerJson;

  /**
   * Cached value of the container's config key-value pairs.
   *
   * <p>Used by the FRC client to retrieve config values.
   */
  private JSONObject configsJson;
  /** Cached value of the time when this container's values were fetched. */
  private Date fetchTime;

  private JSONArray abtExperiments;
  private JSONArray activeRollouts;
  private JSONArray enabledFeatureKeys;

  /**
   * Creates a new container with the specified configs and fetch time.
   *
   * <p>The {@code configsJson} must not be modified.
   */
  private ConfigContainer(
      JSONObject configsJson,
      Date fetchTime,
      JSONArray abtExperiments,
      JSONArray activeRollouts,
      JSONArray enabledFeatureKeys)
      throws JSONException {
    JSONObject containerJson = new JSONObject();
    containerJson.put(CONFIGS_KEY, configsJson);
    containerJson.put(FETCH_TIME_KEY, fetchTime.getTime());
    containerJson.put(ABT_EXPERIMENTS_KEY, abtExperiments);
    containerJson.put(ACTIVE_ROLLOUTS_KEY, activeRollouts);
    containerJson.put(ENABLED_FEATURE_KEYS_KEY, enabledFeatureKeys);

    this.configsJson = configsJson;
    this.fetchTime = fetchTime;
    this.abtExperiments = abtExperiments;
    this.activeRollouts = activeRollouts;
    this.enabledFeatureKeys = enabledFeatureKeys;

    this.containerJson = containerJson;
  }

  /**
   * Returns a {@link ConfigContainer} that wraps the {@code containerJson}.
   *
   * <p>The {@code containerJson} must not be modified.
   */
  static ConfigContainer copyOf(JSONObject containerJson) throws JSONException {
    return new ConfigContainer(
        containerJson.getJSONObject(CONFIGS_KEY),
        new Date(containerJson.getLong(FETCH_TIME_KEY)),
        containerJson.getJSONArray(ABT_EXPERIMENTS_KEY),
        containerJson.getJSONArray(ACTIVE_ROLLOUTS_KEY),
        containerJson.getJSONArray(ENABLED_FEATURE_KEYS_KEY));
  }

  /**
   * Returns the FRC configs.
   *
   * <p>The returned {@link JSONObject} must not be modified.
   */
  public JSONObject getConfigs() {
    return configsJson;
  }

  /**
   * Returns the time the configs of this instance were fetched. The fetch time is epoch for
   * defaults containers.
   */
  public Date getFetchTime() {
    return fetchTime;
  }

  public JSONArray getAbtExperiments() {
    return abtExperiments;
  }

  /** Returns all rollouts that target this device. */
  public JSONArray getActiveRollouts() {
    return activeRollouts;
  }

  /** Returns the keys of all enabled features on this device. */
  public JSONArray getEnabledFeatureKeys() {
    return enabledFeatureKeys;
  }

  @Override
  public String toString() {
    return containerJson.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConfigContainer)) {
      return false;
    }
    ConfigContainer that = (ConfigContainer) o;
    // TODO(issues/285): Use an equality comparison that is guaranteed to be deterministic.
    return containerJson.toString().equals(that.toString());
  }

  @Override
  public int hashCode() {
    return containerJson.hashCode();
  }

  /** Builder for creating an instance of {@link ConfigContainer}. */
  public static class Builder {
    private JSONObject builderConfigsJson;
    private Date builderFetchTime;
    /**
     * A collection of {@link JSONArray} objects that will be used to set {@link JSONArray}
     * parameters of a {@link ConfigContainer}.
     */
    private Map<String, JSONArray> jsonArrayBuilders = new HashMap<>();

    private Builder() {
      builderConfigsJson = new JSONObject();
      builderFetchTime = DEFAULTS_FETCH_TIME;
    }

    public Builder(ConfigContainer otherContainer) {
      this.builderConfigsJson = otherContainer.getConfigs();
      this.builderFetchTime = otherContainer.getFetchTime();
      jsonArrayBuilders.put(ABT_EXPERIMENTS_KEY, otherContainer.getAbtExperiments());
      jsonArrayBuilders.put(ACTIVE_ROLLOUTS_KEY, otherContainer.getActiveRollouts());
      jsonArrayBuilders.put(ENABLED_FEATURE_KEYS_KEY, otherContainer.getEnabledFeatureKeys());
    }

    public Builder replaceConfigsWith(Map<String, String> configsMap) {
      this.builderConfigsJson = new JSONObject(configsMap);
      return this;
    }

    public Builder replaceConfigsWith(JSONObject configsJson) {
      try {
        this.builderConfigsJson = new JSONObject(configsJson.toString());
      } catch (JSONException e) {
        // We serialize and deserialize the JSONObject to guarantee that it cannot be mutated after
        // being set in the builder.
        // A JSONException should never occur because the JSON that is being deserialized is
        // guaranteed to be valid.
      }
      return this;
    }

    public Builder withFetchTime(Date fetchTime) {
      this.builderFetchTime = fetchTime;
      return this;
    }

    public Builder withAbtExperiments(JSONArray abtExperiments) {
      return with(ABT_EXPERIMENTS_KEY, abtExperiments);
    }

    public Builder withActiveRollouts(JSONArray activeRollouts) {
      return with(ACTIVE_ROLLOUTS_KEY, activeRollouts);
    }

    public Builder withEnabledFeatureKeys(JSONArray enabledFeatureKeys) {
      return with(ENABLED_FEATURE_KEYS_KEY, enabledFeatureKeys);
    }

    /** Adds a {@param key}, {@link JSONArray} pair to {@link Builder#jsonArrayBuilders}. */
    private Builder with(String key, JSONArray jsonArray) {
      try {
        jsonArrayBuilders.put(key, new JSONArray(jsonArray.toString()));
      } catch (JSONException e) {
        // We serialize and deserialize the JSONArray to guarantee that it cannot be mutated after
        // being set in the builder.
        // A JSONException should never occur because the JSON that is being deserialized is
        // guaranteed to be valid.
      }
      return this;
    }

    /** If a fetch time is not provided, the defaults container fetch time is used. */
    public ConfigContainer build() throws JSONException {
      return new ConfigContainer(
          builderConfigsJson,
          builderFetchTime,
          getOrDefaultToEmptyJSONArray(ABT_EXPERIMENTS_KEY),
          getOrDefaultToEmptyJSONArray(ACTIVE_ROLLOUTS_KEY),
          getOrDefaultToEmptyJSONArray(ENABLED_FEATURE_KEYS_KEY));
    }

    private JSONArray getOrDefaultToEmptyJSONArray(String key) {
      return jsonArrayBuilders.containsKey(key) ? jsonArrayBuilders.get(key) : new JSONArray();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(ConfigContainer otherContainer) {
    return new Builder(otherContainer);
  }
}
