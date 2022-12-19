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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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
  static final String CONFIGS_KEY = "configs_key";
  static final String FETCH_TIME_KEY = "fetch_time_key";
  static final String ABT_EXPERIMENTS_KEY = "abt_experiments_key";
  static final String PERSONALIZATION_METADATA_KEY = "personalization_metadata_key";
  static final String TEMPLATE_VERSION_NUMBER_KEY = "template_version_number_key";

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

  private JSONObject personalizationMetadata;

  private long templateVersionNumber;

  /**
   * Creates a new container with the specified configs and fetch time.
   *
   * <p>The {@code configsJson} must not be modified.
   */
  private ConfigContainer(
      JSONObject configsJson,
      Date fetchTime,
      JSONArray abtExperiments,
      JSONObject personalizationMetadata,
      long templateVersionNumber)
      throws JSONException {
    JSONObject containerJson = new JSONObject();
    containerJson.put(CONFIGS_KEY, configsJson);
    containerJson.put(FETCH_TIME_KEY, fetchTime.getTime());
    containerJson.put(ABT_EXPERIMENTS_KEY, abtExperiments);
    containerJson.put(PERSONALIZATION_METADATA_KEY, personalizationMetadata);
    containerJson.put(TEMPLATE_VERSION_NUMBER_KEY, templateVersionNumber);

    this.configsJson = configsJson;
    this.fetchTime = fetchTime;
    this.abtExperiments = abtExperiments;
    this.personalizationMetadata = personalizationMetadata;
    this.templateVersionNumber = templateVersionNumber;

    this.containerJson = containerJson;
  }

  /**
   * Returns a {@link ConfigContainer} that wraps the {@code containerJson}.
   *
   * <p>The {@code containerJson} must not be modified.
   */
  static ConfigContainer copyOf(JSONObject containerJson) throws JSONException {
    // Personalization metadata may not have been written yet.
    JSONObject personalizationMetadataJSON =
        containerJson.optJSONObject(PERSONALIZATION_METADATA_KEY);
    if (personalizationMetadataJSON == null) {
      personalizationMetadataJSON = new JSONObject();
    }

    return new ConfigContainer(
        containerJson.getJSONObject(CONFIGS_KEY),
        new Date(containerJson.getLong(FETCH_TIME_KEY)),
        containerJson.getJSONArray(ABT_EXPERIMENTS_KEY),
        personalizationMetadataJSON,
        containerJson.getLong(TEMPLATE_VERSION_NUMBER_KEY));
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

  public JSONObject getPersonalizationMetadata() {
    return personalizationMetadata;
  }

  public long getTemplateVersionNumber() {
    return templateVersionNumber;
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

  /**
   * @param other The other {@link ConfigContainer} against which to compute the diff
   * @return The set of config keys that have changed between the this config and {@code other}
   * @throws JSONException
   */
  public Set<String> getChangedParams(ConfigContainer other) throws JSONException {
    // Make a copy of the other config before modifying it
    JSONObject otherConfig = ConfigContainer.copyOf(other.containerJson).getConfigs();

    // Experiments aren't associated with params, so we can just compare arrays once
    Boolean experimentsChanged = !this.getAbtExperiments().equals(other.getAbtExperiments());

    Set<String> changed = new HashSet<>();
    Iterator<String> keys = this.getConfigs().keys();
    while (keys.hasNext()) {
      String key = keys.next();

      // If the ABT Experiments have changed, add all keys since we don't know which keys the ABT
      // experiments apply to
      if (experimentsChanged) {
        changed.add(key);
        continue;
      }

      // If the other config doesn't have the key
      if (!other.getConfigs().has(key)) {
        changed.add(key);
        continue;
      }

      // If the other config has a different value for the key
      if (!this.getConfigs().get(key).equals(other.getConfigs().get(key))) {
        changed.add(key);
        continue;
      }

      // If only one of the configs has PersonalizationMetadata for the key
      if (this.getPersonalizationMetadata().has(key) && !other.getPersonalizationMetadata().has(key)
          || !this.getPersonalizationMetadata().has(key)
              && other.getPersonalizationMetadata().has(key)) {
        changed.add(key);
        continue;
      }

      // If the both configs have PersonalizationMetadata for the key, but the metadata has changed
      if (this.getPersonalizationMetadata().has(key)
          && other.getPersonalizationMetadata().has(key)
          && !this.getPersonalizationMetadata()
              .get(key)
              .equals(other.getPersonalizationMetadata().get(key))) {
        changed.add(key);
        continue;
      }

      // Since the key is the same in both configs, remove it from otherConfig
      otherConfig.remove(key);
    }

    // Add all the keys from other that are different
    Iterator<String> remainingOtherKeys = otherConfig.keys();
    while (remainingOtherKeys.hasNext()) {
      changed.add(remainingOtherKeys.next());
    }

    return changed;
  }

  @Override
  public int hashCode() {
    return containerJson.hashCode();
  }

  /** Builder for creating an instance of {@link ConfigContainer}. */
  public static class Builder {
    private JSONObject builderConfigsJson;
    private Date builderFetchTime;
    private JSONArray builderAbtExperiments;
    private JSONObject builderPersonalizationMetadata;
    private long builderTemplateVersionNumber;

    private Builder() {
      builderConfigsJson = new JSONObject();
      builderFetchTime = DEFAULTS_FETCH_TIME;
      builderAbtExperiments = new JSONArray();
      builderPersonalizationMetadata = new JSONObject();
      builderTemplateVersionNumber = 0L;
    }

    public Builder(ConfigContainer otherContainer) {
      this.builderConfigsJson = otherContainer.getConfigs();
      this.builderFetchTime = otherContainer.getFetchTime();
      this.builderAbtExperiments = otherContainer.getAbtExperiments();
      this.builderPersonalizationMetadata = otherContainer.getPersonalizationMetadata();
      this.builderTemplateVersionNumber = otherContainer.getTemplateVersionNumber();
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
      try {
        this.builderAbtExperiments = new JSONArray(abtExperiments.toString());
      } catch (JSONException e) {
        // We serialize and deserialize the JSONArray to guarantee that it cannot be mutated after
        // being set in the builder.
        // A JSONException should never occur because the JSON that is being deserialized is
        // guaranteed to be valid.
      }
      return this;
    }

    public Builder withPersonalizationMetadata(JSONObject personalizationMetadata) {
      try {
        this.builderPersonalizationMetadata = new JSONObject(personalizationMetadata.toString());
      } catch (JSONException e) {
        // We serialize and deserialize the JSONObject to guarantee that it cannot be mutated after
        // being set in the builder.
        // A JSONException should never occur because the JSON that is being deserialized is
        // guaranteed to be valid.
      }
      return this;
    }

    public Builder withTemplateVersionNumber(long templateVersionNumber) {
      this.builderTemplateVersionNumber = templateVersionNumber;
      return this;
    }

    /** If a fetch time is not provided, the defaults container fetch time is used. */
    public ConfigContainer build() throws JSONException {
      return new ConfigContainer(
          builderConfigsJson,
          builderFetchTime,
          builderAbtExperiments,
          builderPersonalizationMetadata,
          builderTemplateVersionNumber);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(ConfigContainer otherContainer) {
    return new Builder(otherContainer);
  }
}
