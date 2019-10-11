// Copyright 2019 Google LLC
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

package com.google.android.datatransport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents encodings.
 *
 * <p>Overrides {@link #equals(Object)} and {@link #hashCode()} to enable value semantics.
 */
public final class Encoding {
  private final String name;

  /** Creates {@link Encoding} values for given names. */
  public static Encoding of(@NonNull String name) {
    return new Encoding(name);
  }

  public String getName() {
    return name;
  }

  private Encoding(@NonNull String name) {
    if (name == null) {
      throw new NullPointerException("name is null");
    }
    this.name = name;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof Encoding)) return false;

    return this.name.equals(((Encoding) o).name);
  }

  @Override
  public int hashCode() {
    int h = 1000003;
    h ^= name.hashCode();
    return h;
  }

  @NonNull
  @Override
  public String toString() {
    return "Encoding{name=\"" + name + "\"}";
  }
}
