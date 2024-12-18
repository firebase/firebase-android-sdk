package com.google.firebase.vertexai.type

public class ImagenImage(
  public val data: ByteArray?,
  public val gcsUri: String?,
  public val mimeType: String,
) : ImagenImageRepresentible {

  override fun asImagenImage(): ImagenImage {
    return this
  }
}
