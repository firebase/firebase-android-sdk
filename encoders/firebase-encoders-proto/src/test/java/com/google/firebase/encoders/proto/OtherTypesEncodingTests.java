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

import com.google.firebase.encoders.proto.pojos.OtherTypes;
import com.google.firebase.encoders.proto.tests.OtherTypesProto;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.Charset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OtherTypesEncodingTests {
  @Test
  public void encode_withDefaults() {
    assertThat(new OtherTypes().encode()).isEmpty();
  }

  @Test
  public void encode_withNonDefaultValues() throws InvalidProtocolBufferException {
    byte[] result =
        new OtherTypes("hello", "world".getBytes(Charset.forName("UTF-8")), true, true).encode();
    OtherTypesProto parsed = OtherTypesProto.parseFrom(result);
    assertThat(parsed)
        .isEqualTo(
            OtherTypesProto.newBuilder()
                .setStr("hello")
                .setBts(ByteString.copyFrom("world", Charset.forName("UTF-8")))
                .setBl(true)
                .setWrappedBool(true)
                .build());
  }
}
