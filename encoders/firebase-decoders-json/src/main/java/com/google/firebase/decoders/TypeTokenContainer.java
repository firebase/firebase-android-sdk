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
}
