// Copyright 2021 Google LLC
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

package com.google.firebase.testing.e2e;

import static com.google.common.truth.Truth.assertThat;

import com.example.com.google.firebase.testing.e2e.LinkedListProto;
import com.example.com.google.firebase.testing.e2e.Other;
import com.example.com.google.firebase.testing.e2e.SimpleProto;
import com.example.com.google.firebase.testing.e2e.Types;
import com.example.com.google.firebase.testing.e2e.Types32;
import com.example.com.google.firebase.testing.e2e.Types64;
import com.example.google.protobuf.Timestamp;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CodeGenTests {
  @Test
  public void codegen_withSimpleMessage_shouldProduceCorrectBytes()
      throws InvalidProtocolBufferException {
    SimpleProto msg =
        SimpleProto.newBuilder()
            .setValue(42)
            .addStr("hello")
            .setTime(Timestamp.newBuilder().setSeconds(123).setNanos(42).build())
            .setMyEnum(SimpleProto.MyEnum.EXAMPLE)
            .build();

    Simple.SimpleProto parsed = Simple.SimpleProto.parseFrom(msg.toByteArray());

    assertThat(parsed)
        .isEqualTo(
            Simple.SimpleProto.newBuilder()
                .setValue(42)
                .addStr("hello")
                .setTime(
                    com.google.protobuf.Timestamp.newBuilder().setSeconds(123).setNanos(42).build())
                .setMyEnum(Simple.SimpleProto.MyEnum.EXAMPLE)
                .build());
  }

  @Test
  public void codegen_with32BitVarints_shouldProduceCorrectBytes()
      throws InvalidProtocolBufferException {
    Types32 msg =
        Types32.newBuilder().setI(1).setSi(2).setF(3).setSf(4).setFl(5.0f).setUi(6).build();

    TypesOuterClass.Types32 parsed = TypesOuterClass.Types32.parseFrom(msg.toByteArray());

    assertThat(parsed)
        .isEqualTo(
            TypesOuterClass.Types32.newBuilder()
                .setI(1)
                .setSi(2)
                .setF(3)
                .setSf(4)
                .setFl(5.0f)
                .setUi(6)
                .build());
  }

  @Test
  public void codegen_with64BitVarints_shouldProduceCorrectBytes()
      throws InvalidProtocolBufferException {
    Types64 msg =
        Types64.newBuilder().setI(1).setSi(2).setF(3).setSf(4).setDb(5.0f).setUi(6).build();

    TypesOuterClass.Types64 parsed = TypesOuterClass.Types64.parseFrom(msg.toByteArray());

    assertThat(parsed)
        .isEqualTo(
            TypesOuterClass.Types64.newBuilder()
                .setI(1)
                .setSi(2)
                .setF(3)
                .setSf(4)
                .setDb(5.0f)
                .setUi(6)
                .build());
  }

  @Test
  public void codegen_withOtherPrimitives_shouldProduceCorrectBytes()
      throws InvalidProtocolBufferException {
    byte[] bytes = new byte[] {1, 2, 3};
    Other msg = Other.newBuilder().setBoolean(true).setStr("Hello").setBts(bytes).build();

    TypesOuterClass.Other parsed = TypesOuterClass.Other.parseFrom(msg.toByteArray());

    assertThat(parsed)
        .isEqualTo(
            TypesOuterClass.Other.newBuilder()
                .setBoolean(true)
                .setStr("Hello")
                .setBts(ByteString.copyFrom(bytes))
                .build());
  }

  @Test
  public void codegen_withNestedMessages_shouldProduceCorrectBytes()
      throws InvalidProtocolBufferException {
    Types value =
        Types.newBuilder()
            .setTypes32(Types32.newBuilder().setF(42).build())
            .setTypes64(Types64.newBuilder().setSi(-100).build())
            .setOther(Other.newBuilder().setStr("Hello").build())
            .build();

    TypesOuterClass.Types parsed = TypesOuterClass.Types.parseFrom(value.toByteArray());

    assertThat(parsed)
        .isEqualTo(
            TypesOuterClass.Types.newBuilder()
                .setTypes32(TypesOuterClass.Types32.newBuilder().setF(42).build())
                .setTypes64(TypesOuterClass.Types64.newBuilder().setSi(-100).build())
                .setOther(TypesOuterClass.Other.newBuilder().setStr("Hello").build())
                .build());
  }

  @Test
  public void codegen_withSelfReferencingMessage_shouldProduceCorrectBytes()
      throws InvalidProtocolBufferException {
    LinkedListProto value =
        LinkedListProto.newBuilder()
            .setValue(42)
            .setCons(LinkedListProto.newBuilder().setValue(43).build())
            .build();

    Cycle.LinkedListProto parsed = Cycle.LinkedListProto.parseFrom(value.toByteArray());

    assertThat(parsed)
        .isEqualTo(
            Cycle.LinkedListProto.newBuilder()
                .setValue(42)
                .setCons(Cycle.LinkedListProto.newBuilder().setValue(43).build())
                .build());
  }
}
