/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.ai.type

public class ImagenImagePlacement private constructor(public val x: Int?, public val y: Int?) {

  internal fun normalizeToDimensions(
    imageDimensions: Dimensions,
    canvasDimensions: Dimensions,
  ): ImagenImagePlacement {
    if (this.x != null && this.y != null) {
      return this
    }
    val halfCanvasHeight = canvasDimensions.height / 2
    val halfCanvasWidth = canvasDimensions.width / 2
    val halfImageHeight = imageDimensions.height / 2
    val halfImageWidth = imageDimensions.width / 2
    return when (this) {
      CENTER ->
        ImagenImagePlacement(halfCanvasWidth - halfImageWidth, halfCanvasHeight - halfImageHeight)
      TOP -> ImagenImagePlacement(halfCanvasWidth - halfImageWidth, 0)
      BOTTOM ->
        ImagenImagePlacement(
          halfCanvasWidth - halfImageWidth,
          canvasDimensions.height - imageDimensions.height,
        )
      LEFT -> ImagenImagePlacement(0, halfCanvasHeight - halfImageHeight)
      RIGHT ->
        ImagenImagePlacement(
          canvasDimensions.width - imageDimensions.width,
          halfCanvasHeight - halfImageHeight,
        )
      TOP_RIGHT -> ImagenImagePlacement(canvasDimensions.width - imageDimensions.width, 0)
      BOTTOM_LEFT -> ImagenImagePlacement(0, canvasDimensions.height - imageDimensions.height)
      BOTTOM_RIGHT ->
        ImagenImagePlacement(
          canvasDimensions.width - imageDimensions.width,
          canvasDimensions.height - imageDimensions.height,
        )
      else -> {
        throw IllegalStateException("Unknown ImagenImagePlacement instance, cannot normalize")
      }
    }
  }

  public companion object {
    public fun fromCoordinate(x: Int, y: Int): ImagenImagePlacement {
      return ImagenImagePlacement(x, y)
    }

    public val CENTER: ImagenImagePlacement = ImagenImagePlacement(null, null)
    public val TOP: ImagenImagePlacement = ImagenImagePlacement(null, null)
    public val BOTTOM: ImagenImagePlacement = ImagenImagePlacement(null, null)
    public val LEFT: ImagenImagePlacement = ImagenImagePlacement(null, null)
    public val RIGHT: ImagenImagePlacement = ImagenImagePlacement(null, null)
    public val TOP_LEFT: ImagenImagePlacement = ImagenImagePlacement(0, 0)
    public val TOP_RIGHT: ImagenImagePlacement = ImagenImagePlacement(null, null)
    public val BOTTOM_LEFT: ImagenImagePlacement = ImagenImagePlacement(null, null)
    public val BOTTOM_RIGHT: ImagenImagePlacement = ImagenImagePlacement(null, null)
  }
}
