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

import com.google.firebase.encoders.proto.pojos.Fixed;
import com.google.firebase.encoders.proto.tests.FixedProto;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FixedEncodingTests {
  @Test
  public void test() {
    assertThat(new Fixed().encode()).isEmpty();
  }

  @Test
  public void test2() throws InvalidProtocolBufferException {
    byte[] result = new Fixed(56, -42, 999, -2000).encode();
    FixedProto parsed = FixedProto.parseFrom(result);

    assertThat(parsed)
        .isEqualTo(
            FixedProto.newBuilder().setF32(56).setSf32(-42).setF64(999).setSf64(-2000).build());
  }
}
