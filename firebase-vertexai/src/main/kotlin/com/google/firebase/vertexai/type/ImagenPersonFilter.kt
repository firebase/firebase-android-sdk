package com.google.firebase.vertexai.type

public class ImagenPersonFilter private constructor(internal val internalVal: String) {
  public companion object {
    @JvmField public val ALLOW_ALL: ImagenPersonFilter = ImagenPersonFilter("allow_all")
    @JvmField public val ALLOW_ADULT: ImagenPersonFilter = ImagenPersonFilter("allow_adult")
    @JvmField public val BLOCK_ALL: ImagenPersonFilter = ImagenPersonFilter("dont_allow")
  }
}
