package com.google.firebase.vertexai.type

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Class to represent the media data that needs to be sent to the server. */
public class MediaData(public val mimeType: String, public val data: ByteArray) {
  @Serializable
  internal class Internal(@SerialName("mimeType") val mimeType: String, val data: String)

  internal fun toInternal(): Internal {
    return Internal(mimeType, Base64.encodeToString(data, BASE_64_FLAGS))
  }
}
