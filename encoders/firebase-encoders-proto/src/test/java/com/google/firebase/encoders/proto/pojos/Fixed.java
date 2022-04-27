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

package com.google.firebase.encoders.proto.pojos;

import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.proto.Protobuf;
import com.google.firebase.encoders.proto.Protobuf.IntEncoding;
import com.google.firebase.encoders.proto.ProtobufEncoder;

@Encodable
public class Fixed {
  private static final ProtobufEncoder ENCODER =
      ProtobufEncoder.builder().configureWith(AutoFixedEncoder.CONFIG).build();

  private final int f32;
  private final int sf32;
  private final long f64;
  private final long sf64;

  public Fixed(int f32, int sf32, long f64, long sf64) {
    this.f32 = f32;
    this.sf32 = sf32;
    this.f64 = f64;
    this.sf64 = sf64;
  }

  public Fixed() {
    this(0, 0, 0, 0);
  }

  @Protobuf(tag = 1, intEncoding = IntEncoding.FIXED)
  public int getF32() {
    return f32;
  }

  @Protobuf(tag = 2, intEncoding = IntEncoding.FIXED)
  public int getSf32() {
    return sf32;
  }

  @Protobuf(tag = 3, intEncoding = IntEncoding.FIXED)
  public long getF64() {
    return f64;
  }

  @Protobuf(tag = 4, intEncoding = IntEncoding.FIXED)
  public long getSf64() {
    return sf64;
  }

  public byte[] encode() {
    return ENCODER.encode(this);
  }
}
