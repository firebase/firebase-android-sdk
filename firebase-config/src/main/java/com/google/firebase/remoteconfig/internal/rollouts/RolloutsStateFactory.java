/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.remoteconfig.internal.rollouts;

import androidx.annotation.NonNull;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RolloutsStateFactory {
  ConfigGetParameterHandler getParameterHandler;

  private static final String ROLLOUT_ID_KEY = "rollout_id";
  private static final String VARIANT_ID_KEY = "variant_id";
  private static final String AFFECTED_PARAMETER_KEYS_KEY = "affected_parameter_keys";

  RolloutsStateFactory(ConfigGetParameterHandler getParameterHandler) {
    this.getParameterHandler = getParameterHandler;
  }

  @NonNull
  RolloutsState getActiveRolloutsState(@NonNull ConfigContainer configContainer)
      throws FirebaseRemoteConfigClientException {
    JSONArray rolloutMetadata = configContainer.getRolloutMetadata();
    long templateVersion = configContainer.getTemplateVersionNumber();

    Set<RolloutAssignment> rolloutAssignments = new HashSet<>();
    for (int i = 0; i < rolloutMetadata.length(); i++) {
      try {
        JSONObject rollout = rolloutMetadata.getJSONObject(i);
        JSONArray affectedParameterKeys = rollout.getJSONArray(AFFECTED_PARAMETER_KEYS_KEY);

        for (int j = 0; j < affectedParameterKeys.length(); j++) {
          String parameterKey = affectedParameterKeys.getString(j);
          String parameterValue = getParameterHandler.getString(parameterKey);

          rolloutAssignments.add(
              RolloutAssignment.builder()
                  .setRolloutId(rollout.getString(ROLLOUT_ID_KEY))
                  .setVariantId(rollout.getString(VARIANT_ID_KEY))
                  .setParameterKey(parameterKey)
                  .setParameterValue(parameterValue)
                  .setTemplateVersion(templateVersion)
                  .build());
        }
      } catch (JSONException e) {
        throw new FirebaseRemoteConfigClientException(
            "Exception parsing rollouts metadata to create RolloutsState.", e);
      }
    }

    return RolloutsState.create(rolloutAssignments);
  }

  @NonNull
  public static RolloutsStateFactory create(
      @NonNull ConfigGetParameterHandler configGetParameterHandler) {
    return new RolloutsStateFactory(configGetParameterHandler);
  }
}
