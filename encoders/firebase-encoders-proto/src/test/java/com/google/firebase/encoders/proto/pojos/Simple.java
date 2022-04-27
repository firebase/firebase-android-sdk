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
public final class Simple {
  private static final ProtobufEncoder ENCODER =
      ProtobufEncoder.builder().configureWith(AutoSimpleEncoder.CONFIG).build();
  private final int i32;
  private final long i64;
  private final float aFloat;
  private final double aDouble;
  private final int ui32;
  private final long ui64;
  private final int si32;
  private final long si64;

  public Simple(
      int i32, long i64, float aFloat, double aDouble, int ui32, long ui64, int si32, long si64) {
    this.i32 = i32;
    this.i64 = i64;
    this.aFloat = aFloat;
    this.aDouble = aDouble;
    this.ui32 = ui32;
    this.ui64 = ui64;
    this.si32 = si32;
    this.si64 = si64;
  }

  public Simple() {
    this(0, 0, 0, 0, 0, 0, 0, 0);
  }

  @Protobuf(tag = 1)
  public int getI32() {
    return i32;
  }

  @Protobuf(tag = 2)
  public long getI64() {
    return i64;
  }

  @Protobuf(tag = 3)
  public float getAFloat() {
    return aFloat;
  }

  @Protobuf(tag = 4)
  public double getADouble() {
    return aDouble;
  }

  @Protobuf(tag = 5)
  public int getUi32() {
    return ui32;
  }

  @Protobuf(tag = 6)
  public long getUi64() {
    return ui64;
  }

  @Protobuf(tag = 7, intEncoding = IntEncoding.SIGNED)
  public int getSi32() {
    return si32;
  }

  @Protobuf(tag = 8, intEncoding = IntEncoding.SIGNED)
  public long getSi64() {
    return si64;
  }

  public byte[] encode() {
    return ENCODER.encode(this);
  }
}
