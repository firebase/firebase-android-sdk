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
import java.util.Arrays;

/**
 * {@link TypeTokenContainer} is used to get actual type parameter in a generic class at given
 * index.
 */
public final class TypeTokenContainer {
  private final TypeToken<?>[] typeTokens;

  @NonNull public static final TypeTokenContainer EMPTY = new TypeTokenContainer();

  private TypeTokenContainer() {
    typeTokens = new TypeToken[0];
  }

  public TypeTokenContainer(@NonNull TypeToken[] typeTokens) {
    this.typeTokens = typeTokens;
  }

  @NonNull
  public <T> TypeToken<T> at(int index) {
    if (index >= typeTokens.length || index < 0)
      throw new IllegalArgumentException("No type token at index: " + index);
    @SuppressWarnings("unchecked")
    TypeToken<T> typeToken = (TypeToken<T>) typeTokens[index];
    return typeToken;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(typeTokens);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TypeTokenContainer)) {
      return false;
    }
    TypeTokenContainer that = (TypeTokenContainer) o;
    return Arrays.equals(this.typeTokens, that.typeTokens);
  }

  @NonNull
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < typeTokens.length; i++) {
      if (i == 0) sb.append('<');
      if (i != typeTokens.length - 1) {
        sb.append(typeTokens[i].getTypeTokenLiteral()).append(", ");
      } else {
        sb.append(typeTokens[i].getTypeTokenLiteral()).append('>');
      }
    }
    return sb.toString();
  }
}
