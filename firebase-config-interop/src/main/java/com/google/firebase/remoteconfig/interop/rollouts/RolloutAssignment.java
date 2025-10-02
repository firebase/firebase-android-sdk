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

package com.google.firebase.remoteconfig.interop.rollouts;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import org.json.JSONException;
import org.json.JSONObject;

/** Model representing a single rollout assigned to an app instance at a point in time. */
@Encodable
@AutoValue
public abstract class RolloutAssignment {
  private static final String ROLLOUT_ID = "rolloutId";
  private static final String VARIANT_ID = "variantId";
  private static final String PARAMETER_KEY = "parameterKey";
  private static final String PARAMETER_VALUE = "parameterValue";
  private static final String TEMPLATE_VERSION = "templateVersion";

  /** An ID representing the rollout. The ID is unique within a Remote Config template. */
  @NonNull
  public abstract String getRolloutId();

  /** The variant of the rollout assigned to the app instance. */
  @NonNull
  public abstract String getVariantId();

  /** The Remote Config parameter key affected by the rollout. */
  @NonNull
  public abstract String getParameterKey();

  /** The value of the affected parameter key. */
  @NonNull
  public abstract String getParameterValue();

  /** The Remote Config template version containing this rollout. */
  public abstract long getTemplateVersion();

  // TODO(Dana): Maybe the encoder should live in the Crashlytics SDK?
  public static final DataEncoder ROLLOUT_ASSIGNMENT_JSON_ENCODER =
      new JsonDataEncoderBuilder().configureWith(AutoRolloutAssignmentEncoder.CONFIG).build();

  /** Create a {@code RolloutAssignment} from its JSON representation. */
  @NonNull
  public static RolloutAssignment create(@NonNull JSONObject json) throws JSONException {
    return RolloutAssignment.builder()
        .setRolloutId(json.getString(ROLLOUT_ID))
        .setVariantId(json.getString(VARIANT_ID))
        .setParameterKey(json.getString(PARAMETER_KEY))
        .setParameterValue(json.getString(PARAMETER_VALUE))
        .setTemplateVersion(json.getLong(TEMPLATE_VERSION))
        .build();
  }

  /** Create a {@code RolloutAssignment} from its JSON-encoded representation. */
  @NonNull
  public static RolloutAssignment create(@NonNull String jsonString) throws JSONException {
    return create(new JSONObject(jsonString));
  }

  @NonNull
  public static Builder builder() {
    return new AutoValue_RolloutAssignment.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setRolloutId(@NonNull String rolloutId);

    @NonNull
    public abstract Builder setVariantId(@NonNull String variantId);

    @NonNull
    public abstract Builder setParameterKey(@NonNull String parameterKey);

    @NonNull
    public abstract Builder setParameterValue(@NonNull String parameterValue);

    @NonNull
    public abstract Builder setTemplateVersion(long templateVersion);

    @NonNull
    public abstract RolloutAssignment build();
  }
}
