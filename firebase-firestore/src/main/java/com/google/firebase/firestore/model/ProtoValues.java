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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.Nullable;
import com.google.common.base.Splitter;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.util.Util;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// TODO(mrschmidt): Make package-private
public class ProtoValues {

  public static int typeOrder(Value value) {

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
        return FieldValue.TYPE_ORDER_OBJECT;
      default:
        throw fail("Invalid value type: " + value.getValueTypeCase());
    }
  }

  /** Returns whether `value` is non-null and corresponds to the given type order. */
  public static boolean isType(@Nullable Value value, int typeOrder) {
    return value != null && typeOrder(value) == typeOrder;
  }

  public static boolean equals(Value left, Value right) {
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
      default:
        return left.equals(right);
    }
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
        return compareReferences(left, right);
      case FieldValue.TYPE_ORDER_GEOPOINT:
        return compareGeoPoints(left, right);
      case FieldValue.TYPE_ORDER_ARRAY:
        return compareArrays(left, right);
      case FieldValue.TYPE_ORDER_OBJECT:
        return compareMaps(left, right);
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
    if (left.getTimestampValue().getSeconds() == right.getTimestampValue().getSeconds()) {
      return Integer.signum(
          left.getTimestampValue().getNanos() - right.getTimestampValue().getNanos());
    }
    return Long.signum(
        left.getTimestampValue().getSeconds() - right.getTimestampValue().getSeconds());
  }

  private static int compareReferences(Value left, Value right) {
    List<String> leftSegments = Splitter.on('/').splitToList(left.getReferenceValue());
    List<String> rightSegments = Splitter.on('/').splitToList(right.getReferenceValue());
    int minLength = Math.min(leftSegments.size(), rightSegments.size());
    for (int i = 0; i < minLength; i++) {
      int cmp = leftSegments.get(i).compareTo(rightSegments.get(i));
      if (cmp != 0) {
        return cmp;
      }
    }
    return Util.compareIntegers(leftSegments.size(), rightSegments.size());
  }

  private static int compareGeoPoints(Value left, Value right) {
    int comparison =
        Util.compareDoubles(
            left.getGeoPointValue().getLatitude(), right.getGeoPointValue().getLatitude());
    if (comparison == 0) {
      return Util.compareDoubles(
          left.getGeoPointValue().getLongitude(), right.getGeoPointValue().getLongitude());
    }
    return comparison;
  }

  private static int compareArrays(Value left, Value right) {
    int minLength =
        Math.min(left.getArrayValue().getValuesCount(), right.getArrayValue().getValuesCount());
    for (int i = 0; i < minLength; i++) {
      int cmp = compare(left.getArrayValue().getValues(i), right.getArrayValue().getValues(i));
      if (cmp != 0) {
        return cmp;
      }
    }
    return Util.compareIntegers(
        left.getArrayValue().getValuesCount(), right.getArrayValue().getValuesCount());
  }

  private static int compareMaps(Value left, Value right) {
    Iterator<Map.Entry<String, Value>> iterator1 =
        new TreeMap<>(left.getMapValue().getFieldsMap()).entrySet().iterator();
    Iterator<Map.Entry<String, Value>> iterator2 =
        new TreeMap<>(right.getMapValue().getFieldsMap()).entrySet().iterator();
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
}
