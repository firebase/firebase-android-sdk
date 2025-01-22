package com.google.firebase.vertexai.type

public enum class ImagenSafetyFilter(internal val internalVal: String) {
  BLOCK_LOW_AND_ABOVE("block_low_and_above"),
  BLOCK_MEDIUM_AND_ABOVE("block_medium_and_above"),
  BLOCK_ONLY_HIGH("block_only_high"),
  BLOCK_NONE("block_none")
}
