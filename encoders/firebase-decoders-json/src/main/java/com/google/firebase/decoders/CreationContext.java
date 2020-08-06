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

package com.google.firebase.decoders;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@link CreationContext} is used by {@link TypeCreator} to get type-safe data of given {@link
 * FieldRef} to create instance of corresponding type.
 *
 * <p>A class implemented {@link CreationContext} should be initialized to store decoded data of a
 * single instance, and should be able to provide type-safe data of given {@link FieldRef}.
 */
public interface CreationContext {
  /** Get type-safe instance of type {@code TField} by given {@code FieldRef.Boxed<TField>} */
  @Nullable
  <TField> TField get(@NonNull FieldRef.Boxed<TField> ref);

  /** Get boolean by given {@code FieldRef.Primitive<Boolean>} */
  boolean getBoolean(@NonNull FieldRef.Primitive<Boolean> ref);

  /** Get int by given {@code FieldRef.Primitive<Integer>} */
  int getInteger(@NonNull FieldRef.Primitive<Integer> ref);

  /** Get short by given {@code FieldRef.Primitive<Short>} */
  short getShort(@NonNull FieldRef.Primitive<Short> ref);

  /** Get long by given {@code FieldRef.Primitive<Long>} */
  long getLong(@NonNull FieldRef.Primitive<Long> ref);

  /** Get float by given {@code FieldRef.Primitive<Float>} */
  float getFloat(@NonNull FieldRef.Primitive<Float> ref);

  /** Get double by given {@code FieldRef.Primitive<Double>} */
  double getDouble(@NonNull FieldRef.Primitive<Double> ref);

  /** Get double by given {@code FieldRef.Primitive<Character>} */
  char getChar(@NonNull FieldRef.Primitive<Character> ref);
}
