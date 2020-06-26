// Copyright 2020 Google LLC
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

package com.google.firebase.encoders.protobuf;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.encoders.GenericEncoder;
import com.google.firebase.encoders.protobuf.Hello.HeyHey;
import com.google.firebase.encoders.protobuf.Hello.World;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtobufDataEncoderBuilderTest {
  @Test
  public void test() throws InvalidProtocolBufferException {
    GenericEncoder<OutputStream, byte[]> encoder =
        new ProtobufDataEncoderBuilder().configureWith(AutoHelloEncoder.CONFIG).build();

    HashMap<String, Integer> map = new HashMap<>();
    map.put("key", 42);

    byte[] resultBytes =
        encoder.encode(
            new Hello(
                150,
                "testing",
                Arrays.asList(
                    new World(true, map, new HeyHey("bar")),
                    new World(false, new LinkedHashMap<>(), new HeyHey("baz")))));

    com.google.firebase.encoders.proto.Hello hello =
        com.google.firebase.encoders.proto.Hello.newBuilder()
            .setMyInt(150)
            .setStr("testing")
            .addWorld(
                com.google.firebase.encoders.proto.Hello.World.newBuilder()
                    .setMyBool(true)
                    .putMyMap("key", 42)
                    .setHeyHey(
                        com.google.firebase.encoders.proto.Hello.HeyHey.newBuilder()
                            .setBar("bar")
                            .build())
                    .build())
            .addWorld(
                com.google.firebase.encoders.proto.Hello.World.newBuilder()
                    .setMyBool(false)
                    .setHeyHey(
                        com.google.firebase.encoders.proto.Hello.HeyHey.newBuilder()
                            .setBar("baz")
                            .build())
                    .build())
            .build();

    com.google.firebase.encoders.proto.Hello parsed =
        com.google.firebase.encoders.proto.Hello.parseFrom(resultBytes);

    assertThat(parsed).isEqualTo(hello);
  }
}
