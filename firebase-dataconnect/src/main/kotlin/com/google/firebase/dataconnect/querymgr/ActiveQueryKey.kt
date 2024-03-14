package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.calculateSha512
import com.google.firebase.dataconnect.encodeToStruct
import com.google.firebase.dataconnect.toAlphaNumericString
import com.google.firebase.dataconnect.toCompactString
import com.google.protobuf.Struct
import java.util.Objects

internal class ActiveQueryKey(val operationName: String, val variables: Struct) {

  private val variablesHash: String = variables.calculateSha512().toAlphaNumericString()

  override fun equals(other: Any?) =
    other is ActiveQueryKey &&
      other.operationName == operationName &&
      other.variablesHash == variablesHash

  override fun hashCode() = Objects.hash(operationName, variablesHash)

  override fun toString() =
    "ActiveQueryKey(" +
      "operationName=$operationName, " +
      "variables=${variables.toCompactString()})"

  companion object {
    fun <Data, Variables> forQueryRef(query: QueryRef<Data, Variables>): ActiveQueryKey {
      val variablesStruct = encodeToStruct(query.variablesSerializer, query.variables)
      return ActiveQueryKey(operationName = query.operationName, variables = variablesStruct)
    }
  }
}
