/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.encoders.proto.codegen

import com.google.common.truth.Truth.assertThat
import com.google.firebase.encoders.proto.CodeGenConfig
import com.google.firebase.encoders.proto.codegen.UserDefined.Message
import com.google.firebase.encoders.proto.codegen.UserDefined.ProtoEnum
import com.google.firebase.testing.Extendable
import com.google.firebase.testing.LinkedListProto
import com.google.firebase.testing.SimpleProto
import com.google.firebase.testing.Types
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Timestamp
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ParsingTests {
  @Test
  fun `parse with simple message, enum and import should succeed`() {
    val result =
      parse(
        SimpleProto.getDescriptor().file,
        Timestamp.getDescriptor().file,
        include = listOf(SimpleProto.getDefaultInstance())
      )

    val msg = result.messageAt(0)
    assertThat(msg.protobufFullName).isEqualTo("com.google.firebase.testing.SimpleProto")
    assertThat(msg.javaName).isEqualTo("com.example.com.google.firebase.testing.SimpleProto")

    assertThat(msg.fields)
      .containsAtLeast(
        ProtoField(name = "value", number = 1, type = Primitive.INT32),
        ProtoField(name = "str", number = 2, type = Primitive.STRING, repeated = true)
      )
    val timestamp = msg.fields.withName("time")
    assertThat(timestamp.number).isEqualTo(3)
    assertThat(timestamp.repeated).isEqualTo(false)

    val tsMsg = timestamp.messageType
    assertThat(tsMsg.javaName).isEqualTo("com.example.google.protobuf.Timestamp")
    assertThat(tsMsg.protobufFullName).isEqualTo("google.protobuf.Timestamp")
    assertThat(tsMsg.fields)
      .containsExactly(
        ProtoField(name = "seconds", number = 1, type = Primitive.INT64),
        ProtoField(name = "nanos", number = 2, type = Primitive.INT32)
      )

    val enum =
      msg.fields.withName("my_enum").type as? ProtoEnum
        ?: throw AssertionError("my_enum is not an enum")

    assertThat(enum.javaName)
      .isEqualTo("com.example.com.google.firebase.testing.SimpleProto\$MyEnum")
    assertThat(enum.values)
      .containsExactly(ProtoEnum.Value("DEFAULT", 0), ProtoEnum.Value("EXAMPLE", 1))
  }

  @Test
  fun `parse all primitive types should succeed`() {

    val result = parse(Types.getDescriptor().file, include = listOf(Types.getDefaultInstance()))

    val msg = result.messageAt(0)
    assertThat(msg.protobufFullName).isEqualTo("com.google.firebase.testing.Types")
    assertThat(msg.javaName).isEqualTo("com.example.com.google.firebase.testing.Types")

    val types32 = msg.messageFieldNamed("types32")

    assertThat(types32.fields)
      .containsExactly(
        ProtoField(name = "i", number = 1, type = Primitive.INT32),
        ProtoField(name = "si", number = 2, type = Primitive.SINT32),
        ProtoField(name = "f", number = 3, type = Primitive.FIXED32),
        ProtoField(name = "sf", number = 4, type = Primitive.SFIXED32),
        ProtoField(name = "fl", number = 5, type = Primitive.FLOAT),
        ProtoField(name = "ui", number = 6, type = Primitive.INT32)
      )

    val types64 = msg.messageFieldNamed("types64")

    assertThat(types64.fields)
      .containsExactly(
        ProtoField(name = "i", number = 1, type = Primitive.INT64),
        ProtoField(name = "si", number = 2, type = Primitive.SINT64),
        ProtoField(name = "f", number = 3, type = Primitive.FIXED64),
        ProtoField(name = "sf", number = 4, type = Primitive.SFIXED64),
        ProtoField(name = "db", number = 5, type = Primitive.DOUBLE),
        ProtoField(name = "ui", number = 6, type = Primitive.INT64)
      )

    val other = msg.messageFieldNamed("other")

    assertThat(other.fields)
      .containsExactly(
        ProtoField(name = "boolean", number = 1, type = Primitive.BOOLEAN),
        ProtoField(name = "str", number = 2, type = Primitive.STRING),
        ProtoField(name = "bts", number = 3, type = Primitive.BYTES)
      )
  }

  @Test
  fun `parse with self-referencing message should succeed`() {
    val result =
      parse(
        LinkedListProto.getDescriptor().file,
        include = listOf(LinkedListProto.getDefaultInstance())
      )

    val msg = result.messageAt(0)
    assertThat(msg.name).isEqualTo("LinkedListProto")
    assertThat(msg.fields)
      .containsExactly(
        ProtoField(name = "value", number = 1, type = Primitive.INT32),
        ProtoField(name = "self", number = 2, type = msg)
      )
  }

  @Test
  fun `parse with extensions should add fields to extended message`() {
    val result =
      parse(Extendable.getDescriptor().file, include = listOf(Extendable.getDefaultInstance()))

    val msg = result.messageAt(0)
    assertThat(msg.fields).hasSize(2)

    assertThat(msg.fields.withName("extended"))
      .isEqualTo(ProtoField(name = "extended", number = 101, type = Primitive.STRING))

    val nested = msg.messageFieldNamed("nested")
    val ext = nested.messageFieldNamed("ext")
    assertThat(ext).isSameInstanceAs(msg)
  }
}

private fun parse(
  vararg files: FileDescriptor,
  include: List<GeneratedMessageV3> = listOf()
): List<UserDefined> {
  return DefaultParser(
      CodeGenConfig.newBuilder()
        .setVendorPackage("com.example")
        .addAllInclude(include.map { it.descriptorForType.fullName })
        .build()
    )
    .parse(files.map { it.toProto() })
}

private fun List<UserDefined>.messageAt(index: Int): Message {
  assertThat(this).hasSize(index + 1)
  assertThat(this[index]).isInstanceOf(Message::class.java)
  return this[index] as Message
}

private fun List<ProtoField>.withName(name: String): ProtoField {
  return this.find { it.name == name }
    ?: throw AssertionError("Did not find expected $name field in the message")
}

private val ProtoField.messageType: Message
  get() = type as? Message ?: throw AssertionError("Field $name is not a message.")

private fun Message.messageFieldNamed(name: String): Message {
  return this.fields.asSequence().find { it.name == name }?.messageType
    ?: throw AssertionError("Did not find expected $name field in the message")
}
