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

import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firestore.v1.Value;

/**
 * Represents a FieldValue that is backed by a single Firestore V1 Value proto and implements
 * Firestore's Value semantics for ordering and equality.
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
public class FieldValue implements Comparable<FieldValue> {
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

  final Value internalValue;

  public FieldValue(Value value) {
    this.internalValue = value;
  }

  /** Creates a new FieldValue based on the Protobuf Value. */
  // TODO(mrschmidt): Unify with UserDataReader.parseScalarValue()
  public static @Nullable FieldValue valueOf(@Nullable Value value) {
    if (value == null) {
      return null;
    }

    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return new FieldValue(value);
      case BOOLEAN_VALUE:
        return new BooleanValue(value);
      case INTEGER_VALUE:
        return new FieldValue(value);
      case DOUBLE_VALUE:
        return new FieldValue(value);
      case TIMESTAMP_VALUE:
        return new TimestampValue(value);
      case STRING_VALUE:
        return new StringValue(value);
      case BYTES_VALUE:
        return new BlobValue(value);
      case REFERENCE_VALUE:
        return new FieldValue(value);
      case GEO_POINT_VALUE:
        return new GeoPointValue(value);
      case ARRAY_VALUE:
        return new FieldValue(value);
      case MAP_VALUE:
        if (ServerTimestampValue.isServerTimestamp(value)) {
          return new ServerTimestampValue(value);
        }
        return new ObjectValue(value);
      default:
        throw fail("Invlaid value type: %s", value.getValueTypeCase());
    }
  }

  /** Returns Firestore Value Protobuf that backs this FieldValuee */
  public Value getProto() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof FieldValue) {
      return ProtoValues.equals(internalValue, ((FieldValue) o).internalValue);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(@NonNull FieldValue other) {
    return ProtoValues.compare(internalValue, other.internalValue);
  }

  @Override
  public String toString() {
    return ProtoValues.canonicalId(internalValue);
  }
}
