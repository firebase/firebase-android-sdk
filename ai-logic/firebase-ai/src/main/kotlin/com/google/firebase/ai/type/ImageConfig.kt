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

    /**
     * Sets the aspect ratio of generated images.
     */
    public fun setAspectRatio(aspectRatio: AspectRatio): Builder = apply {
      this.aspectRatio = aspectRatio
    }

    /**
     * Sets the size of the generated images.
     */
    public fun setImageSize(imageSize: ImageSize): Builder = apply {
      this.imageSize = imageSize
    }

    /** Create a new [ImageConfig] with the attached arguments. */
    public fun build(): ImageConfig = ImageConfig(aspectRatio, imageSize)
  }

  internal fun toInternal() = Internal(aspectRatio?.toInternal(), imageSize?.toInternal())

  @Serializable
  internal data class Internal(
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    @SerialName("image_size") val imageSize: String? = null
  )

  public companion object {
    /**
     * Creates a new [Builder].
     */
    @JvmStatic public fun builder(): Builder = Builder()
  }
}

/**
 * An aspect ratio for generated images.
 */
public class AspectRatio private constructor(public val value: String) {
  internal fun toInternal() = value

  public companion object {
    /** Square (1:1) aspect ratio. */
    @JvmField public val SQUARE_1x1: AspectRatio = AspectRatio("1:1")

    /** Portrait widescreen (9:16) aspect ratio. */
    @JvmField public val PORTRAIT_9x16: AspectRatio = AspectRatio("9:16")

    /** Widescreen (16:9) aspect ratio. */
    @JvmField public val LANDSCAPE_16x9: AspectRatio = AspectRatio("16:9")

    /** Portrait full screen (3:4) aspect ratio. */
    @JvmField public val PORTRAIT_3x4: AspectRatio = AspectRatio("3:4")

    /** Fullscreen (4:3) aspect ratio. */
    @JvmField public val LANDSCAPE_4x3: AspectRatio = AspectRatio("4:3")

    /** Portrait (2:3) aspect ratio. */
    @JvmField public val PORTRAIT_2x3: AspectRatio = AspectRatio("2:3")

    /** Landscape (3:2) aspect ratio. */
    @JvmField public val LANDSCAPE_3x2: AspectRatio = AspectRatio("3:2")

    /** Portrait (4:5) aspect ratio. */
    @JvmField public val PORTRAIT_4x5: AspectRatio = AspectRatio("4:5")

    /** Landscape (5:4) aspect ratio. */
    @JvmField public val LANDSCAPE_5x4: AspectRatio = AspectRatio("5:4")

    /** Portrait (1:4) aspect ratio. */
    @JvmField public val PORTRAIT_1x4: AspectRatio = AspectRatio("1:4")

    /** Landscape (4:1) aspect ratio. */
    @JvmField public val LANDSCAPE_4x1: AspectRatio = AspectRatio("4:1")

    /** Portrait (1:8) aspect ratio. */
    @JvmField public val PORTRAIT_1x8: AspectRatio = AspectRatio("1:8")

    /** Landscape (8:1) aspect ratio. */
    @JvmField public val LANDSCAPE_8x1: AspectRatio = AspectRatio("8:1")

    /** Ultrawide (21:9) aspect ratio. */
    @JvmField public val ULTRAWIDE_21x9: AspectRatio = AspectRatio("21:9")
  }
}

/**
 * The size of images to generate.
 */
public class ImageSize private constructor(public val value: String) {
  internal fun toInternal() = value

  public companion object {
    /** 512px (0.5K) image size. */
    @JvmField public val SIZE_512: ImageSize = ImageSize("512")

    /** 1K image size. */
    @JvmField public val SIZE_1K: ImageSize = ImageSize("1K")

    /** 2K image size. */
    @JvmField public val SIZE_2K: ImageSize = ImageSize("2K")

    /** 4K image size. */
    @JvmField public val SIZE_4K: ImageSize = ImageSize("4K")
  }
}
