// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.metadata;

import com.google.auto.value.AutoValue;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * model used in user metadata context which make rollout assignment serialize, deserialize easier
 */
@Encodable
@AutoValue
public abstract class RolloutAssignment {

  public abstract String getRolloutId();

  public abstract String getParameterKey();

  public abstract String getParameterValue();

  public abstract String getVariantId();

  public abstract long getTemplateVersion();

  static RolloutAssignment create(
      String rolloutId,
      String parameterKey,
      String parameterValue,
      String variantId,
      long templateVersion) {
    return new AutoValue_RolloutAssignment(
        rolloutId, parameterKey, parameterValue, variantId, templateVersion);
  }

  public static final DataEncoder ROLLOUT_ASSIGNMENT_JSON_ENCODER =
      new JsonDataEncoderBuilder().configureWith(AutoRolloutAssignmentEncoder.CONFIG).build();

  static RolloutAssignment create(String json) throws JSONException {
    final JSONObject dataObj = new JSONObject(json);
    String rolloutId = dataObj.getString("rolloutId");
    String parameterKey = dataObj.getString("parameterKey");
    String parameterValue = dataObj.getString("parameterValue");
    String variantId = dataObj.getString("variantId");
    long templateVersion = dataObj.getLong("templateVersion");
    return new AutoValue_RolloutAssignment(
        rolloutId, parameterKey, parameterValue, variantId, templateVersion);
  }
}
