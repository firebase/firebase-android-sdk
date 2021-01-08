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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.ValueEncoderContext;
import java.io.IOException;

class ProtobufValueEncoderContext implements ValueEncoderContext {
  private boolean encoded = false;
  private final FieldDescriptor field;
  private final ObjectEncoderContext objEncoderCtx;

  ProtobufValueEncoderContext(FieldDescriptor field, ObjectEncoderContext objEncoderCtx) {
    this.field = field;
    this.objEncoderCtx = objEncoderCtx;
  }

  private void checkNotUsed() {
    if (encoded) {
      throw new EncodingException("Cannot encode a second value in the ValueEncoderContext");
    }
    encoded = true;
  }

  @NonNull
  @Override
  public ValueEncoderContext add(@Nullable String value) throws IOException {
    checkNotUsed();
    objEncoderCtx.add(field, value);
    return this;
  }

  @NonNull
  @Override
  public ValueEncoderContext add(float value) throws IOException {
    checkNotUsed();
    objEncoderCtx.add(field, value);
    return this;
  }

  @NonNull
  @Override
  public ValueEncoderContext add(double value) throws IOException {
    checkNotUsed();
    objEncoderCtx.add(field, value);
    return this;
  }

  @NonNull
  @Override
  public ValueEncoderContext add(int value) throws IOException {
    checkNotUsed();
    objEncoderCtx.add(field, value);
    return this;
  }

  @NonNull
  @Override
  public ValueEncoderContext add(long value) throws IOException {
    checkNotUsed();
    objEncoderCtx.add(field, value);
    return this;
  }

  @NonNull
  @Override
  public ValueEncoderContext add(boolean value) throws IOException {
    checkNotUsed();
    objEncoderCtx.add(field, value);
    return this;
  }

  @NonNull
  @Override
  public ValueEncoderContext add(@NonNull byte[] bytes) throws IOException {
    checkNotUsed();
    objEncoderCtx.add(field, bytes);
    return this;
  }
}
