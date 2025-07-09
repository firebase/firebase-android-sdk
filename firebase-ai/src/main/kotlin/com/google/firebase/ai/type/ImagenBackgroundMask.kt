package com.google.firebase.ai.type

@PublicPreviewAPI
public class ImagenBackgroundMask(dilation: Double? = null) :
  ImagenMaskReference(maskConfig = ImagenMaskConfig(ImagenMaskMode.BACKGROUND, dilation)) {}
