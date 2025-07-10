package com.google.firebase.ai.type

public class ImagenControlType internal constructor(internal val value: String) {
  public companion object {
    public val CANNY: ImagenControlType = ImagenControlType("CONTROL_TYPE_CANNY")
    public val SCRIBBLE: ImagenControlType = ImagenControlType("CONTROL_TYPE_SCRIBBLE")
    public val FACE_MESH: ImagenControlType = ImagenControlType("CONTROL_TYPE_FACE_MESH")
    public val COLOR_SUPERPIXEL: ImagenControlType =
      ImagenControlType("CONTROL_TYPE_COLOR_SUPERPIXEL")
  }
}
