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

package com.google.firebase.firestore.model.value;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.NonNull;
import com.google.firebase.firestore.util.Util;
import javax.annotation.Nullable;

/**
 * A field value represents a data type as stored by Firestore.
 *
 * <p>Supported types are:
 *
 * <ul>
 *   <li>Null
 *   <li>Boolean
 *   <li>Long
 *   <li>Double
 *   <li>Timestamp
 *   <li>ServerTimestamp (a sentinel used in uncommitted writes)
 *   <li>String
 *   <li>Binary
 *   <li>(Document) References
 *   <li>GeoPoint
 *   <li>Array
 *   <li>Object
 * </ul>
 */
public abstract class FieldValue implements Comparable<FieldValue> {
  /** The order of types in Firestore; this order is defined by the backend. */
  static final int TYPE_ORDER_NULL = 0;

  static final int TYPE_ORDER_BOOLEAN = 1;
  static final int TYPE_ORDER_NUMBER = 2;
  static final int TYPE_ORDER_TIMESTAMP = 3;
  static final int TYPE_ORDER_STRING = 4;
  static final int TYPE_ORDER_BLOB = 5;
  static final int TYPE_ORDER_REFERENCE = 6;
  static final int TYPE_ORDER_GEOPOINT = 7;
  static final int TYPE_ORDER_ARRAY = 8;
  static final int TYPE_ORDER_OBJECT = 9;

  public abstract int typeOrder();

  /**
   * Converts a FieldValue into the value that users will see in document snapshots using the
   * default deserialization options.
   */
  @Nullable
  public abstract Object value();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  @Override
  public abstract int compareTo(@NonNull FieldValue o);

  @Override
  public String toString() {
    Object val = value();
    return val == null ? "null" : val.toString();
  }

  protected int defaultCompareTo(FieldValue other) {
    int cmp = Util.compareIntegers(typeOrder(), other.typeOrder());
    hardAssert(cmp != 0, "Default compareTo should not be used for values of same type.");
    return cmp;
  }
}
