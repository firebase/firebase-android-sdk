/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.vertexai.type

/**
 * Contains extra settings to configure image generation.
 *
 * @param negativePrompt This string contains things that should be explicitly excluded from
 * generated images.
 * @param numberOfImages How many images should be generated.
 * @param aspectRatio The aspect ratio of the generated images.
 * @param imageFormat The file format/compression of the generated images.
 * @param addWatermark Adds an invisible watermark to mark the image as AI generated.
 */
import kotlin.jvm.JvmField

@PublicPreviewAPI
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class ImagenGenerationConfig(
  public val negativePrompt: String? = null,
  public val numberOfImages: Int? = 1,
  public val aspectRatio: ImagenAspectRatio? = null,
  public val imageFormat: ImagenImageFormat? = null,
  public val addWatermark: Boolean? = null,
) {
  /**
   * Builder for creating a [ImagenGenerationConfig].
   *
   * This is mainly intended for Java interop. For Kotlin, use [imagenGenerationConfig] for a more
   * idiomatic experience.
   */
  @Deprecated(
    """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
  )
  public class Builder {
    @JvmField public var negativePrompt: String? = null
    @JvmField public var numberOfImages: Int? = 1
    @JvmField public var aspectRatio: ImagenAspectRatio? = null
    @JvmField public var imageFormat: ImagenImageFormat? = null
    @JvmField public var addWatermark: Boolean? = null

    /** See [ImagenGenerationConfig.negativePrompt]. */
    public fun setNegativePrompt(negativePrompt: String): Builder = apply {
      this.negativePrompt = negativePrompt
    }

    /** See [ImagenGenerationConfig.numberOfImages]. */
    public fun setNumberOfImages(numberOfImages: Int): Builder = apply {
      this.numberOfImages = numberOfImages
    }

    /** See [ImagenGenerationConfig.aspectRatio]. */
    public fun setAspectRatio(aspectRatio: ImagenAspectRatio): Builder = apply {
      this.aspectRatio = aspectRatio
    }

    /** See [ImagenGenerationConfig.imageFormat]. */
    public fun setImageFormat(imageFormat: ImagenImageFormat): Builder = apply {
      this.imageFormat = imageFormat
    }

    /** See [ImagenGenerationConfig.addWatermark]. */
    public fun setAddWatermark(addWatermark: Boolean): Builder = apply {
      this.addWatermark = addWatermark
    }

    /**
     * Alternative casing for [ImagenGenerationConfig.Builder]:
     * ```
     * val config = GenerationConfig.builder()
     * ```
     */
    public fun build(): ImagenGenerationConfig =
      ImagenGenerationConfig(
        negativePrompt = negativePrompt,
        numberOfImages = numberOfImages,
        aspectRatio = aspectRatio,
        imageFormat = imageFormat,
        addWatermark = addWatermark,
      )
  }

  public companion object {
    public fun builder(): Builder = Builder()
  }
}

/**
 * Helper method to construct a [ImagenGenerationConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * imagenGenerationConfig {
 *   negativePrompt = "People, black and white, painting"
 *   numberOfImages = 1
 *   aspectRatio = ImagenAspecRatio.SQUARE_1x1
 *   imageFormat = ImagenImageFormat.png()
 *   addWatermark = false
 * }
 * ```
 */
@PublicPreviewAPI
public fun imagenGenerationConfig(
  init: ImagenGenerationConfig.Builder.() -> Unit
): ImagenGenerationConfig {
  val builder = ImagenGenerationConfig.builder()
  builder.init()
  return builder.build()
}
