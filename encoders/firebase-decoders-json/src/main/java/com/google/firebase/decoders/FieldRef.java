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

import java.util.Map;

// TODO: support required, optional.

/**
 * {@link FieldRef} act as an wire to put decoded information to {@link ObjectDecoderContext} and
 * get decoded information from {@link CreationContext}. It also provide {@link DataDecoder} the
 * ability to obtain the data type of nextToken.
 */
public abstract class FieldRef<T> {
  private TypeToken<T> typeToken;
  private Map<Class<?>, Object> properties;

  @NonNull
  public abstract TypeToken<T> getTypeToken();

  @NonNull
  public abstract Map<Class<?>, Object> getProperties();

  @NonNull
  public static final Primitive<Boolean> BOOLEAN = new Primitive<>(TypeToken.of(boolean.class));

  @NonNull public static final Primitive<Short> SHORT = new Primitive<>(TypeToken.of(short.class));

  @NonNull public static final Primitive<Long> LONG = new Primitive<>(TypeToken.of(long.class));

  @NonNull public static final Primitive<Float> FLOAT = new Primitive<>(TypeToken.of(float.class));

  @NonNull
  public static final Primitive<Double> DOUBLE = new Primitive<>(TypeToken.of(double.class));

  @NonNull
  public static final Primitive<Character> CHAR = new Primitive<>(TypeToken.of(char.class));

  @NonNull public static final Primitive<Integer> INT = new Primitive<>(TypeToken.of(int.class));

  @NonNull
  public static <T> Boxed<T> of(@NonNull TypeToken<T> typeToken) {
    return new Boxed<T>(typeToken, null);
  }

  @NonNull
  public static <T> Boxed<T> of(@NonNull TypeToken<T> typeToken, @NonNull Map<Class<?>, Object> properties) {
    return new Boxed<T>(typeToken, properties);
  }

  /** Used to represent primitive data type. */
  public static final class Primitive<T> extends FieldRef<T> {
    private Primitive(@NonNull TypeToken<T> typeToken) {
      super.typeToken = typeToken;
      super.properties = null;
    }

    @NonNull
    @Override
    public TypeToken<T> getTypeToken() {
      return super.typeToken;
    }

    @NonNull
    @Override
    public Map<Class<?>, Object> getProperties() {
      return null;
    }
  }

  /** Use it to represent Boxed Data type */
  public static final class Boxed<T> extends FieldRef<T> {

    private Boxed(@NonNull TypeToken<T> typeToken, @Nullable Map<Class<?>, Object> properties) {
      if (typeToken instanceof TypeToken.ClassToken) {
        Class<?> clazz = ((TypeToken.ClassToken<T>) typeToken).getRawType();
        if (clazz.isPrimitive())
          throw new IllegalArgumentException(
                  "FieldRef.Boxed<T> can only be used to hold non-primitive type.\n"
                          + clazz
                          + "was found.");
      }
      super.typeToken = typeToken;
      super.properties = properties;
    }

    @NonNull
    @Override
    public TypeToken<T> getTypeToken() {
      return super.typeToken;
    }

    @NonNull
    @Override
    public Map<Class<?>, Object> getProperties() {
      return super.properties;
    }
  }
}
