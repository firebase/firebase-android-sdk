package com.google.firebase.ai.type

@PublicPreviewAPI
public abstract class ImagenMaskReference
internal constructor(
  maskConfig: ImagenMaskConfig,
  image: ImagenInlineImage? = null,
) : ImagenReferenceImage(maskConfig = maskConfig, image = image) {

  public companion object {
    public fun generateMaskAndPadForOutpainting(
      image: ImagenInlineImage,
      newDimensions: Dimensions,
      newPosition: ImagenImagePlacement = ImagenImagePlacement.CENTER,
    ): List<ImagenReferenceImage> {
      // TODO: Fill in
      return emptyList()
    }
  }
}
