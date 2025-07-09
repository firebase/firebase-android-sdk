package com.google.firebase.ai.typeImage

import com.google.firebase.ai.type.ImagenControlType

internal class ImagenControlConfig(
  internal val controlType: ImagenControlType,
  internal val enableComputation: Boolean? = null,
  internal val superpixelRegionSize: Int? = null,
  internal val superpixelRuler: Int? = null
) {}
