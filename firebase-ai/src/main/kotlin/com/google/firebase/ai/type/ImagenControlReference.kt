package com.google.firebase.ai.type

import com.google.firebase.ai.typeImage.ImagenControlConfig

@PublicPreviewAPI
public class ImagenControlReference(
  image: ImagenInlineImage,
  type: ImagenControlType,
  referenceId: Int? = null,
  enableComputation: Boolean? = null,
  superpixelRegionSize: Int? = null,
  superpixelRuler: Int? = null,
) :
  ImagenReferenceImage(
    controlConfig =
      ImagenControlConfig(type, enableComputation, superpixelRegionSize, superpixelRuler),
    image = image,
    referenceId = referenceId,
  ) {}
