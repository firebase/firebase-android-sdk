package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.QueryRef

class IdentityMutationRef(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  operationSet: String,
  revision: String
) :
  MutationRef<Map<String, Any?>, Map<String, Any?>>(
    dataConnect = dataConnect,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision
  ) {
  override fun encodeVariables(variables: Map<String, Any?>): Map<String, Any?> = variables
  override fun decodeResult(map: Map<String, Any?>): Map<String, Any?> = map
}

class IdentityQueryRef(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  operationSet: String,
  revision: String
) :
  QueryRef<Map<String, Any?>, Map<String, Any?>>(
    dataConnect = dataConnect,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision
  ) {
  override fun encodeVariables(variables: Map<String, Any?>): Map<String, Any?> = variables
  override fun decodeResult(map: Map<String, Any?>): Map<String, Any?> = map
}
