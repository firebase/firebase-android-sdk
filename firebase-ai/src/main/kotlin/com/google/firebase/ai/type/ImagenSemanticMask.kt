package com.google.firebase.ai.type

@PublicPreviewAPI
public class ImagenSemanticMask(classes: List<Int>, dilation: Double? = null) :
  ImagenMaskReference(maskConfig = ImagenMaskConfig(ImagenMaskMode.SEMANTIC, dilation, classes)) {}
