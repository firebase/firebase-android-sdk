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
import java.util.List;
import java.util.Map;

@Encodable
public class WithCollections {
  private static final ProtobufEncoder ENCODER =
      ProtobufEncoder.builder().configureWith(AutoWithCollectionsEncoder.CONFIG).build();

  private final String value;
  private final Map<String, Fixed> myMap;
  private final List<OtherTypes> otherTypes;

  public WithCollections(String value, Map<String, Fixed> myMap, List<OtherTypes> otherTypes) {
    this.value = value;
    this.myMap = myMap;
    this.otherTypes = otherTypes;
  }

  @Protobuf(tag = 1)
  public String getValue() {
    return value;
  }

  @Protobuf(tag = 2)
  public Map<String, Fixed> getMyMap() {
    return myMap;
  }

  @Protobuf(tag = 3)
  public List<OtherTypes> getOtherTypes() {
    return otherTypes;
  }

  public byte[] encode() {
    return ENCODER.encode(this);
  }
}
