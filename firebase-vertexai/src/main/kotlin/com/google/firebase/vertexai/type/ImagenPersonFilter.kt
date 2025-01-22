package com.google.firebase.vertexai.type

public enum class ImagenPersonFilter(internal val internalVal: String) {
  ALLOW_ALL("allow_all"),
  ALLOW_ADULT("allow_adult"),
  BLOCK_ALL("dont_allow"),
}
