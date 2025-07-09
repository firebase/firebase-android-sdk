package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

@PublicPreviewAPI
public class ImagenEditingConfig(
  internal val editMode: ImagenEditMode? = null,
  internal val editSteps: Int? = null,
) {
  internal fun toInternal(): Internal {
    return Internal(baseSteps = editSteps)
  }

  @Serializable
  internal data class Internal(
    val baseSteps: Int?,
  )
}
