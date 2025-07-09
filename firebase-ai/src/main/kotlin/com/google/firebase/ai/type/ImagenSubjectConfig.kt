package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

internal class ImagenSubjectConfig(
  val description: String?,
  val type: ImagenSubjectReferenceType?,
) {

  internal fun toInternal(): Internal {
    return Internal(description, type?.value)
  }

  @Serializable
  internal data class Internal(val subjectDescription: String?, val subjectType: String?)
}
