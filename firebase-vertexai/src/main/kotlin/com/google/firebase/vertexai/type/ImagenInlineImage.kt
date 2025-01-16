package com.google.firebase.vertexai.type

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

public class ImagenInlineImage(public val data: ByteArray, public val mimeType: String) {

  public fun asBitmap(): Bitmap {
    val data = Base64.decode(data, Base64.NO_WRAP)
    return BitmapFactory.decodeByteArray(data, 0, data.size)
  }
}
