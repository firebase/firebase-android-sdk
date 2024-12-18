package com.google.firebase.vertexai.type

public class ImageFormat
private constructor(public val mimeType: String, public val compressionQuality: Int?) {

  public companion object {
    public fun jpeg(compressionQuality: Int?): ImageFormat {
      return ImageFormat("image/jpeg", compressionQuality)
    }

    public fun png(): ImageFormat {
      return ImageFormat("image/png", null)
    }
  }
}
