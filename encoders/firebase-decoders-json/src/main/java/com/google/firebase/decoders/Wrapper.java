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

public final class Wrapper<T> {
  private T value;
  private final TypeToken<T> typeToken;

  @NonNull
  public static <T> Wrapper<T> of(@Nullable T value, @NonNull TypeToken<T> typeToken) {
    return new Wrapper<>(value, typeToken);
  }

  private Wrapper(T value, TypeToken<T> typeToken) {
    this.value = value;
    this.typeToken = typeToken;
  }

  @Nullable
  public T getValue() {
    return value;
  }

  @NonNull
  public TypeToken<T> getTypeToken() {
    return typeToken;
  }

  public void setValue(@Nullable T value) {
    this.value = value;
  }
}
