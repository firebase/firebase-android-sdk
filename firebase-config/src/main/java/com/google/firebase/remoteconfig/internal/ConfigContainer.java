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

import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.AFFECTED_PARAMETER_KEY;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.EXPERIMENT_ID;

import java.util.Date;
import java.util.HashMap;
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
   * <p>This is a shallow copy so {@code containerJson} must not be modified.
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
        // Default to 0 if template_version_number_key has not been cached yet.
        containerJson.optLong(TEMPLATE_VERSION_NUMBER_KEY));
  }

  /**
   * Returns a new {@link ConfigContainer} containing a deep copy of {@code containerJson}.
   *
   * <p>This is a deep copy so it may be modified without affecting the original.
   */
  private static ConfigContainer deepCopyOf(JSONObject containerJson) throws JSONException {
    JSONObject deepCopyJson = new JSONObject(containerJson.toString());
    return ConfigContainer.copyOf(deepCopyJson);
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

  private Map<String, JSONObject> getExperimentsMap(JSONArray experiments) throws JSONException {
    Map<String, JSONObject> experimentsMap = new HashMap<>();
    for (int i = 0; i < experiments.length(); i++) {
      JSONObject experiment = experiments.getJSONObject(i);
      String experimentID = experiment.getString(EXPERIMENT_ID);
      // Map experiments to their experiment IDs.
      experimentsMap.put(experimentID, experiment);
    }

    return experimentsMap;
  }

  private boolean isExperimentMetadataSame(
      JSONObject activeExperiment, JSONObject fetchedExperiment) throws JSONException {
    // Create copies of active and fetched experiments.
    JSONObject activeExperimentCopy = new JSONObject(activeExperiment.toString());
    JSONObject fetchedExperimentCopy = new JSONObject(fetchedExperiment.toString());

    // Remove config parameter keys from object since they don't show up in consistent order.
    if (activeExperimentCopy.has(AFFECTED_PARAMETER_KEY)) {
      activeExperimentCopy.remove(AFFECTED_PARAMETER_KEY);
    }
    if (fetchedExperimentCopy.has(AFFECTED_PARAMETER_KEY)) {
      fetchedExperimentCopy.remove(AFFECTED_PARAMETER_KEY);
    }

    return activeExperimentCopy.toString().equals(fetchedExperimentCopy.toString());
  }

  private void compareExperimentConfigKeys(JSONArray active, JSONArray fetched, Set<String> changed)
      throws JSONException {
    Set<String> allKeys = new HashSet<>();
    Set<String> activeKeys = new HashSet<>();
    Set<String> fetchedKeys = new HashSet<>();

    // Iterate through the active experiments config keys and add it to the sets.
    for (int i = 0; i < active.length(); i++) {
      String activeKey = active.getString(i);
      allKeys.add(activeKey);
      activeKeys.add(activeKey);
    }
    // Iterate through the fetched experiments config keys and add it to the sets.
    for (int i = 0; i < fetched.length(); i++) {
      String fetchedKey = fetched.getString(i);
      allKeys.add(fetchedKey);
      fetchedKeys.add(fetchedKey);
    }

    // Iterate through all possible keys.
    for (String key : allKeys) {
      // If fetchedKeys or activeKeys does not contain the config key, add it to `changed`.
      if (!activeKeys.contains(key) || !fetchedKeys.contains(key)) {
        changed.add(key);
      }
    }
  }

  private void getChangedABTExperiments(Set<String> changed, JSONArray otherExperiment)
      throws JSONException {
    Set<String> allExperimentIds = new HashSet<>();
    Map<String, JSONObject> fetchedExperimentsMap = new HashMap<>();
    Map<String, JSONObject> activeExperimentsMap = new HashMap<>();

    if (this.abtExperiments != null) {
      // Put active experiments metadata into a map.
      activeExperimentsMap = getExperimentsMap(this.abtExperiments);
      // Store experiment IDs for later iteration.
      allExperimentIds.addAll(activeExperimentsMap.keySet());
    }
    if (otherExperiment != null) {
      // Put fetched experiments metadata into map.
      fetchedExperimentsMap = getExperimentsMap(otherExperiment);
      // Store experiment IDs for later iteration.
      allExperimentIds.addAll(fetchedExperimentsMap.keySet());
    }

    // Iterate through all experiment IDs
    for (String experimentId : allExperimentIds) {
      // If one of the maps does not contain the experiment ID, it must have been added or removed.
      // Add that experiments' config keys to `changed`.
      if (!activeExperimentsMap.containsKey(experimentId)
          || !fetchedExperimentsMap.containsKey(experimentId)) {
        // Get the experiment that was added/removed.
        JSONObject changedExperiment = null;
        if (activeExperimentsMap.containsKey(experimentId)) {
          changedExperiment = activeExperimentsMap.get(experimentId);
        } else {
          changedExperiment = fetchedExperimentsMap.get(experimentId);
        }

        // Check if changed experiment has param keys and add them to `changed` if present.
        if (changedExperiment.has(AFFECTED_PARAMETER_KEY)) {
          JSONArray experimentKeys = changedExperiment.getJSONArray(AFFECTED_PARAMETER_KEY);
          for (int i = 0; i < experimentKeys.length(); i++) {
            changed.add(experimentKeys.getString(i));
          }
        }
      } else {
        // Fetched and Active contain the experiment ID. The metadata needs to be compared to see if
        // they're still the same.
        JSONObject activeExperiment = activeExperimentsMap.get(experimentId);
        JSONObject fetchedExperiment = fetchedExperimentsMap.get(experimentId);
        boolean experimentsMetadataSame =
            isExperimentMetadataSame(activeExperiment, fetchedExperiment);

        // Get config keys from fetched and active experiments if they exist.
        JSONArray activeExperimentConfigKeys = new JSONArray();
        JSONArray fetchedExperimentConfigKeys = new JSONArray();
        if (activeExperimentsMap.get(experimentId).has(AFFECTED_PARAMETER_KEY)) {
          activeExperimentConfigKeys =
              activeExperimentsMap.get(experimentId).getJSONArray(AFFECTED_PARAMETER_KEY);
        }
        if (fetchedExperimentsMap.get(experimentId).has(AFFECTED_PARAMETER_KEY)) {
          fetchedExperimentConfigKeys =
              fetchedExperimentsMap.get(experimentId).getJSONArray(AFFECTED_PARAMETER_KEY);
        }

        if (!experimentsMetadataSame) {
          // Add in all keys from both sides if the experiments metadata has changed.
          for (int i = 0; i < activeExperimentConfigKeys.length(); i++) {
            changed.add(activeExperimentConfigKeys.getString(i));
          }
          for (int i = 0; i < fetchedExperimentConfigKeys.length(); i++) {
            changed.add(fetchedExperimentConfigKeys.getString(i));
          }
        } else {
          // Compare config keys from either experiment.
          compareExperimentConfigKeys(
              activeExperimentConfigKeys, fetchedExperimentConfigKeys, changed);
        }
      }
    }
  }

  /**
   * @param other The other {@link ConfigContainer} against which to compute the diff
   * @return The set of config keys that have changed between the this config and {@code other}
   * @throws JSONException
   */
  public Set<String> getChangedParams(ConfigContainer other) throws JSONException {
    // Make a deep copy of the other config before modifying it
    JSONObject otherConfig = ConfigContainer.deepCopyOf(other.containerJson).getConfigs();

    Set<String> changed = new HashSet<>();
    Iterator<String> keys = this.getConfigs().keys();
    while (keys.hasNext()) {
      String key = keys.next();

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
              .getJSONObject(key)
              .toString()
              .equals(other.getPersonalizationMetadata().getJSONObject(key).toString())) {
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
    getChangedABTExperiments(changed, other.getAbtExperiments());

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
