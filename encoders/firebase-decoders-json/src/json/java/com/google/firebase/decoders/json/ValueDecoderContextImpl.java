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

package com.google.firebase.decoders.json;

import androidx.annotation.NonNull;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.decoders.ValueDecoderContext;
import com.google.firebase.encoders.EncodingException;

class ValueDecoderContextImpl implements ValueDecoderContext {

  private FieldRef<?> ref;

  ValueDecoderContextImpl() {}

  FieldRef<?> getRef() {
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Boxed<String> decodeString() {
    if (isDecoded()) {
      throw new EncodingException("ValueDecoder can only be decoded once.");
    }
    FieldRef.Boxed<String> ref = FieldRef.of(TypeToken.of(String.class));
    this.ref = ref;
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Boolean> decodeBoolean() {
    if (isDecoded()) {
      throw new EncodingException("ValueDecoder can only be decoded once.");
    }
    FieldRef.Primitive<Boolean> ref = FieldRef.BOOLEAN;
    this.ref = ref;
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Integer> decodeInteger() {
    if (isDecoded()) {
      throw new EncodingException("ValueDecoder can only be decoded once.");
    }
    FieldRef.Primitive<Integer> ref = FieldRef.INT;
    this.ref = ref;
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Long> decodeLong() {
    if (isDecoded()) {
      throw new EncodingException("ValueDecoder can only be decoded once.");
    }
    FieldRef.Primitive<Long> ref = FieldRef.LONG;
    this.ref = ref;
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Double> decodeDouble() {
    if (isDecoded()) {
      throw new EncodingException("ValueDecoder can only be decoded once.");
    }
    FieldRef.Primitive<Double> ref = FieldRef.DOUBLE;
    this.ref = ref;
    return ref;
  }

  private boolean isDecoded() {
    return ref != null;
  }
}
