package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

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

@PublicPreviewAPI
public fun imagenEditingConfig(init: ImagenEditingConfig.Builder.() -> Unit): ImagenEditingConfig {
  val builder = ImagenEditingConfig.builder()
  builder.init()
  return builder.build()
}
