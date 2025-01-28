package com.google.firebase.vertexai.type

public class ImagenAspectRatio private constructor(internal val internalVal: String) {
  public companion object {
    @JvmField public val SQUARE_1x1: ImagenAspectRatio = ImagenAspectRatio("1:1")
    @JvmField public val PORTRAIT_3x4: ImagenAspectRatio = ImagenAspectRatio("3:4")
    @JvmField public val LANDSCAPE_4x3: ImagenAspectRatio = ImagenAspectRatio("4:3")
    @JvmField public val PORTRAIT_9x16: ImagenAspectRatio = ImagenAspectRatio("9:16")
    @JvmField public val LANDSCAPE_16x9: ImagenAspectRatio = ImagenAspectRatio("16:9")
  }
}
