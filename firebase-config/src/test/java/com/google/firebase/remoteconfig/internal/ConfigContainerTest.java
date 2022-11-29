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
  public void getChangedParams_changedExperimentsMetadata_returnsAllParamKeys() throws Exception {
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

    assertThat(changedParams).containsExactly("string_param");
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
    ConfigContainer config =
            ConfigContainer.newBuilder()
                    .build();

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
}
