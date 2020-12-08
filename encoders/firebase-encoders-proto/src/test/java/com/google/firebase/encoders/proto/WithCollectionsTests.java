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

package com.google.firebase.encoders.proto;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.encoders.proto.pojos.Fixed;
import com.google.firebase.encoders.proto.pojos.OtherTypes;
import com.google.firebase.encoders.proto.pojos.WithCollections;
import com.google.firebase.encoders.proto.tests.FixedProto;
import com.google.firebase.encoders.proto.tests.OtherTypesProto;
import com.google.firebase.encoders.proto.tests.WithCollectionsProto;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WithCollectionsTests {
  @Test
  public void test() {
    byte[] result =
        new WithCollections("", Collections.emptyMap(), Collections.emptyList()).encode();
    assertThat(result).isEmpty();
  }

  @Test
  public void test2() throws InvalidProtocolBufferException {

    byte[] result =
        new WithCollections(
                "hello",
                ImmutableMap.<String, Fixed>builder()
                    .put("noValue", new Fixed())
                    .put("value", new Fixed(1, 2, 3, 4))
                    .build(),
                ImmutableList.of(
                    new OtherTypes("hello", new byte[0], false, true),
                    new OtherTypes("", new byte[] {42}, true, false)))
            .encode();

    WithCollectionsProto parsed = WithCollectionsProto.parseFrom(result);

    assertThat(parsed)
        .isEqualTo(
            WithCollectionsProto.newBuilder()
                .setValue("hello")
                .putMyMap("noValue", FixedProto.getDefaultInstance())
                .putMyMap(
                    "value",
                    FixedProto.newBuilder().setF32(1).setSf32(2).setF64(3).setSf64(4).build())
                .addOtherTypes(
                    OtherTypesProto.newBuilder().setStr("hello").setWrappedBool(true).build())
                .addOtherTypes(
                    OtherTypesProto.newBuilder()
                        .setBts(ByteString.copyFrom(new byte[] {42}))
                        .setBl(true)
                        .build())
                .build());
  }
}
