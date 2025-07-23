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

/**
 * Represents where the placement of an image is within a new, larger image, usually in the context
 * of an outpainting request.
 */
public class ImagenImagePlacement
private constructor(public val x: Int? = null, public val y: Int? = null) {

  /**
   * If this placement is represented by coordinates this is a no-op, if its one of the enumerated
   * types below, then the position is calculated based on its description
   */
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
      TOP_CENTER -> ImagenImagePlacement(halfCanvasWidth - halfImageWidth, 0)
      BOTTOM_CENTER ->
        ImagenImagePlacement(
          halfCanvasWidth - halfImageWidth,
          canvasDimensions.height - imageDimensions.height,
        )
      LEFT_CENTER -> ImagenImagePlacement(0, halfCanvasHeight - halfImageHeight)
      RIGHT_CENTER ->
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
    /**
     * Creates an [ImagenImagePlacement] that represents a placement in an image described by two
     * coordinates. The coordinate system has 0,0 in the top left corner, and the x and y
     * coordinates represent the location of the top left corner of the original image.
     * @param x the x coordinate of the top left corner of the original image
     * @param y the y coordinate of the top left corner of the original image
     */
    @JvmStatic
    public fun fromCoordinate(x: Int, y: Int): ImagenImagePlacement {
      return ImagenImagePlacement(x, y)
    }

    /** Center the image horizontally and vertically within the larger image */
    @JvmField public val CENTER: ImagenImagePlacement = ImagenImagePlacement()

    /** Center the image horizontally and aligned with the top edge of the larger image */
    @JvmField public val TOP_CENTER: ImagenImagePlacement = ImagenImagePlacement()

    /** Center the image horizontally and aligned with the bottom edge of the larger image */
    @JvmField public val BOTTOM_CENTER: ImagenImagePlacement = ImagenImagePlacement()

    /** Center the image vertically and aligned with the left edge of the larger image */
    @JvmField public val LEFT_CENTER: ImagenImagePlacement = ImagenImagePlacement()

    /** Center the image vertically and aligned with the right edge of the larger image */
    @JvmField public val RIGHT_CENTER: ImagenImagePlacement = ImagenImagePlacement()

    /** Align the image with the top left corner of the larger image */
    @JvmField public val TOP_LEFT: ImagenImagePlacement = ImagenImagePlacement(0, 0)

    /** Align the image with the top right corner of the larger image */
    @JvmField public val TOP_RIGHT: ImagenImagePlacement = ImagenImagePlacement()

    /** Align the image with the bottom left corner of the larger image */
    @JvmField public val BOTTOM_LEFT: ImagenImagePlacement = ImagenImagePlacement()

    /** Align the image with the bottom right corner of the larger image */
    @JvmField public val BOTTOM_RIGHT: ImagenImagePlacement = ImagenImagePlacement()
  }
}
