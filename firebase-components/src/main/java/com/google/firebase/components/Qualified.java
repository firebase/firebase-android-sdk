// Copyright 2022 Google LLC
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

package com.google.firebase.components;

import androidx.annotation.NonNull;
import java.lang.annotation.Annotation;

/** Represents a qualified class object. */
public final class Qualified<T> {
  private @interface Unqualified {}

  private final Class<? extends Annotation> qualifier;
  private final Class<T> type;

  public Qualified(Class<? extends Annotation> qualifier, Class<T> type) {
    this.qualifier = qualifier;
    this.type = type;
  }

  @NonNull
  public static <T> Qualified<T> unqualified(Class<T> type) {
    return new Qualified<>(Unqualified.class, type);
  }

  @NonNull
  public static <T> Qualified<T> qualified(Class<? extends Annotation> qualifier, Class<T> type) {
    return new Qualified<>(qualifier, type);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Qualified<?> qualified = (Qualified<?>) o;

    if (!type.equals(qualified.type)) return false;
    return qualifier.equals(qualified.qualifier);
  }

  @Override
  public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + qualifier.hashCode();
    return result;
  }

  @Override
  public String toString() {
    if (qualifier == Unqualified.class) {
      return type.getName();
    }
    return "@" + qualifier.getName() + " " + type.getName();
  }
}
