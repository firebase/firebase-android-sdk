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

package com.google.firebase.ai.type

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration options for generating images with Gemini models.
 *
 * See the
 * [documentation](https://ai.google.dev/gemini-api/docs/image-generation#aspect_ratios_and_image_size)
 * to learn about parameters available for use with Gemini image models.
 */
public class ImageConfig
private constructor(
  internal val aspectRatio: AspectRatio? = null,
  internal val imageSize: ImageSize? = null
) {
  /** Builder for creating a [ImageConfig]. */
  public class Builder {
    @JvmField public var aspectRatio: AspectRatio? = null
    @JvmField public var imageSize: ImageSize? = null

    /** Sets the aspect ratio of generated images. */
    public fun setAspectRatio(aspectRatio: AspectRatio): Builder = apply {
      this.aspectRatio = aspectRatio
    }

    /** Sets the size of the generated images. */
    public fun setImageSize(imageSize: ImageSize): Builder = apply { this.imageSize = imageSize }

    /** Create a new [ImageConfig] with the attached arguments. */
    public fun build(): ImageConfig = ImageConfig(aspectRatio, imageSize)
  }

  @OptIn(InternalSerializationApi::class)
  @Serializable
  internal data class Internal(
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    @SerialName("image_size") val imageSize: String? = null
  )

  internal fun toInternal() = Internal(aspectRatio?.toInternal(), imageSize?.toInternal())

  public companion object {
    /** Creates a new [Builder]. */
    @JvmStatic public fun builder(): Builder = Builder()
  }
}

/** Helper method to construct an [ImageConfig] in a DSL-like manner. */
public fun imageConfig(init: ImageConfig.Builder.() -> Unit): ImageConfig {
  val builder = ImageConfig.builder()
  builder.init()
  return builder.build()
}
