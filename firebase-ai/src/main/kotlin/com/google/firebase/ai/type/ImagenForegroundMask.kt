package com.google.firebase.ai.type

@PublicPreviewAPI
public class ImagenForegroundMask(dilation: Double? = null) :
  ImagenMaskReference(maskConfig = ImagenMaskConfig(ImagenMaskMode.FOREGROUND, dilation)) {}
