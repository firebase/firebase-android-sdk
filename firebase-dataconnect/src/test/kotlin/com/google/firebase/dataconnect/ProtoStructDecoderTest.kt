// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import org.junit.Test

class ProtoStructDecoderTest {

  @Test
  fun `decodeFromStruct() can decode a Struct with a single String value`() {
    @Serializable data class TestData(val value: String)
    val struct = encodeToStruct(TestData(value = "Test Value"))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(value = "Test Value"))
  }
}

/**
 * A decoder that can be useful during testing to simply print the method invocations in order to
 * discover how a decoder should be implemented.
 */
private class LoggingDecoder(
  value: Any?,
  private val idBySerialDescriptor: MutableMap<SerialDescriptor, Long> = mutableMapOf()
) : Decoder, CompositeDecoder {
  val id = nextEncoderId.incrementAndGet()

  override val serializersModule = EmptySerializersModule()

  private var nextElementIndex = 0
  private val elements =
    when (value) {
      null -> null
      is Map<*, *> -> value.entries.toList()
      is Collection<*> -> value.toList()
      else -> null
    }

  private fun log(message: String) {
    println("zzyzx LoggingDecoder[$id] $message")
  }

  private fun idFor(descriptor: SerialDescriptor) =
    idBySerialDescriptor[descriptor]
      ?: nextSerialDescriptorId.incrementAndGet().also { idBySerialDescriptor[descriptor] = it }

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
    log(
      "beginStructure() descriptorId=${idFor(descriptor)} kind=${descriptor.kind} " +
        "elementsCount=${descriptor.elementsCount}"
    )
    return LoggingDecoder(idBySerialDescriptor)
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    log("endStructure() descriptorId=${idFor(descriptor)} kind=${descriptor.kind}")
  }

  override fun decodeBoolean(): Boolean {
    log("decodeBoolean() returns true")
    return true
  }

  override fun decodeByte(): Byte {
    log("decodeByte() returns 111")
    return 111
  }

  override fun decodeChar(): Char {
    log("decodeChar() returns Z")
    return 'Z'
  }

  override fun decodeDouble(): Double {
    log("decodeDouble() returns 123.45")
    return 123.45
  }

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    log("decodeEnum() returns 0")
    return 0
  }

  override fun decodeFloat(): Float {
    log("decodeFloat() returns 678.90")
    return 678.90f
  }

  override fun decodeInline(descriptor: SerialDescriptor): Decoder {
    log("decodeInline() kind=${descriptor.kind} serialName=${descriptor.serialName}")
    return LoggingDecoder(idBySerialDescriptor)
  }

  override fun decodeInt(): Int {
    log("decodeInt() returns 4242")
    return 4242
  }

  override fun decodeLong(): Long {
    log("decodeLong() returns 987654")
    return 987654
  }

  @ExperimentalSerializationApi
  override fun decodeNotNullMark(): Boolean {
    log("decodeNotNullMark() returns false")
    return false
  }

  @ExperimentalSerializationApi
  override fun decodeNull(): Nothing? {
    log("decodeNull() returns null")
    return null
  }

  override fun decodeShort(): Short {
    log("decodeShort() returns 554433")
    return 554433.toShort()
  }

  override fun decodeString(): String {
    log("decodeString() returns \"Hello World\"")
    return "Hello World"
  }

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
    log(
      "decodeBooleanElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns false"
    )
    return false
  }

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
    log(
      "decodeByteElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns 66"
    )
    return 66
  }

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
    log(
      "decodeCharElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns X"
    )
    return 'X'
  }

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double {
    log(
      "decodeDoubleElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns 543.21"
    )
    return 543.21
  }

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    log("zzyzx elements=$elements")
    if (elements === null || nextElementIndex >= elements.size) {
      log("decodeElementIndex() returns DECODE_DONE")
      return CompositeDecoder.DECODE_DONE
    }
    val elementIndex = nextElementIndex++
    log("decodeElementIndex() returns $elementIndex")
    return elementIndex
  }

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
    log(
      "decodeFloatElement() index=$index elementName=${descriptor.getElementName(index)}" +
        "returns 987.65"
    )
    return 987.65f
  }

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
    log("decodeInlineElement() index=$index elementName=${descriptor.getElementName(index)}")
    return LoggingDecoder(idBySerialDescriptor)
  }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
    log(
      "decodeIntElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns 5555"
    )
    return 5555
  }

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
    log(
      "decodeLongElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns 848484848"
    )
    return 848484848
  }

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
    log(
      "decodeShortElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns 443344"
    )
    return 443344.toShort()
  }

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
    log(
      "decodeStringElement() index=$index elementName=${descriptor.getElementName(index)}" +
        " returns \"Goodbye Cruel World\""
    )
    return "Goodbye Cruel World"
  }

  @ExperimentalSerializationApi
  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ): T? {
    log(
      "decodeNullableSerializableElement()" +
        "index=$index elementName=${descriptor.getElementName(index)}" +
        " previousValue=$previousValue" +
        " returns null"
    )
    return null
  }

  override fun <T : Any?> decodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T>,
    previousValue: T?
  ): T {
    log(
      "decodeSerializableElement()" +
        "index=$index elementName=${descriptor.getElementName(index)}" +
        " previousValue=$previousValue"
    )
    return decodeSerializableValue(deserializer)
  }

  companion object {

    fun <T : Any> decode(serializer: DeserializationStrategy<T>, value: Map<*, *>): T =
      LoggingDecoder(value).decodeSerializableValue(serializer)

    inline fun <reified T : Any> decode(value: Map<*, *>): T = decode(serializer(), value)

    private val nextEncoderId = AtomicLong(0)
    private val nextSerialDescriptorId = AtomicLong(998800000L)
  }
}
