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

import com.google.firebase.encoders.proto.pojos.Simple;
import com.google.firebase.encoders.proto.tests.SimpleProto;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleEncodingTests {
  @Test
  public void test() {
    assertThat(new Simple().encode()).isEmpty();
  }

  @Test
  public void test2() throws InvalidProtocolBufferException {
    byte[] result = new Simple(-200, -999, 2.0f, 5.0d, 100, 2000, -1, -4).encode();
    SimpleProto parsed = SimpleProto.parseFrom(result);
    assertThat(parsed)
        .isEqualTo(
            SimpleProto.newBuilder()
                .setI32(-200)
                .setI64(-999)
                .setFloat(2.0f)
                .setDouble(5.0d)
                .setUi32(100)
                .setUi64(2000)
                .setSi32(-1)
                .setSi64(-4)
                .build());
  }
}
