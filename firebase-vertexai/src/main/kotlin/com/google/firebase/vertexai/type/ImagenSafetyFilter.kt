package com.google.firebase.vertexai.type

public class ImagenSafetyFilter private constructor(internal val internalVal: String) {
  public companion object {
    @JvmField
    public val BLOCK_LOW_AND_ABOVE: ImagenSafetyFilter = ImagenSafetyFilter("block_low_and_above")
    @JvmField
    public val BLOCK_MEDIUM_AND_ABOVE: ImagenSafetyFilter =
      ImagenSafetyFilter("block_medium_and_above")
    @JvmField public val BLOCK_ONLY_HIGH: ImagenSafetyFilter = ImagenSafetyFilter("block_only_high")
    @JvmField public val BLOCK_NONE: ImagenSafetyFilter = ImagenSafetyFilter("block_none")
  }
}
