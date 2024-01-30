// Copyright 2022 Google LLC
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
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.EXPERIMENT_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.VARIANT_ID;
import static com.google.firebase.remoteconfig.internal.Personalization.ARM_INDEX;
import static com.google.firebase.remoteconfig.internal.Personalization.CHOICE_ID;
import static com.google.firebase.remoteconfig.internal.Personalization.GROUP;
import static com.google.firebase.remoteconfig.internal.Personalization.PERSONALIZATION_ID;

import com.google.common.collect.ImmutableMap;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ConfigContainerTest {
  @Before
  public void setup() throws Exception {}

  @Test
  public void getChangedParams_changedValue_returnsChangedParamKey() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_2"))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("string_param");
  }

  @Test
  public void getChangedParams_addedParam_returnsAddedParamKey() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                ImmutableMap.of("string_param", "value_1", "long_param", "long_value"))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("long_param");
  }

  @Test
  public void getChangedParams_removedParam_returnsRemovedParamKey() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder().replaceConfigsWith(ImmutableMap.of()).build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("string_param");
  }

  @Test
  public void getChangedParams_changedP13nMetadata_returnsChangedParamKey() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .withPersonalizationMetadata(
                new JSONObject(
                    ImmutableMap.of(
                        "string_param", generateP13nMetadata("p13n-id-1", "0", "id1", "BASELINE"))))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .withPersonalizationMetadata(
                new JSONObject(
                    ImmutableMap.of(
                        "string_param", generateP13nMetadata("p13n-id-1", "1", "id1", "P13N"))))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("string_param");
  }

  @Test
  public void getChangedParams_sameP13nMetadata_returnsEmptySet() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .withPersonalizationMetadata(
                new JSONObject(
                    ImmutableMap.of(
                        "string_param", generateP13nMetadata("p13n-id-1", "1", "id1", "P13N"))))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .withPersonalizationMetadata(
                new JSONObject(
                    ImmutableMap.of(
                        "string_param", generateP13nMetadata("p13n-id-1", "1", "id1", "P13N"))))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).isEmpty();
  }

  @Test
  public void getChangedParams_changedExperimentsMetadata_returnsNoParamKeys() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .withAbtExperiments(generateAbtExperiments(1))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).isEmpty();
  }

  @Test
  public void getChangedParams_noChanges_returnsEmptySet() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).isEmpty();
  }

  @Test
  public void getChangedParams_emptyConfig_returnsAllOtherParams() throws Exception {
    ConfigContainer config = ConfigContainer.newBuilder().build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("string_param");
  }

  @Test
  public void getChangedParams_multipleChanges_returnsMultipleParamKeys() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                ImmutableMap.of(
                    "always_on",
                    "true",
                    "string_param",
                    "value_1",
                    "long_param",
                    "1",
                    "bool_param",
                    "true"))
            .withPersonalizationMetadata(
                new JSONObject(
                    ImmutableMap.of(
                        "string_param", generateP13nMetadata("p13n-id-1", "0", "id1", "BASELINE"))))
            .build();

    // always_on has NOT changed
    // string_param has changed P13n metadata
    // long_param has a changed value
    // bool_param has been removed
    // feature_1 has been added
    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                ImmutableMap.of(
                    "always_on",
                    "true",
                    "string_param",
                    "value_1",
                    "long_param",
                    "2",
                    "feature_1",
                    "true"))
            .withPersonalizationMetadata(
                new JSONObject(
                    ImmutableMap.of(
                        "string_param", generateP13nMetadata("p13n-id-1", "1", "id1", "P13N"))))
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams)
        .containsExactly("string_param", "long_param", "bool_param", "feature_1");
  }

  @Test
  public void getChangedParams_configsUnmodified() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .build();

    // ConfigContainer#getChangedParams should not modify either comparison argument.
    config.getChangedParams(other);

    String configsString = new JSONObject("{string_param: value_1}").toString();
    assertThat(config.getConfigs().toString()).isEqualTo(configsString);
    assertThat(other.getConfigs().toString()).isEqualTo(configsString);
  }

  @Test
  public void copyOf_missingTemplateVersionNumber_defaultsToZero() throws Exception {
    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("string_param", "value_1"))
            .withTemplateVersionNumber(2L)
            .build();
    JSONObject containerJson = new JSONObject(config.toString());
    containerJson.remove(ConfigContainer.TEMPLATE_VERSION_NUMBER_KEY);

    ConfigContainer configWithoutTemplateVersion = ConfigContainer.copyOf(containerJson);

    assertThat(configWithoutTemplateVersion.getTemplateVersionNumber()).isEqualTo(0L);
  }

  @Test
  public void copyOfConfigContainer_withoutRolloutsMetadata_defaultsToEmptyArray()
      throws Exception {
    JSONArray affectedParameterKeys = new JSONArray();
    affectedParameterKeys.put("key_1");
    affectedParameterKeys.put("key_2");

    JSONArray rolloutsMetadata = new JSONArray();
    rolloutsMetadata.put(
        new JSONObject()
            .put("rolloutId", "1")
            .put("variantId", "A")
            .put("affectedParameterKeys", affectedParameterKeys));
    ConfigContainer configContainer =
        ConfigContainer.newBuilder().withRolloutMetadata(rolloutsMetadata).build();
    JSONObject configContainerJson = new JSONObject(configContainer.toString());
    configContainerJson.remove(ConfigContainer.ROLLOUT_METADATA_KEY);

    ConfigContainer otherConfigContainer = ConfigContainer.copyOf(configContainerJson);

    assertThat(otherConfigContainer.getRolloutMetadata()).isNotNull();
    assertThat(otherConfigContainer.getRolloutMetadata().length()).isEqualTo(0);
  }

  @Test
  public void copyOfConfigContainer_withRolloutsMetadata_returnsSameRolloutsMetadata()
      throws Exception {
    JSONArray affectedParameterKeys = new JSONArray();
    affectedParameterKeys.put("key_1");
    affectedParameterKeys.put("key_2");

    JSONArray rolloutsMetadata = new JSONArray();
    rolloutsMetadata.put(
        new JSONObject()
            .put("rolloutId", "1")
            .put("variantId", "A")
            .put("affectedParameterKeys", affectedParameterKeys));
    ConfigContainer configContainer =
        ConfigContainer.newBuilder().withRolloutMetadata(rolloutsMetadata).build();
    ConfigContainer otherConfigContainer =
        ConfigContainer.copyOf(new JSONObject(configContainer.toString()));

    assertThat(otherConfigContainer.getRolloutMetadata().toString())
        .isEqualTo(rolloutsMetadata.toString());
  }

  @Test
  public void configContainer_withRolloutsMetadata_returnsCorrectRolloutsMetadata()
      throws Exception {
    JSONArray affectedParameterKeys = new JSONArray();
    affectedParameterKeys.put("key_1");
    affectedParameterKeys.put("key_2");

    JSONArray rolloutsMetadata = new JSONArray();
    rolloutsMetadata.put(
        new JSONObject()
            .put("rolloutId", "1")
            .put("variantId", "A")
            .put("affectedParameterKeys", affectedParameterKeys));
    ConfigContainer configContainer =
        ConfigContainer.newBuilder().withRolloutMetadata(rolloutsMetadata).build();

    assertThat(configContainer.getRolloutMetadata().toString())
        .isEqualTo(rolloutsMetadata.toString());
  }

  @Test
  public void getChangedParams_changedRolloutMetadata_returnsUpdatedKey() throws Exception {
    JSONArray activeRolloutMetadata = generateNRolloutMetadataEntries(1);
    JSONArray fetchedRolloutMetadata = generateNRolloutMetadataEntries(1);

    fetchedRolloutMetadata.getJSONObject(0).put("variantId", "B");

    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(activeRolloutMetadata)
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(fetchedRolloutMetadata)
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("key_1");
  }

  @Test
  public void getChangedParams_addedRolloutMetadataToSameKey_returnsUpdatedKey() throws Exception {
    JSONArray activeRolloutMetadata = generateNRolloutMetadataEntries(1);
    JSONArray fetchedRolloutMetadata = generateNRolloutMetadataEntries(2);

    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(activeRolloutMetadata)
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(fetchedRolloutMetadata)
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("key_1");
  }

  @Test
  public void getChangedParams_deletedRolloutMetadata_returnsUpdatedKey() throws Exception {
    JSONArray activeRolloutMetadata = generateNRolloutMetadataEntries(1);
    JSONArray fetchedRolloutMetadata = new JSONArray();

    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(activeRolloutMetadata)
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(fetchedRolloutMetadata)
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("key_1");
  }

  @Test
  public void getChangedParams_addNewKeyWithRolloutMetadata_returnsUpdatedKey() throws Exception {
    JSONArray activeRolloutMetadata = generateNRolloutMetadataEntries(1);
    JSONArray fetchedRolloutMetadata = generateNRolloutMetadataEntries(2);

    fetchedRolloutMetadata.getJSONObject(1).getJSONArray("affectedParameterKeys").put(0, "key_2");

    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(activeRolloutMetadata)
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1", "key_2", "value_2"))
            .withRolloutMetadata(fetchedRolloutMetadata)
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).containsExactly("key_2");
  }

  @Test
  public void getChangedParams_unchangedRolloutMetadata_returnsNoKey() throws Exception {
    JSONArray activeRolloutMetadata = generateNRolloutMetadataEntries(1);
    JSONArray fetchedRolloutMetadata = generateNRolloutMetadataEntries(1);

    ConfigContainer config =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(activeRolloutMetadata)
            .build();

    ConfigContainer other =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("key_1", "value_1"))
            .withRolloutMetadata(fetchedRolloutMetadata)
            .build();

    Set<String> changedParams = config.getChangedParams(other);

    assertThat(changedParams).isEmpty();
  }

  private static JSONArray generateAbtExperiments(int numExperiments) throws JSONException {
    JSONArray experiments = new JSONArray();
    for (int experimentNum = 1; experimentNum <= numExperiments; experimentNum++) {
      experiments.put(
          new JSONObject().put(EXPERIMENT_ID, "exp" + experimentNum).put(VARIANT_ID, "var1"));
    }
    return experiments;
  }

  private static ImmutableMap<String, String> generateP13nMetadata(
      String p13nId, String armIndex, String choiceId, String group) {
    return ImmutableMap.of(
        PERSONALIZATION_ID, p13nId, ARM_INDEX, armIndex, CHOICE_ID, choiceId, GROUP, group);
  }

  private static JSONArray generateNRolloutMetadataEntries(int numberOfMetadata)
      throws JSONException {
    JSONArray rolloutMetadata = new JSONArray();
    for (int i = 1; i <= numberOfMetadata; i++) {
      rolloutMetadata.put(
          new JSONObject()
              .put("rolloutId", "rollout_" + i)
              .put("variantId", "A")
              .put("affectedParameterKeys", new JSONArray().put("key_1")));
    }

    return rolloutMetadata;
  }
}
