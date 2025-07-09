package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

internal class ImagenMaskConfig(
  internal val maskType: ImagenMaskMode,
  internal val dilation: Double? = null,
  internal val classes: List<Int>? = null
) {
  internal fun toInternal(): Internal {
    return Internal(maskType.value, dilation, classes)
  }

  @Serializable
  internal data class Internal(
    val maskMode: String,
    val dilation: Double?,
    val maskClasses: List<Int>?
  )
}
