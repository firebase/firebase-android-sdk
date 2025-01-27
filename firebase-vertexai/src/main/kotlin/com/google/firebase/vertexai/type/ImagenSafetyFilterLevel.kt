package com.google.firebase.vertexai.type

public class ImagenSafetyFilterLevel private constructor(internal val internalVal: String) {
  public companion object {
    @JvmField
    public val BLOCK_LOW_AND_ABOVE: ImagenSafetyFilterLevel =
      ImagenSafetyFilterLevel("block_low_and_above")
    @JvmField
    public val BLOCK_MEDIUM_AND_ABOVE: ImagenSafetyFilterLevel =
      ImagenSafetyFilterLevel("block_medium_and_above")
    @JvmField
    public val BLOCK_ONLY_HIGH: ImagenSafetyFilterLevel = ImagenSafetyFilterLevel("block_only_high")
    @JvmField public val BLOCK_NONE: ImagenSafetyFilterLevel = ImagenSafetyFilterLevel("block_none")
  }
}
