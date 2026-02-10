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

package com.google.firebase.ai

/**
 * Configuration for on-device AI model inference.
 *
 * @property mode The [InferenceMode] to use for the model.
 * @property maxOutputTokens The maximum number of tokens to generate in the response. See
 * [GenerationConfig] for more detail.
 * @property temperature A parameter controlling the degree of randomness in token selection. See
 * [GenerationConfig] for more detail.
 * @property topK The `topK` parameter changes how the model selects tokens for output. See
 * [GenerationConfig] for more detail.
 * @property seed The seed to use for generation to ensure reproducibility. See [GenerationConfig]
 * for more detail.
 * @property candidateCount The number of generated responses to return. See [GenerationConfig] for
 * more detail. By default it's set to 1.
 */
public class OnDeviceConfig
@JvmOverloads
constructor(
  public val mode: InferenceMode,
  public val maxOutputTokens: Int? = null,
  public val temperature: Float? = null,
  public val topK: Int? = null,
  public val seed: Int? = null,
  public val candidateCount: Int = 1
) {

  public companion object {
    /** A default configuration that only uses in-cloud inference. */
    @JvmField public val IN_CLOUD: OnDeviceConfig = OnDeviceConfig(InferenceMode.ONLY_IN_CLOUD)
  }
}

/** Specifies how the SDK should choose between on-device and in-cloud inference. */
public class InferenceMode private constructor(private val value: String) {
  public companion object {
    /**
     * Prefer on-device inference, but fallback to in-cloud if unavailable.
     *
     * In this mode, the SDK will attempt to use on-device inference first and, if the on-device
     * model is unable to generate an answer, it will retry using the cloud model.
     */
    @JvmField public val PREFER_ON_DEVICE: InferenceMode = InferenceMode("prefer on device")

    /**
     * Prefer in-cloud inference, but fallback to on-device if cloud is unavailable.
     *
     * In this mode, the SDK will use in-cloud inference only unless the device is offline, at which
     * point the SDK will fall back to on-device inference.
     */
    @JvmField public val PREFER_IN_CLOUD: InferenceMode = InferenceMode("prefer in cloud")

    /** Only use on-device inference. */
    @JvmField public val ONLY_ON_DEVICE: InferenceMode = InferenceMode("only on device")

    /** Only use in-cloud inference. */
    @JvmField public val ONLY_IN_CLOUD: InferenceMode = InferenceMode("only in cloud")
  }
}

/** Indicates the source of the model inference. */
public class InferenceSource private constructor(private val value: String) {
  public companion object {
    /** Inference was performed on the device. */
    @JvmField public val ON_DEVICE: InferenceSource = InferenceSource("source on device")

    /** Inference was performed in the cloud. */
    @JvmField public val IN_CLOUD: InferenceSource = InferenceSource("source in cloud")
  }
}
