package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.BaseRef

object IdentityCodec : BaseRef.Codec<Map<String, Any?>, Map<String, Any?>> {
  override fun encodeVariables(variables: Map<String, Any?>): Map<String, Any?> = variables
  override fun decodeResult(map: Map<String, Any?>): Map<String, Any?> = map
}
