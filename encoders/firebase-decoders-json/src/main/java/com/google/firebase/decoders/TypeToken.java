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
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

// TODO: implement hashCode(), equals(), and toString().

/**
 * {@link TypeToken} is used to represent types supported by the library in a type-safe manner.
 *
 * <p>{@link TypeToken} supports the following types:
 *
 * <ol>
 *   <li>Primitive types
 *   <li>Array Types: primitive arrays(i.e. {@code int[]}) and object arrays(i.e. {@code Foo[]})
 *   <li>Plain class types. i.e. {@code Foo}
 *   <li>Generic types. i.e. {@code Foo<String, Double>}
 *   <li>Wildcard types: only support {@code Foo<? extend Bar>} by downgrading it to {@code
 *       Foo<Bar>}, and throw exception when stumbled upon {@code Foo<? super Bar>}.
 * </ol>
 */
public abstract class TypeToken<T> {

  /**
   * Return an {@link TypeToken} to represent generic type {@code T}.
   *
   * <p>For example:
   *
   * <p>Create an {@code TypeToken<List<String>} of generic type {@code List<String>}:
   *
   * <p>{@code TypeToken<Link<String>> token = TypeToken.of(new Safe<Link<String>>() {});}
   */
  @NonNull
  public static <T> TypeToken<T> of(@NonNull Safe<T> token) {
    Type superclass = token.getClass().getGenericSuperclass();
    if (superclass instanceof Class) {
      throw new IllegalArgumentException("Missing type parameters");
    }
    @SuppressWarnings("ConstantConditions") // Safe because superclass is not instanceof Class
    Type type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    return of(type);
  }

  /**
   * Return an {@link TypeToken} to represent plain class type {@code T}.
   *
   * <p>For example:
   *
   * <p>Create an {@code TypeToken<Foo>} of type {@code Foo}:
   *
   * <p>{@code TypeToken<Foo> token = TypeToken.of(Foo.class);}
   */
  @NonNull
  public static <T> TypeToken<T> of(@NonNull Class<T> typeToken) {
    return of((Type) typeToken);
  }

  @NonNull
  private static <T> TypeToken<T> of(@NonNull Type type) {
    if (type instanceof WildcardType) {
      if (((WildcardType) type).getLowerBounds().length == 0) {
        return of(((WildcardType) type).getUpperBounds()[0]);
      }
      throw new IllegalArgumentException("<? super T> is not supported");
    } else if (type instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) type).getGenericComponentType();
      return new ArrayToken<T>(TypeToken.of(componentType));
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      @SuppressWarnings(
          "unchecked") // Safe because rawType of parameterizedType is always instance of Class<T>
      Class<T> rawType = (Class<T>) parameterizedType.getRawType();
      Type[] types = parameterizedType.getActualTypeArguments();
      TypeToken[] typeTokens = new TypeToken[types.length];
      for (int i = 0; i < types.length; i++) {
        typeTokens[i] = TypeToken.of(types[i]);
      }
      return new ClassToken<T>(rawType, new TypeTokenContainer(typeTokens));
    } else if (type instanceof Class<?>) {
      @SuppressWarnings("unchecked") // Safe because type is instance of Class<?>
      Class<T> typeToken = (Class<T>) type;
      if (typeToken.isArray()) {
        Class<?> componentTypeToken = typeToken.getComponentType();
        @SuppressWarnings(
            "ConstantConditions") // Safe because typeToken is an Array and componentTypeToken will
                                  // never be null
        ArrayToken<T> arrayToken = new ArrayToken<T>(TypeToken.of(componentTypeToken));
        return arrayToken;
      }
      return new ClassToken<T>(typeToken);
    } else {
      throw new IllegalArgumentException("Type: " + type.toString() + " not supported.");
    }
  }

  private TypeToken() {}

  /**
   * {@link ClassToken} is used to represent types in a type-safe manner, including Primitive types,
   * Plain class types, Generic types, and Wildcard types.
   */
  public static class ClassToken<T> extends TypeToken<T> {
    private final Class<T> rawType;
    private final TypeTokenContainer typeArguments;

    private ClassToken(Class<T> rawType) {
      this.rawType = rawType;
      this.typeArguments = TypeTokenContainer.EMPTY;
    }

    private ClassToken(Class<T> rawType, TypeTokenContainer typeArguments) {
      this.rawType = rawType;
      this.typeArguments = typeArguments;
    }

    @NonNull
    public Class<T> getRawType() {
      return rawType;
    }

    @NonNull
    public TypeTokenContainer getTypeArguments() {
      return typeArguments;
    }
  }

  /**
   * {@link ArrayToken} is used to represent Array types in a type-safe manner. such as: primitive
   * arrays(i.e. {@code int[]}) and object arrays(i.e. {@code Foo[]})
   */
  public static class ArrayToken<T> extends TypeToken<T> {
    private final TypeToken<?> componentType;

    private ArrayToken(TypeToken<?> componentType) {
      this.componentType = componentType;
    }

    @NonNull
    public TypeToken<?> getComponentType() {
      return componentType;
    }
  }
}
