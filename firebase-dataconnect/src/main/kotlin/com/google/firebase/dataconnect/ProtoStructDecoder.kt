@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect

import com.google.protobuf.Struct
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer

inline fun <reified T> decodeFromStruct(struct: Struct): T = decodeFromStruct(serializer(), struct)

fun <T> decodeFromStruct(serializer: DeserializationStrategy<T>, struct: Struct): T {
  return ProtoValueDecoder(struct).decodeSerializableValue(serializer)
}

private class ProtoValueDecoder(private val struct: Struct) : Decoder {
  override val serializersModule = EmptySerializersModule()

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
    TODO("Not yet implemented")
  }

  override fun decodeBoolean(): Boolean {
    TODO("Not yet implemented")
  }

  override fun decodeByte(): Byte {
    TODO("Not yet implemented")
  }

  override fun decodeChar(): Char {
    TODO("Not yet implemented")
  }

  override fun decodeDouble(): Double {
    TODO("Not yet implemented")
  }

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    TODO("Not yet implemented")
  }

  override fun decodeFloat(): Float {
    TODO("Not yet implemented")
  }

  override fun decodeInline(descriptor: SerialDescriptor): Decoder {
    TODO("Not yet implemented")
  }

  override fun decodeInt(): Int {
    TODO("Not yet implemented")
  }

  override fun decodeLong(): Long {
    TODO("Not yet implemented")
  }

  @ExperimentalSerializationApi
  override fun decodeNotNullMark(): Boolean {
    TODO("Not yet implemented")
  }

  @ExperimentalSerializationApi
  override fun decodeNull(): Nothing? {
    TODO("Not yet implemented")
  }

  override fun decodeShort(): Short {
    TODO("Not yet implemented")
  }

  override fun decodeString(): String {
    TODO("Not yet implemented")
  }
}
