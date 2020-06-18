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
    if (val == null) return null;
    @SuppressWarnings("unchecked")
    TField tField = (TField) val;
    return tField;
  }

  @Override
  public boolean getBoolean(@NonNull FieldRef.Primitive<Boolean> ref) {
    return (boolean) ctx.get(ref);
  }

  @Override
  public int getInteger(@NonNull FieldRef.Primitive<Integer> ref) {
    return (int) ctx.get(ref);
  }

  @Override
  public short getShort(@NonNull FieldRef.Primitive<Short> ref) {
    return (short) ctx.get(ref);
  }

  @Override
  public long getLong(@NonNull FieldRef.Primitive<Long> ref) {
    return (long) ctx.get(ref);
  }

  @Override
  public float getFloat(@NonNull FieldRef.Primitive<Float> ref) {
    return (float) ctx.get(ref);
  }

  @Override
  public double getDouble(@NonNull FieldRef.Primitive<Double> ref) {
    return (double) ctx.get(ref);
  }

  @Override
  public char getChar(@NonNull FieldRef.Primitive<Character> ref) {
    return (char) ctx.get(ref);
  }
}
