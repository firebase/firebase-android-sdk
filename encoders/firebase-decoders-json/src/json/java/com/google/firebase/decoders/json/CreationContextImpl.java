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
import androidx.annotation.Nullable;
import com.google.firebase.decoders.CreationContext;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.EncodingException;
import java.util.Collections;
import java.util.HashMap;

public class CreationContextImpl implements CreationContext {
  private HashMap<FieldRef<?>, Object> ctx = new HashMap<>();

  CreationContextImpl() {}

  // TODO: avoid auto-boxing for all primitive types
  void put(@NonNull FieldRef<?> ref, @Nullable Object val) {
    ctx.put(ref, val);
  }

  @Nullable
  @Override
  public <TField> TField get(@NonNull FieldRef.Boxed<TField> ref) {
    Object val = ctx.get(ref);
    if (val == null) {
      return getDefault(ref.getTypeToken());
    }
    @SuppressWarnings("unchecked")
    TField tField = (TField) val;
    return tField;
  }

  @SuppressWarnings("unchecked")
  private <T> T getDefault(TypeToken<T> typeToken) {
    if (typeToken instanceof TypeToken.ArrayToken) {
      return JsonDataDecoderBuilderContext.convertGenericListToArray(
          Collections.emptyList(), (TypeToken.ArrayToken<T>) typeToken);
    } else if (typeToken instanceof TypeToken.ClassToken) {
      Class<T> clazz = typeToken.getRawType();
      if (clazz.equals(String.class)) {
        return (T) "";
      } else if (clazz.equals(Boolean.class)) {
        return (T) Boolean.valueOf(false);
      } else if (clazz.equals(Integer.class)) {
        return (T) Integer.valueOf(0);
      } else if (clazz.equals(Long.class)) {
        return (T) Long.valueOf(0);
      } else if (clazz.equals(Short.class)) {
        return (T) Short.valueOf((short) 0);
      } else if (clazz.equals(Float.class)) {
        return (T) Float.valueOf(0);
      } else if (clazz.equals(Double.class)) {
        return (T) Double.valueOf(0);
      } else if (clazz.equals(Character.class)) {
        return (T) (Character) Character.MIN_VALUE;
      } else {
        return null;
      }
    }
    throw new EncodingException("Unknown typeToken: " + typeToken);
  }

  @Override
  public boolean getBoolean(@NonNull FieldRef.Primitive<Boolean> ref) {
    Object val = ctx.get(ref);
    if (val == null) return false;
    return (boolean) val;
  }

  @Override
  public int getInteger(@NonNull FieldRef.Primitive<Integer> ref) {
    Object val = ctx.get(ref);
    if (val == null) return 0;
    return (int) val;
  }

  @Override
  public short getShort(@NonNull FieldRef.Primitive<Short> ref) {
    Object val = ctx.get(ref);
    if (val == null) return 0;
    return (short) val;
  }

  @Override
  public long getLong(@NonNull FieldRef.Primitive<Long> ref) {
    Object val = ctx.get(ref);
    if (val == null) return 0;
    return (long) val;
  }

  @Override
  public float getFloat(@NonNull FieldRef.Primitive<Float> ref) {
    Object val = ctx.get(ref);
    if (val == null) return 0;
    return (float) val;
  }

  @Override
  public double getDouble(@NonNull FieldRef.Primitive<Double> ref) {
    Object val = ctx.get(ref);
    if (val == null) return 0;
    return (double) val;
  }

  @Override
  public char getChar(@NonNull FieldRef.Primitive<Character> ref) {
    Object val = ctx.get(ref);
    if (val == null) return Character.MIN_VALUE;
    return (char) val;
  }
}
