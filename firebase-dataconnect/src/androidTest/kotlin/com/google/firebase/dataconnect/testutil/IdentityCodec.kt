package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.BaseRef

object IdentityCodec : BaseRef.Codec<Map<String, Any?>> {
  override fun decodeResult(map: Map<String, Any?>): Map<String, Any?> = map
}
