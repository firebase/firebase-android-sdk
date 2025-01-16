package com.google.firebase.vertexai.type

public class ImagenImageFormat
private constructor(public val mimeType: String, public val compressionQuality: Int?) {

  public companion object {
    public fun jpeg(compressionQuality: Int? = null): ImagenImageFormat {
      return ImagenImageFormat("image/jpeg", compressionQuality)
    }

    public fun png(): ImagenImageFormat {
      return ImagenImageFormat("image/png", null)
    }
  }
}
