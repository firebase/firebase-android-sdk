package com.google.firebase.ai.type

internal class ImagenMaskMode private constructor(internal val value: String) {
  companion object {
    val USER_PROVIDED: ImagenMaskMode = ImagenMaskMode("MASK_MODE_USER_PROVIDED")
    val BACKGROUND: ImagenMaskMode = ImagenMaskMode("MASK_MODE_BACKGROUND")
    val FOREGROUND: ImagenMaskMode = ImagenMaskMode("MASK_MODE_FOREGROUND")
    val SEMANTIC: ImagenMaskMode = ImagenMaskMode("MASK_MODE_SEMANTIC")
  }
}
