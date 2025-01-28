package com.google.firebase.vertexai.type

public class ImagenPersonFilterLevel private constructor(internal val internalVal: String) {
  public companion object {
    @JvmField public val ALLOW_ALL: ImagenPersonFilterLevel = ImagenPersonFilterLevel("allow_all")
    @JvmField
    public val ALLOW_ADULT: ImagenPersonFilterLevel = ImagenPersonFilterLevel("allow_adult")
    @JvmField public val BLOCK_ALL: ImagenPersonFilterLevel = ImagenPersonFilterLevel("dont_allow")
  }
}
