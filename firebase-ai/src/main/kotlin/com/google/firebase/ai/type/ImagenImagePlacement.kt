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
