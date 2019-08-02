// Copyright 2018 Google LLC
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

package com.google.firebase.firestore;

import androidx.annotation.NonNull;
import java.util.Arrays;
import java.util.List;

/**
 * Sentinel values that can be used when writing document fields with {@code set()} or {@code
 * update()}.
 */
public abstract class FieldValue {

  FieldValue() {}

  /**
   * Returns the method name (for example, "FieldValue.delete") that was used to create this {@code
   * FieldValue} instance, for use in error messages, etc.
   */
  abstract String getMethodName();

  /* {@code FieldValue} class for field deletes. */
  static class DeleteFieldValue extends FieldValue {
    @Override
    String getMethodName() {
      return "FieldValue.delete";
    }
  }

  /* {@code FieldValue} class for server timestamps. */
  static class ServerTimestampFieldValue extends FieldValue {
    @Override
    String getMethodName() {
      return "FieldValue.serverTimestamp";
    }
  }

  /* {@code FieldValue} class for {@link #arrayUnion()} transforms. */
  static class ArrayUnionFieldValue extends FieldValue {
    private final List<Object> elements;

    ArrayUnionFieldValue(List<Object> elements) {
      this.elements = elements;
    }

    @Override
    String getMethodName() {
      return "FieldValue.arrayUnion";
    }

    List<Object> getElements() {
      return elements;
    }
  }

  /* {@code FieldValue} class for {@link #arrayRemove()} transforms. */
  static class ArrayRemoveFieldValue extends FieldValue {
    private final List<Object> elements;

    ArrayRemoveFieldValue(List<Object> elements) {
      this.elements = elements;
    }

    @Override
    String getMethodName() {
      return "FieldValue.arrayRemove";
    }

    List<Object> getElements() {
      return elements;
    }
  }

  /* {@code FieldValue} class for {@link #increment()} transforms. */
  static class NumericIncrementFieldValue extends FieldValue {
    private final Number operand;

    NumericIncrementFieldValue(Number operand) {
      this.operand = operand;
    }

    @Override
    String getMethodName() {
      return "FieldValue.increment";
    }

    Number getOperand() {
      return operand;
    }
  }

  private static final DeleteFieldValue DELETE_INSTANCE = new DeleteFieldValue();
  private static final ServerTimestampFieldValue SERVER_TIMESTAMP_INSTANCE =
      new ServerTimestampFieldValue();

  /** Returns a sentinel for use with {@code update()} to mark a field for deletion. */
  @NonNull
  public static FieldValue delete() {
    return DELETE_INSTANCE;
  }

  /**
   * Returns a sentinel for use with {@code set()} or {@code update()} to include a server-generated
   * timestamp in the written data.
   */
  @NonNull
  public static FieldValue serverTimestamp() {
    return SERVER_TIMESTAMP_INSTANCE;
  }

  /**
   * Returns a special value that can be used with {@code set()} or {@code update()} that tells the
   * server to union the given elements with any array value that already exists on the server. Each
   * specified element that doesn't already exist in the array will be added to the end. If the
   * field being modified is not already an array it will be overwritten with an array containing
   * exactly the specified elements.
   *
   * @param elements The elements to union into the array.
   * @return The {@code FieldValue} sentinel for use in a call to {@code set()} or {@code update()}.
   */
  @NonNull
  public static FieldValue arrayUnion(Object... elements) {
    return new ArrayUnionFieldValue(Arrays.asList(elements));
  }

  /**
   * Returns a special value that can be used with {@code set()} or {@code update()} that tells the
   * server to remove the given elements from any array value that already exists on the server. All
   * instances of each element specified will be removed from the array. If the field being modified
   * is not already an array it will be overwritten with an empty array.
   *
   * @param elements The elements to remove from the array.
   * @return The {@code FieldValue} sentinel for use in a call to {@code set()} or {@code update()}.
   */
  @NonNull
  public static FieldValue arrayRemove(Object... elements) {
    return new ArrayRemoveFieldValue(Arrays.asList(elements));
  }

  /**
   * Returns a special value that can be used with {@code set()} or {@code update()} that tells the
   * server to increment the field's current value by the given value.
   *
   * <p>If the current field value is an integer, possible integer overflows are resolved to
   * Long.MAX_VALUE or Long.MIN_VALUE. If the current field value is a double, both values will be
   * interpreted as doubles and the arithmetic will follow IEEE 754 semantics.
   *
   * <p>If the current field is not an integer or double, or if the field does not yet exist, the
   * transformation will set the field to the given value.
   *
   * @return The {@code FieldValue} sentinel for use in a call to {@code set()} or {@code update()}.
   */
  @NonNull
  public static FieldValue increment(long l) {
    return new NumericIncrementFieldValue(l);
  }

  /**
   * Returns a special value that can be used with {@code set()} or {@code update()} that tells the
   * server to increment the field's current value by the given value.
   *
   * <p>If the current value is an integer or a double, both the current and the given value will be
   * interpreted as doubles and all arithmetic will follow IEEE 754 semantics. Otherwise, the
   * transformation will set the field to the given value.
   *
   * @return The {@code FieldValue} sentinel for use in a call to {@code set()} or {@code update()}.
   */
  @NonNull
  public static FieldValue increment(double l) {
    return new NumericIncrementFieldValue(l);
  }
}
