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
import com.google.firebase.encoders.proto.ProtobufEncoder;

@Encodable
public class OtherTypes {
  private static final ProtobufEncoder ENCODER =
      ProtobufEncoder.builder().configureWith(AutoOtherTypesEncoder.CONFIG).build();

  private final String str;
  private final byte[] bytes;
  private final boolean bool;
  private final Boolean wrappedBool;

  public OtherTypes(String str, byte[] bytes, boolean bool, Boolean wrappedBool) {
    this.str = str;
    this.bytes = bytes;
    this.bool = bool;
    this.wrappedBool = wrappedBool;
  }

  public OtherTypes() {
    this("", new byte[0], false, null);
  }

  @Protobuf(tag = 1)
  public String getStr() {
    return str;
  }

  @Protobuf(tag = 2)
  public byte[] getBytes() {
    return bytes;
  }

  @Protobuf(tag = 3)
  public boolean isBool() {
    return bool;
  }

  @Protobuf(tag = 4)
  public Boolean getWrappedBool() {
    return wrappedBool;
  }

  public byte[] encode() {
    return ENCODER.encode(this);
  }
}
