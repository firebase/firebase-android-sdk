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
import com.google.firebase.decoders.ObjectDecoderContext;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.FieldDescriptor;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ObjectDecoderContextImpl<T> implements ObjectDecoderContext<T> {
  private final Map<String, FieldRef<?>> refs = new HashMap<>();
  private final TypeToken.ClassToken<T> classToken;

  @NonNull
  public static <T> ObjectDecoderContextImpl<T> of(@NonNull TypeToken.ClassToken<T> classToken) {
    return new ObjectDecoderContextImpl<T>(classToken);
  }

  private ObjectDecoderContextImpl(TypeToken.ClassToken<T> classToken) {
    this.classToken = classToken;
  }

  @NonNull
  FieldRef<?> getFieldRef(@NonNull String fieldName) {
    if (refs.containsKey(fieldName)) {
      return refs.get(fieldName);
    } else {
      throw new IllegalArgumentException(fieldName + " was not register in ObjectDecoder.");
    }
  }

  @NonNull
  @Override
  public TypeToken.ClassToken<T> getTypeToken() {
    return classToken;
  }

  @NonNull
  @Override
  public <TTypeArgument> TypeToken<TTypeArgument> getTypeArgument(int index) {
    return classToken.getTypeArguments().at(index);
  }

  @NonNull
  @Override
  public <TField> FieldRef.Boxed<TField> decode(
      @NonNull FieldDescriptor fileDescriptor, @NonNull TypeToken<TField> typeToken) {
    FieldRef.Boxed<TField> ref = FieldRef.Boxed.of(typeToken);
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Boolean> decodeBoolean(@NonNull FieldDescriptor fileDescriptor) {
    FieldRef.Primitive<Boolean> ref = FieldRef.Primitive.BOOLEAN;
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Short> decodeShort(@NonNull FieldDescriptor fileDescriptor) {
    FieldRef.Primitive<Short> ref = FieldRef.Primitive.SHORT;
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Long> decodeLong(@NonNull FieldDescriptor fileDescriptor) {
    FieldRef.Primitive<Long> ref = FieldRef.Primitive.LONG;
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Float> decodeFloat(@NonNull FieldDescriptor fileDescriptor) {
    FieldRef.Primitive<Float> ref = FieldRef.Primitive.FLOAT;
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Double> decodeDouble(@NonNull FieldDescriptor fileDescriptor) {
    FieldRef.Primitive<Double> ref = FieldRef.Primitive.DOUBLE;
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Character> decodeChar(@NonNull FieldDescriptor fileDescriptor) {
    FieldRef.Primitive<Character> ref = FieldRef.Primitive.CHAR;
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }

  @NonNull
  @Override
  public FieldRef.Primitive<Integer> decodeInteger(@NonNull FieldDescriptor fileDescriptor) {
    FieldRef.Primitive<Integer> ref = FieldRef.Primitive.INT;
    refs.put(fileDescriptor.getName(), ref);
    return ref;
  }
}
