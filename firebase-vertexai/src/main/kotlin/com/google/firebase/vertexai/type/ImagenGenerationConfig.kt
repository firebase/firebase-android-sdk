package com.google.firebase.vertexai.type

public class ImagenGenerationConfig(
  public val negativePrompt: String? = null,
  public val numberOfImages: Int? = 1,
  public val aspectRatio: ImagenAspectRatio? = null,
  public val imageFormat: ImagenImageFormat? = null,
  public val addWatermark: Boolean? = null,
) {
  public class Builder {
    @JvmField public var negativePrompt: String? = null
    @JvmField public var numberOfImages: Int? = 1
    @JvmField public var aspectRatio: ImagenAspectRatio? = null
    @JvmField public var imageFormat: ImagenImageFormat? = null
    @JvmField public var addWatermark: Boolean? = null

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

public fun imagenGenerationConfig(
  init: ImagenGenerationConfig.Builder.() -> Unit
): ImagenGenerationConfig {
  val builder = ImagenGenerationConfig.builder()
  builder.init()
  return builder.build()
}
