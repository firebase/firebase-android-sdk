/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ai.ondevice.interop

public class GenerationConfig(public val modelConfig: ModelConfig) {
  override fun toString(): String = "GenerationConfig(modelConfig=$modelConfig)"

  override fun equals(other: Any?): Boolean =
    other is GenerationConfig && modelConfig == other.modelConfig

  override fun hashCode(): Int = modelConfig.hashCode() ?: 0
}

/**
 * Configuration parameters for model selection.
 *
 * @property releaseStage The release stage of the model to use.
 * @property preference The performance preference for the model.
 */
public class ModelConfig(
  public val releaseStage: ModelReleaseStage = ModelReleaseStage.STABLE,
  public val preference: ModelPreference = ModelPreference.FULL,
) {
  override fun equals(other: Any?): Boolean =
    other is ModelConfig && releaseStage == other.releaseStage && preference == other.preference

  override fun hashCode(): Int {
    var result = releaseStage.hashCode()
    result = 31 * result + preference.hashCode()
    return result
  }

  override fun toString(): String {
    return "ModelConfig(releaseStage=$releaseStage, preference=$preference)"
  }
}
/** Defines the release stage of the model. */
public enum class ModelReleaseStage {
  /**
   * Selects the latest model version that is fully tested and on consumer devices. This is the
   * default setting.
   */
  STABLE,

  /**
   * Selects the latest model version in the preview stage. This stage lets you test beta features
   * or newer model architectures before they are widely deployed.
   */
  PREVIEW,
}

/** Defines the performance preference for the model. */
public enum class ModelPreference {
  /** Recommended when model accuracy and full capabilities are prioritized over speed. */
  FULL,

  /** Recommended for latency-sensitive apps that require minimal response times. */
  FAST,
}
