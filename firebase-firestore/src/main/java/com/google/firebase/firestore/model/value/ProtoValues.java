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

package com.google.firebase.firestore.model.value;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.common.base.Splitter;
import com.google.firebase.firestore.util.Util;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ProtoValues {

  /** Returns the backend's type order of the given Value type. */
  static int typeOrder(Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return FieldValue.TYPE_ORDER_NULL;
      case BOOLEAN_VALUE:
        return FieldValue.TYPE_ORDER_BOOLEAN;
      case INTEGER_VALUE:
        return FieldValue.TYPE_ORDER_NUMBER;
      case DOUBLE_VALUE:
        return FieldValue.TYPE_ORDER_NUMBER;
      case TIMESTAMP_VALUE:
        return FieldValue.TYPE_ORDER_TIMESTAMP;
      case STRING_VALUE:
        return FieldValue.TYPE_ORDER_STRING;
      case BYTES_VALUE:
        return FieldValue.TYPE_ORDER_BLOB;
      case REFERENCE_VALUE:
        return FieldValue.TYPE_ORDER_REFERENCE;
      case GEO_POINT_VALUE:
        return FieldValue.TYPE_ORDER_GEOPOINT;
      case ARRAY_VALUE:
        return FieldValue.TYPE_ORDER_ARRAY;
      case MAP_VALUE:
        if (ServerTimestampValue.isServerTimestamp(value)) {
          return FieldValue.TYPE_ORDER_TIMESTAMP;
        }
        return FieldValue.TYPE_ORDER_OBJECT;
      default:
        throw fail("Invalid value type: " + value.getValueTypeCase());
    }
  }

  static boolean equals(Value left, Value right) {
    if (left == null && right == null) {
      return true;
    } else if (left == null || right == null) {
      return false;
    }

    int leftType = typeOrder(left);
    int rightType = typeOrder(right);
    if (leftType != rightType) {
      return false;
    }

    switch (leftType) {
      case FieldValue.TYPE_ORDER_NUMBER:
        return numberEquals(left, right);
      case FieldValue.TYPE_ORDER_ARRAY:
        return arrayEquals(left, right);
      case FieldValue.TYPE_ORDER_OBJECT:
        return objectEquals(left, right);
      case FieldValue.TYPE_ORDER_TIMESTAMP:
        return timestampEquals(left, right);
      default:
        return left.equals(right);
    }
  }

  private static boolean timestampEquals(Value left, Value right) {
    if (ServerTimestampValue.isServerTimestamp(left)
        && ServerTimestampValue.isServerTimestamp(right)) {
      return new ServerTimestampValue(left)
          .getLocalWriteTime()
          .equals(new ServerTimestampValue(right).getLocalWriteTime());
    } else if (ServerTimestampValue.isServerTimestamp(left)
        || ServerTimestampValue.isServerTimestamp(right)) {
      return false;
    }

    return left.getTimestampValue().equals(right.getTimestampValue());
  }

  private static boolean numberEquals(Value left, Value right) {
    if (left.getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE
        && right.getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE) {
      return left.equals(right);
    } else if (left.getValueTypeCase() == Value.ValueTypeCase.DOUBLE_VALUE
        && right.getValueTypeCase() == Value.ValueTypeCase.DOUBLE_VALUE) {
      return Double.doubleToLongBits(left.getDoubleValue())
          == Double.doubleToLongBits(right.getDoubleValue());
    }

    return false;
  }

  private static boolean arrayEquals(Value left, Value right) {
    ArrayValue leftArray = left.getArrayValue();
    ArrayValue rightArray = right.getArrayValue();

    if (leftArray.getValuesCount() != rightArray.getValuesCount()) {
      return false;
    }

    for (int i = 0; i < leftArray.getValuesCount(); ++i) {
      if (!equals(leftArray.getValues(i), rightArray.getValues(i))) {
        return false;
      }
    }

    return true;
  }

  private static boolean objectEquals(Value left, Value right) {
    MapValue leftMap = left.getMapValue();
    MapValue rightMap = right.getMapValue();

    if (leftMap.getFieldsCount() != rightMap.getFieldsCount()) {
      return false;
    }

    for (Map.Entry<String, Value> entry : leftMap.getFieldsMap().entrySet()) {
      Value otherEntry = rightMap.getFieldsMap().get(entry.getKey());
      if (!entry.getValue().equals(otherEntry)) {
        return false;
      }
    }

    return true;
  }

  public static int compare(Value left, Value right) {
    int leftType = typeOrder(left);
    int rightType = typeOrder(right);

    if (leftType != rightType) {
      return Util.compareIntegers(leftType, rightType);
    }

    switch (leftType) {
      case FieldValue.TYPE_ORDER_NULL:
        return 0;
      case FieldValue.TYPE_ORDER_BOOLEAN:
        return Util.compareBooleans(left.getBooleanValue(), right.getBooleanValue());
      case FieldValue.TYPE_ORDER_NUMBER:
        return compareNumbers(left, right);
      case FieldValue.TYPE_ORDER_TIMESTAMP:
        return compareTimestamps(left, right);
      case FieldValue.TYPE_ORDER_STRING:
        return left.getStringValue().compareTo(right.getStringValue());
      case FieldValue.TYPE_ORDER_BLOB:
        return Util.compareByteStrings(left.getBytesValue(), right.getBytesValue());
      case FieldValue.TYPE_ORDER_REFERENCE:
        return compareReferences(left.getReferenceValue(), right.getReferenceValue());
      case FieldValue.TYPE_ORDER_GEOPOINT:
        return compareGeoPoints(left.getGeoPointValue(), right.getGeoPointValue());
      case FieldValue.TYPE_ORDER_ARRAY:
        return compareArrays(left.getArrayValue(), right.getArrayValue());
      case FieldValue.TYPE_ORDER_OBJECT:
        return compareMaps(left.getMapValue(), right.getMapValue());
      default:
        throw fail("Invalid value type: " + leftType);
    }
  }

  private static int compareNumbers(Value left, Value right) {
    if (left.getValueTypeCase() == Value.ValueTypeCase.DOUBLE_VALUE) {
      double thisDouble = left.getDoubleValue();
      if (right.getValueTypeCase() == Value.ValueTypeCase.DOUBLE_VALUE) {
        return Util.compareDoubles(thisDouble, right.getDoubleValue());
      } else if (right.getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE) {
        return Util.compareMixed(thisDouble, right.getIntegerValue());
      }
    } else if (left.getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE) {
      long thisLong = left.getIntegerValue();
      if (right.getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE) {
        return Util.compareLongs(thisLong, right.getIntegerValue());
      } else if (right.getValueTypeCase() == Value.ValueTypeCase.DOUBLE_VALUE) {
        return -1 * Util.compareMixed(right.getDoubleValue(), thisLong);
      }
    }

    throw fail("Unexpected values: %s vs %s", left, right);
  }

  private static int compareTimestamps(Value left, Value right) {
    if (ServerTimestampValue.isServerTimestamp(left)
        && ServerTimestampValue.isServerTimestamp(right)) {
      return new ServerTimestampValue(left)
          .getLocalWriteTime()
          .compareTo(new ServerTimestampValue(right).getLocalWriteTime());
    } else if (ServerTimestampValue.isServerTimestamp(left)) {
      // Server timestamps come after all concrete timestamps.
      return 1;
    } else if (ServerTimestampValue.isServerTimestamp(right)) {
      // Server timestamps come after all concrete timestamps.
      return -1;
    }

    int comparison =
        Util.compareLongs(
            left.getTimestampValue().getSeconds(), right.getTimestampValue().getSeconds());
    if (comparison != 0) {
      return comparison;
    }
    return Util.compareIntegers(
        left.getTimestampValue().getNanos(), right.getTimestampValue().getNanos());
  }

  private static int compareReferences(String leftPath, String rightPath) {
    List<String> leftSegments = Splitter.on('/').splitToList(leftPath);
    List<String> rightSegments = Splitter.on('/').splitToList(rightPath);
    int minLength = Math.min(leftSegments.size(), rightSegments.size());
    for (int i = 0; i < minLength; i++) {
      int cmp = leftSegments.get(i).compareTo(rightSegments.get(i));
      if (cmp != 0) {
        return cmp;
      }
    }
    return Util.compareIntegers(leftSegments.size(), rightSegments.size());
  }

  private static int compareGeoPoints(LatLng left, LatLng right) {
    int comparison = Util.compareDoubles(left.getLatitude(), right.getLatitude());
    if (comparison == 0) {
      return Util.compareDoubles(left.getLongitude(), right.getLongitude());
    }
    return comparison;
  }

  private static int compareArrays(ArrayValue left, ArrayValue right) {
    int minLength = Math.min(left.getValuesCount(), right.getValuesCount());
    for (int i = 0; i < minLength; i++) {
      int cmp = compare(left.getValues(i), right.getValues(i));
      if (cmp != 0) {
        return cmp;
      }
    }
    return Util.compareIntegers(left.getValuesCount(), right.getValuesCount());
  }

  private static int compareMaps(MapValue left, MapValue right) {
    Iterator<Map.Entry<String, Value>> iterator1 =
        new TreeMap<>(left.getFieldsMap()).entrySet().iterator();
    Iterator<Map.Entry<String, Value>> iterator2 =
        new TreeMap<>(right.getFieldsMap()).entrySet().iterator();
    while (iterator1.hasNext() && iterator2.hasNext()) {
      Map.Entry<String, Value> entry1 = iterator1.next();
      Map.Entry<String, Value> entry2 = iterator2.next();
      int keyCompare = entry1.getKey().compareTo(entry2.getKey());
      if (keyCompare != 0) {
        return keyCompare;
      }
      int valueCompare = compare(entry1.getValue(), entry2.getValue());
      if (valueCompare != 0) {
        return valueCompare;
      }
    }

    // Only equal if both iterators are exhausted.
    return Util.compareBooleans(iterator1.hasNext(), iterator2.hasNext());
  }

  /** Generate the canonical ID for the provided field value (as used in Target serialization). */
  public static String canonicalId(Value value) {
    StringBuilder builder = new StringBuilder();
    canonifyValue(builder, value);
    return builder.toString();
  }

  private static void canonifyValue(StringBuilder builder, Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        builder.append("null");
        break;
      case BOOLEAN_VALUE:
        builder.append(value.getBooleanValue());
        break;
      case INTEGER_VALUE:
        builder.append(value.getIntegerValue());
        break;
      case DOUBLE_VALUE:
        builder.append(value.getDoubleValue());
        break;
      case TIMESTAMP_VALUE:
        canonifyTimestamp(builder, value.getTimestampValue());
        break;
      case STRING_VALUE:
        builder.append(value.getStringValue());
        break;
      case BYTES_VALUE:
        builder.append(Util.toDebugString(value.getBytesValue()));
        break;
      case REFERENCE_VALUE:
        canonifyReference(builder, value);
        break;
      case GEO_POINT_VALUE:
        canonifyGeoPoint(builder, value.getGeoPointValue());
        break;
      case ARRAY_VALUE:
        canonifyArray(builder, value.getArrayValue());
        break;
      case MAP_VALUE:
        canonifyObject(builder, value.getMapValue());
        break;
      default:
        throw fail("Invalid value type: " + value.getValueTypeCase());
    }
  }

  private static void canonifyTimestamp(StringBuilder builder, Timestamp timestamp) {
    builder.append(String.format("time(%s,%s)", timestamp.getSeconds(), timestamp.getNanos()));
  }

  private static void canonifyGeoPoint(StringBuilder builder, LatLng latLng) {
    builder.append(String.format("geo(%s,%s)", latLng.getLatitude(), latLng.getLongitude()));
  }

  private static void canonifyReference(StringBuilder builder, Value value) {
    FieldValue fieldValue = FieldValue.valueOf(value);
    hardAssert(fieldValue instanceof ReferenceValue, "Value should be a ReferenceValue");
    builder.append(((ReferenceValue) fieldValue).getKey());
  }

  private static void canonifyObject(StringBuilder builder, MapValue mapValue) {
    // Even though MapValue are likely sorted correctly based on their insertion order (e.g. when
    // received from the backend), local modifications can bring elements out of order. We need to
    // re-sort the elements to ensure that canonical IDs are independent of insertion order.
    List<String> keys = new ArrayList<>(mapValue.getFieldsMap().keySet());
    Collections.sort(keys);

    builder.append("{");
    boolean first = true;
    for (String key : keys) {
      if (!first) {
        builder.append(",");
      } else {
        first = false;
      }
      builder.append(key).append(":");
      canonifyValue(builder, mapValue.getFieldsOrThrow(key));
    }
    builder.append("}");
  }

  private static void canonifyArray(StringBuilder builder, ArrayValue arrayValue) {
    builder.append("[");
    for (int i = 0; i < arrayValue.getValuesCount(); ++i) {
      canonifyValue(builder, arrayValue.getValues(i));
      if (i != arrayValue.getValuesCount() - 1) {
        builder.append(",");
      }
    }
    builder.append("]");
  }
}
