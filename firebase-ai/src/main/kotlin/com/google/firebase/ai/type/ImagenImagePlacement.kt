package com.google.firebase.ai.type

public class ImagenImagePlacement private constructor(public val x: Int?, public val y: Int?) {
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
