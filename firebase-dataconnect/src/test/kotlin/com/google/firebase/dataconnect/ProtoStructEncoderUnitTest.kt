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
import com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtoStructEncoderUnitTest {

  @Test
  fun `encodeToStruct() should throw if a NUMBER_VALUE is produced`() {
    val exception = assertThrows(IllegalArgumentException::class.java) { encodeToStruct(42) }
    assertThat(exception).hasMessageThat().contains("NUMBER_VALUE")
  }

  @Test
  fun `encodeToStruct() should throw if a BOOL_VALUE is produced`() {
    val exception = assertThrows(IllegalArgumentException::class.java) { encodeToStruct(true) }
    assertThat(exception).hasMessageThat().contains("BOOL_VALUE")
  }

  @Test
  fun `encodeToStruct() should throw if a STRING_VALUE is produced`() {
    val exception =
      assertThrows(IllegalArgumentException::class.java) {
        encodeToStruct("arbitrary string value")
      }
    assertThat(exception).hasMessageThat().contains("STRING_VALUE")
  }

  @Test
  fun `encodeToStruct() should throw if a LIST_VALUE is produced`() {
    val exception =
      assertThrows(IllegalArgumentException::class.java) {
        encodeToStruct(listOf("element1", "element2"))
      }
    assertThat(exception).hasMessageThat().contains("LIST_VALUE")
  }

  @Test
  fun `encodeToStruct() should return an empty struct if an empty map is given`() {
    val encodedStruct = encodeToStruct(emptyMap<Unit, Unit>())
    assertThat(encodedStruct).isEqualToDefaultInstance()
  }

  @Test
  fun `encodeToStruct() should encode Unit as an empty struct`() {
    val encodedStruct = encodeToStruct(Unit)
    assertThat(encodedStruct).isEqualToDefaultInstance()
  }

  @Test
  fun `encodeToStruct() should encode an class with all primitive types`() {
    @Serializable
    data class TestData(
      val iv: Int,
      val dv: Double,
      val bvt: Boolean,
      val bvf: Boolean,
      val sv: String,
      val nsvn: String?,
      val nsvnn: String?
    )
    val encodedStruct =
      encodeToStruct(
        TestData(
          iv = 42,
          dv = 1234.5,
          bvt = true,
          bvf = false,
          sv = "blah blah",
          nsvn = null,
          nsvnn = "I'm not null"
        )
      )

    assertThat(encodedStruct)
      .isEqualTo(
        buildStructProto {
          put("iv", 42.0)
          put("dv", 1234.5)
          put("bvt", true)
          put("bvf", false)
          put("sv", "blah blah")
          put("nsvnn", "I'm not null")
        }
      )
  }

  @Test
  fun `encodeToStruct() should encode lists with all primitive types`() {
    @Serializable
    data class TestData(
      val iv: List<Int>,
      val dv: List<Double>,
      val bv: List<Boolean>,
      val sv: List<String>,
      val nsv: List<String?>
    )
    val encodedStruct =
      encodeToStruct(
        TestData(
          iv = listOf(42, 43),
          dv = listOf(1234.5, 5678.9),
          bv = listOf(true, false, false, true),
          sv = listOf("abcde", "fghij"),
          nsv = listOf("klmno", null, "pqrst", null)
        )
      )

    assertThat(encodedStruct)
      .isEqualTo(
        buildStructProto {
          putList("iv") {
            add(42.0)
            add(43.0)
          }
          putList("dv") {
            add(1234.5)
            add(5678.9)
          }
          putList("bv") {
            add(true)
            add(false)
            add(false)
            add(true)
          }
          putList("sv") {
            add("abcde")
            add("fghij")
          }
          putList("nsv") {
            add("klmno")
            addNull()
            add("pqrst")
            addNull()
          }
        }
      )
  }

  @Test
  fun `encodeToStruct() should support nested composite types`() {
    @Serializable data class TestData3(val s: String)
    @Serializable data class TestData2(val data3: TestData3, val data3N: TestData3?)
    @Serializable data class TestData1(val data2: TestData2)
    val encodedStruct = encodeToStruct(TestData1(TestData2(TestData3("zzzz"), null)))

    assertThat(encodedStruct)
      .isEqualTo(
        buildStructProto { putStruct("data2") { putStruct("data3") { put("s", "zzzz") } } }
      )
  }
}

/**
 * An encoder that can be useful during testing to simply print the method invocations in order to
 * discover how an encoder should be implemented.
 */
private class LoggingEncoder(
  private val idBySerialDescriptor: MutableMap<SerialDescriptor, Long> = mutableMapOf()
) : Encoder, CompositeEncoder {
  val id = nextEncoderId.incrementAndGet()

  override val serializersModule = EmptySerializersModule()

  private fun log(message: String) {
    println("zzyzx LoggingEncoder[$id] $message")
  }

  private fun idFor(descriptor: SerialDescriptor) =
    idBySerialDescriptor[descriptor]
      ?: nextSerialDescriptorId.incrementAndGet().also { idBySerialDescriptor[descriptor] = it }

  override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
    log(
      "beginStructure() descriptorId=${idFor(descriptor)} kind=${descriptor.kind} " +
        "elementsCount=${descriptor.elementsCount}"
    )
    return LoggingEncoder(idBySerialDescriptor)
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    log("endStructure() descriptorId=${idFor(descriptor)} kind=${descriptor.kind}")
  }

  override fun encodeBoolean(value: Boolean) {
    log("encodeBoolean($value)")
  }

  override fun encodeByte(value: Byte) {
    log("encodeByte($value)")
  }

  override fun encodeChar(value: Char) {
    log("encodeChar($value)")
  }

  override fun encodeDouble(value: Double) {
    log("encodeDouble($value)")
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    log("encodeEnum($index)")
  }

  override fun encodeFloat(value: Float) {
    log("encodeFloat($value)")
  }

  override fun encodeInline(descriptor: SerialDescriptor): Encoder {
    log("encodeInline() kind=${descriptor.kind} serialName=${descriptor.serialName}")
    return LoggingEncoder(idBySerialDescriptor)
  }

  override fun encodeInt(value: Int) {
    log("encodeInt($value)")
  }

  override fun encodeLong(value: Long) {
    log("encodeLong($value)")
  }

  @ExperimentalSerializationApi
  override fun encodeNull() {
    log("encodeNull()")
  }

  override fun encodeShort(value: Short) {
    log("encodeShort($value)")
  }

  override fun encodeString(value: String) {
    log("encodeString($value)")
  }

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
    log("encodeBooleanElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    log("encodeByteElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    log("encodeCharElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    log("encodeDoubleElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    log("encodeFloatElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    log("encodeInlineElement() index=$index elementName=${descriptor.getElementName(index)}")
    return LoggingEncoder(idBySerialDescriptor)
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    log("encodeIntElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    log("encodeLongElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    log("encodeShortElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    log("encodeStringElement($value) index=$index elementName=${descriptor.getElementName(index)}")
  }

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    log(
      "encodeNullableSerializableElement($value) index=$index elementName=${descriptor.getElementName(index)}"
    )
    if (value != null) {
      encodeSerializableValue(serializer, value)
    }
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    log(
      "encodeSerializableElement($value) index=$index elementName=${descriptor.getElementName(index)}"
    )
    encodeSerializableValue(serializer, value)
  }

  companion object {

    fun <T : Any> encode(serializer: SerializationStrategy<T>, value: T) {
      LoggingEncoder().encodeSerializableValue(serializer, value)
    }

    inline fun <reified T : Any> encode(value: T) = encode(serializer(), value)

    private val nextEncoderId = AtomicLong(0)
    private val nextSerialDescriptorId = AtomicLong(998800000L)
  }
}
