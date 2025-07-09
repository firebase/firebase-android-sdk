package com.google.firebase.ai.type

@PublicPreviewAPI
public class ImagenRawMask(mask: ImagenInlineImage, dilation: Double? = null) :
  ImagenMaskReference(
    maskConfig = ImagenMaskConfig(ImagenMaskMode.USER_PROVIDED, dilation),
    image = mask,
  ) {}
