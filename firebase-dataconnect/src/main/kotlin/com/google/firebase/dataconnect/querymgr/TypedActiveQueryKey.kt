package com.google.firebase.dataconnect.querymgr

import kotlinx.serialization.DeserializationStrategy

internal class TypedActiveQueryKey<Data>(val dataDeserializer: DeserializationStrategy<Data>) {
  override fun equals(other: Any?) =
    other is TypedActiveQueryKey<*> && other.dataDeserializer === dataDeserializer

  override fun hashCode() = System.identityHashCode(dataDeserializer)

  override fun toString() = "TypedActiveQueryKey(dataDeserializer=$dataDeserializer)"
}
