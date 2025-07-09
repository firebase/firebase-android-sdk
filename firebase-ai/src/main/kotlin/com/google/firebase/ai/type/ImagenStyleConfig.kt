package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

internal class ImagenStyleConfig(val description: String?) {

  fun toInternal(): Internal {
    return Internal(description)
  }

  @Serializable internal data class Internal(val styleDescription: String?)
}
