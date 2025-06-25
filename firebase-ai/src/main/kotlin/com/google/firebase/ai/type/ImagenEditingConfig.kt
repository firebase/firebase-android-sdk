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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * Configuration parameters to use for imagen editing.
 * @property image the base image to be edited.
 * @property editMode specifies the edititing mode for this request.
 * @property mask the mask to specify which sections of the base image can be edited.
 * @property maskDilation a percentage by which to shrink the mask to allow some edge blending.
 * @property editSteps the number of intermediate steps for the edit to take.
 */
@PublicPreviewAPI
public class ImagenEditingConfig(
  public val image: ImagenInlineImage,
  public val editMode: ImagenEditMode,
  public val mask: ImagenInlineImage? = null,
  public val maskDilation: Double? = null,
  public val editSteps: Int? = null,
) {
  public companion object {
    public fun builder(): Builder = Builder()
  }

  /**
   * Builder for creating a [ImagenEditingConfig].
   *
   * Mainly intended for Java interop. Kotlin consumers should use [imagenEditingConfig] for a more
   * idiomatic experience.
   *
   * @property image see [ImagenEditingConfig.image]
   * @property editMode see [ImagenEditingConfig.editMode]
   * @property mask see [ImagenEditingConfig.mask]
   * @property maskDilation see [ImagenEditingConfig.maskDilation]
   * @property editSteps see [ImagenEditingConfig.editSteps]
   */
  public class Builder {
    @JvmField public var image: ImagenInlineImage? = null
    @JvmField public var editMode: ImagenEditMode? = null
    @JvmField public var mask: ImagenInlineImage? = null
    @JvmField public var maskDilation: Double? = null
    @JvmField public var editSteps: Int? = null

    public fun setImage(image: ImagenInlineImage): Builder = apply { this.image = image }

    public fun setEditMode(editMode: ImagenEditMode): Builder = apply { this.editMode = editMode }

    public fun setMask(mask: ImagenInlineImage): Builder = apply { this.mask = mask }

    public fun setMaskDilation(maskDilation: Double): Builder = apply {
      this.maskDilation = maskDilation
    }

    public fun setEditSteps(editSteps: Int): Builder = apply { this.editSteps = editSteps }

    /** Creates a new [ImagenEditingConfig] with the attached arguments */
    public fun build(): ImagenEditingConfig {
      if (image == null) {
        throw IllegalStateException("ImagenEditingConfig must contain an image")
      }
      if (editMode == null) {
        throw IllegalStateException("ImagenEditingConfig must contain an editMode")
      }
      return ImagenEditingConfig(
        image = image!!,
        editMode = editMode!!,
        mask = mask,
        maskDilation = maskDilation,
        editSteps = editSteps,
      )
    }
  }

  internal fun toInternal(): Internal {
    return Internal(baseSteps = editSteps)
  }

  @Serializable
  internal data class Internal(
    val baseSteps: Int?,
  )
}

/**
 * Helper method to construct a [ImagenEditingConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * imagenEditingConfig {
 *   image = baseImage
 *   mask = imageMask
 *   editMode = ImagenEditMode.INPAINTING_REMOVAL
 *   maskDilation = 0.05
 * }
 * ```
 */
@PublicPreviewAPI
public fun imagenEditingConfig(init: ImagenEditingConfig.Builder.() -> Unit): ImagenEditingConfig {
  val builder = ImagenEditingConfig.builder()
  builder.init()
  return builder.build()
}
