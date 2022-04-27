// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util;

import java.util.NoSuchElementException;

/**
 * Implementation for Guava Optional in order to incorporate API version support on Android. This is
 * mainly used for ConfigResolver and various config sources. In order to skip the rest of config
 * value fetch when higher precedence config value exists, Optional is used as return value on
 * config sources' APIs. It also enables high readability on config resolver logic.
 */
public final class Optional<T> {

  /** If non-null, the value; if null, indicates no value is present */
  private final T value;

  /** Constructs an empty instance. */
  private Optional() {
    this.value = null;
  }

  /**
   * Constructs an instance with the value present.
   *
   * @param value the non-null value to be present
   * @throws NullPointerException if value is null
   */
  private Optional(T value) {
    if (value == null) {
      throw new NullPointerException("value for optional is empty.");
    } else {
      this.value = value;
    }
  }

  /**
   * Constructs an empty value instance of the Optional.
   *
   * @return an {@code Optional} with the value being empty
   */
  public static <T> Optional<T> absent() {
    return new Optional<T>();
  }

  /**
   * Returns an {@code Optional} with the specified present non-null value.
   *
   * @param <T> the class of the value
   * @param value the value to be present, which must be non-null
   * @return an {@code Optional} with the value present
   * @throws NullPointerException if value is null
   */
  public static <T> Optional<T> of(T value) {
    return new Optional<>(value);
  }

  /**
   * Returns an {@code Optional} describing the specified value, if non-null, otherwise returns an
   * empty {@code Optional}.
   *
   * @param <T> the class of the value
   * @param value the possibly-null value to describe
   * @return an {@code Optional} with a present value if the specified value is non-null, otherwise
   *     an empty {@code Optional}
   */
  public static <T> Optional<T> fromNullable(T value) {
    return value == null ? absent() : of(value);
  }

  /**
   * If a value is present in this {@code Optional}, returns the value, otherwise throws {@code
   * NoSuchElementException}.
   *
   * @return the non-null value held by this {@code Optional}
   * @throws NoSuchElementException if there is no value available
   * @see Optional#isAvailable()
   */
  public T get() {
    if (value == null) {
      throw new NoSuchElementException("No value present");
    }
    return value;
  }

  /**
   * Return {@code true} if there is a value available, otherwise {@code false}.
   *
   * @return {@code true} if there is a value available, otherwise {@code false}
   */
  public boolean isAvailable() {
    return value != null;
  }
}
