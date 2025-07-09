package com.google.firebase.ai.type

@PublicPreviewAPI
public class ImagenStyleReference(
  image: ImagenInlineImage,
  referenceId: Int? = null,
  description: String? = null,
) :
  ImagenReferenceImage(
    image = image,
    referenceId = referenceId,
    styleConfig = ImagenStyleConfig(description)
  ) {}
