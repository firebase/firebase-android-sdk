/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * Configuration parameters to use for image generation.
 *
 * @property aspectRatio The aspect ratio of the generated image.
 * @property imageSize The size of generated images.
 */
public class ImageConfig
internal constructor(internal val aspectRatio: AspectRatio?, internal val imageSize: ImageSize?) {

  /**
   * Builder for creating an [ImageConfig].
   *
   * Mainly intended for Java interop. Kotlin consumers should use [imageConfig] for a more
   * idiomatic experience.
   *
   * @property aspectRatio See [ImageConfig.aspectRatio].
   * @property imageSize See [ImageConfig.imageSize].
   * @see [imageConfig]
   */
  public class Builder {
    @JvmField
    @set:JvmSynthetic // hide void setter from Java
    public var aspectRatio: AspectRatio? = null

    @JvmField
    @set:JvmSynthetic // hide void setter from Java
    public var imageSize: ImageSize? = null

    public fun setAspectRatio(aspectRatio: AspectRatio?): Builder = apply {
      this.aspectRatio = aspectRatio
    }

    public fun setImageSize(imageSize: ImageSize): Builder = apply { this.imageSize = imageSize }

    /** Create a new [ImageConfig] with the attached arguments. */
    public fun build(): ImageConfig = ImageConfig(aspectRatio = aspectRatio, imageSize = imageSize)
  }

  internal fun toInternal() =
    Internal(aspectRatio = aspectRatio?.internalVal, imageSize = imageSize?.internalVal)

  @Serializable internal data class Internal(val aspectRatio: String?, val imageSize: String?)
}

/**
 * Helper method to construct an [ImageConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * imageConfig {
 *   aspectRatio = AspectRatio.LANDSCAPE_16x9
 *   imageSize = ImageSize.SIZE_2K
 * }
 * ```
 */
public fun imageConfig(init: ImageConfig.Builder.() -> Unit): ImageConfig {
  val builder = ImageConfig.Builder()
  builder.init()
  return builder.build()
}
