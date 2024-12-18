package com.google.firebase.vertexai.type

public class ImagenGCSImage(public val gcsUri: String, public val mimeType: String) :
  ImagenImageRepresentible {

  override fun asImagenImage(): ImagenImage {
    return ImagenImage(null, gcsUri, mimeType)
  }
}
