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
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TypeTests {
  @Test
  fun `proto and java qualified names work as expected`() {
    val package1 = Owner.Package("com.example", "com.example.proto", "my_proto.proto")
    val helloMsg = UserDefined.Message(owner = package1, name = "Hello", fields = listOf())
    val worldMsg = UserDefined.Message(Owner.MsgRef(helloMsg), "World", fields = listOf())

    assertThat(helloMsg.protobufFullName).isEqualTo("com.example.Hello")
    assertThat(helloMsg.javaName).isEqualTo("com.example.proto.Hello")

    assertThat(worldMsg.protobufFullName).isEqualTo("com.example.Hello.World")
    assertThat(worldMsg.javaName).isEqualTo("com.example.proto.Hello\$World")
  }

  @Test
  fun `ProtoField#toString() method should not cause stack overflow`() {
    /*
    syntax = "proto3";
    package com.example;
    option java_package = "com.example.proto";

    message Hello {
        message World {
          Hello hello = 1;
          int64 my_long = 2;
        }
        World world = 1;
        repeated int32 my_int = 2;
    }
     */
    val package1 = Owner.Package("com.example", "com.example.proto", "my_proto.proto")
    val worldField =
      ProtoField(name = "world", type = Unresolved("com.example.Hello.World"), number = 1)
    val helloField = ProtoField(name = "hello", type = Unresolved("com.example.Hello"), number = 1)
    val helloMsg =
      UserDefined.Message(
        owner = package1,
        name = "Hello",
        fields =
          listOf(
            worldField,
            ProtoField(name = "my_int", type = Primitive.INT32, number = 2, repeated = true)
          )
      )
    val worldMsg =
      UserDefined.Message(
        owner = Owner.MsgRef(helloMsg),
        name = "World",
        fields =
          listOf(helloField, ProtoField(name = "my_long", type = Primitive.INT64, number = 2))
      )
    worldField.type = worldMsg
    helloField.type = helloMsg

    assertThat(helloMsg.fields[0].toString()).contains("com.example.Hello.World")
  }
}
