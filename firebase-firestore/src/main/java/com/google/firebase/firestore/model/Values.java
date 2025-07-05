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

import static com.google.firebase.firestore.model.ServerTimestamps.getLocalWriteTime;
import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.Quadruple;
import com.google.firebase.firestore.util.Util;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.ArrayValueOrBuilder;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Values {
  public static final String TYPE_KEY = "__type__";

  public static final String RESERVED_VECTOR_KEY = "__vector__";
  // For MinKey type
  public static final String RESERVED_MIN_KEY = "__min__";

  // For MaxKey type
  public static final String RESERVED_MAX_KEY = "__max__";

  // For Regex type
  public static final String RESERVED_REGEX_KEY = "__regex__";
  public static final String RESERVED_REGEX_PATTERN_KEY = "pattern";
  public static final String RESERVED_REGEX_OPTIONS_KEY = "options";

  // For ObjectId type
  public static final String RESERVED_OBJECT_ID_KEY = "__oid__";

  // For Int32 type
  public static final String RESERVED_INT32_KEY = "__int__";

  // For Decimal128 type.
  public static final String RESERVED_DECIMAL128_KEY = "__decimal128__";

  // For RequestTimestamp
  public static final String RESERVED_BSON_TIMESTAMP_KEY = "__request_timestamp__";

  public static final String RESERVED_BSON_TIMESTAMP_SECONDS_KEY = "seconds";
  public static final String RESERVED_BSON_TIMESTAMP_INCREMENT_KEY = "increment";

  // For BSON Binary Data
  public static final String RESERVED_BSON_BINARY_KEY = "__binary__";

  public static final String RESERVED_SERVER_TIMESTAMP_KEY = "server_timestamp";

  public static final Value NAN_VALUE = Value.newBuilder().setDoubleValue(Double.NaN).build();
  public static final Value NULL_VALUE =
      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
  public static final Value INTERNAL_MIN_VALUE = NULL_VALUE;
  public static final Value MAX_VALUE_TYPE =
      Value.newBuilder().setStringValue(RESERVED_MAX_KEY).build();
  public static final Value INTERNAL_MAX_VALUE =
      Value.newBuilder()
          .setMapValue(MapValue.newBuilder().putFields(TYPE_KEY, MAX_VALUE_TYPE))
          .build();

  public static final Value VECTOR_VALUE_TYPE =
      Value.newBuilder().setStringValue(RESERVED_VECTOR_KEY).build();
  public static final String VECTOR_MAP_VECTORS_KEY = "value";

  private static final Value MIN_VECTOR_VALUE =
      Value.newBuilder()
          .setMapValue(
              MapValue.newBuilder()
                  .putFields(TYPE_KEY, VECTOR_VALUE_TYPE)
                  .putFields(
                      VECTOR_MAP_VECTORS_KEY,
                      Value.newBuilder().setArrayValue(ArrayValue.newBuilder()).build()))
          .build();

  /**
   * The order of types in Firestore. This order is based on the backend's ordering, but modified to
   * support server timestamps and {@link #INTERNAL_MAX_VALUE}.
   */
  public static final int TYPE_ORDER_NULL = 0;

  public static final int TYPE_ORDER_MIN_KEY = 1;
  public static final int TYPE_ORDER_BOOLEAN = 2;
  public static final int TYPE_ORDER_NUMBER = 3;
  public static final int TYPE_ORDER_TIMESTAMP = 4;
  public static final int TYPE_ORDER_BSON_TIMESTAMP = 5;
  public static final int TYPE_ORDER_SERVER_TIMESTAMP = 6;
  public static final int TYPE_ORDER_STRING = 7;
  public static final int TYPE_ORDER_BLOB = 8;
  public static final int TYPE_ORDER_BSON_BINARY = 9;
  public static final int TYPE_ORDER_REFERENCE = 10;
  public static final int TYPE_ORDER_BSON_OBJECT_ID = 11;
  public static final int TYPE_ORDER_GEOPOINT = 12;
  public static final int TYPE_ORDER_REGEX = 13;
  public static final int TYPE_ORDER_ARRAY = 14;
  public static final int TYPE_ORDER_VECTOR = 15;
  public static final int TYPE_ORDER_MAP = 16;
  public static final int TYPE_ORDER_MAX_KEY = 17;

  public static final int TYPE_ORDER_MAX_VALUE = Integer.MAX_VALUE;

  /** Returns the backend's type order of the given Value type. */
  public static int typeOrder(Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return TYPE_ORDER_NULL;
      case BOOLEAN_VALUE:
        return TYPE_ORDER_BOOLEAN;
      case INTEGER_VALUE:
      case DOUBLE_VALUE:
        return TYPE_ORDER_NUMBER;
      case TIMESTAMP_VALUE:
        return TYPE_ORDER_TIMESTAMP;
      case STRING_VALUE:
        return TYPE_ORDER_STRING;
      case BYTES_VALUE:
        return TYPE_ORDER_BLOB;
      case REFERENCE_VALUE:
        return TYPE_ORDER_REFERENCE;
      case GEO_POINT_VALUE:
        return TYPE_ORDER_GEOPOINT;
      case ARRAY_VALUE:
        return TYPE_ORDER_ARRAY;
      case MAP_VALUE:
        MapRepresentation mapType = detectMapRepresentation(value);
        switch (mapType) {
          case SERVER_TIMESTAMP:
            return TYPE_ORDER_SERVER_TIMESTAMP;
          case INTERNAL_MAX:
            return TYPE_ORDER_MAX_VALUE;
          case VECTOR:
            return TYPE_ORDER_VECTOR;
          case MIN_KEY:
            return TYPE_ORDER_MIN_KEY;
          case MAX_KEY:
            return TYPE_ORDER_MAX_KEY;
          case REGEX:
            return TYPE_ORDER_REGEX;
          case BSON_TIMESTAMP:
            return TYPE_ORDER_BSON_TIMESTAMP;
          case BSON_OBJECT_ID:
            return TYPE_ORDER_BSON_OBJECT_ID;
          case BSON_BINARY:
            return TYPE_ORDER_BSON_BINARY;
          case INT32:
          case DECIMAL128:
            return TYPE_ORDER_NUMBER;
          default:
            return TYPE_ORDER_MAP;
        }
      default:
        throw fail("Invalid value type: " + value.getValueTypeCase());
    }
  }

  public static boolean equals(Value left, Value right) {
    if (left == right) {
      return true;
    }

    if (left == null || right == null) {
      return false;
    }

    int leftType = typeOrder(left);
    int rightType = typeOrder(right);
    if (leftType != rightType) {
      return false;
    }

    switch (leftType) {
      case TYPE_ORDER_NUMBER:
        return numberEquals(left, right);
      case TYPE_ORDER_ARRAY:
        return arrayEquals(left, right);
      case TYPE_ORDER_VECTOR:
      case TYPE_ORDER_MAP:
        return objectEquals(left, right);
      case TYPE_ORDER_SERVER_TIMESTAMP:
        return getLocalWriteTime(left).equals(getLocalWriteTime(right));
      case TYPE_ORDER_MAX_VALUE:
      case TYPE_ORDER_NULL:
      case TYPE_ORDER_MAX_KEY:
      case TYPE_ORDER_MIN_KEY:
        return true;
      default:
        return left.equals(right);
    }
  }

  private static boolean numberEquals(Value left, Value right) {
    if ((isInt64Value(left) && isInt64Value(right))
        || (isInt32Value(left) && isInt32Value(right))) {
      return getIntegerValue(left) == getIntegerValue(right);
    } else if (isDouble(left) && isDouble(right)) {
      return Double.doubleToLongBits(left.getDoubleValue())
          == Double.doubleToLongBits(right.getDoubleValue());
    } else if (isDecimal128Value(left) && isDecimal128Value(right)) {
      Quadruple leftQuadruple = Quadruple.fromString(getDecimal128StringValue(left));
      Quadruple rightQuadruple = Quadruple.fromString(getDecimal128StringValue(right));
      return Util.compareQuadruples(leftQuadruple, rightQuadruple) == 0;
    }

    return false;
  }

  /**
   * Returns a long from a 32-bit or 64-bit proto integer value. Throws an exception if the value is
   * not an integer.
   */
  private static long getIntegerValue(Value value) {
    if (value.hasIntegerValue()) {
      return value.getIntegerValue();
    }
    if (isInt32Value(value)) {
      return value.getMapValue().getFieldsMap().get(RESERVED_INT32_KEY).getIntegerValue();
    }
    throw new IllegalArgumentException("getIntegerValue was called with a non-integer argument");
  }

  private static String getDecimal128StringValue(Value value) {
    if (isDecimal128Value(value)) {
      return value.getMapValue().getFieldsMap().get(RESERVED_DECIMAL128_KEY).getStringValue();
    }
    throw new IllegalArgumentException(
        "getDecimal128Value was called with a non-decimal128 argument");
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
      if (!equals(entry.getValue(), otherEntry)) {
        return false;
      }
    }

    return true;
  }

  /** Returns true if the Value list contains the specified element. */
  public static boolean contains(ArrayValueOrBuilder haystack, Value needle) {
    for (Value haystackElement : haystack.getValuesList()) {
      if (equals(haystackElement, needle)) {
        return true;
      }
    }
    return false;
  }

  public static int compare(Value left, Value right) {
    int leftType = typeOrder(left);
    int rightType = typeOrder(right);

    if (leftType != rightType) {
      return Util.compareIntegers(leftType, rightType);
    }

    switch (leftType) {
      case TYPE_ORDER_NULL:
      case TYPE_ORDER_MAX_VALUE:
      case TYPE_ORDER_MAX_KEY:
      case TYPE_ORDER_MIN_KEY:
        return 0;
      case TYPE_ORDER_BOOLEAN:
        return Util.compareBooleans(left.getBooleanValue(), right.getBooleanValue());
      case TYPE_ORDER_NUMBER:
        return compareNumbers(left, right);
      case TYPE_ORDER_TIMESTAMP:
        return compareTimestamps(left.getTimestampValue(), right.getTimestampValue());
      case TYPE_ORDER_SERVER_TIMESTAMP:
        return compareTimestamps(getLocalWriteTime(left), getLocalWriteTime(right));
      case TYPE_ORDER_STRING:
        return Util.compareUtf8Strings(left.getStringValue(), right.getStringValue());
      case TYPE_ORDER_BLOB:
        return Util.compareByteStrings(left.getBytesValue(), right.getBytesValue());
      case TYPE_ORDER_REFERENCE:
        return compareReferences(left.getReferenceValue(), right.getReferenceValue());
      case TYPE_ORDER_GEOPOINT:
        return compareGeoPoints(left.getGeoPointValue(), right.getGeoPointValue());
      case TYPE_ORDER_ARRAY:
        return compareArrays(left.getArrayValue(), right.getArrayValue());
      case TYPE_ORDER_MAP:
        return compareMaps(left.getMapValue(), right.getMapValue());
      case TYPE_ORDER_VECTOR:
        return compareVectors(left.getMapValue(), right.getMapValue());
      case TYPE_ORDER_REGEX:
        return compareRegex(left.getMapValue(), right.getMapValue());
      case TYPE_ORDER_BSON_OBJECT_ID:
        return compareBsonObjectId(left.getMapValue(), right.getMapValue());
      case TYPE_ORDER_BSON_TIMESTAMP:
        return compareBsonTimestamp(left.getMapValue(), right.getMapValue());
      case TYPE_ORDER_BSON_BINARY:
        return compareBsonBinary(left.getMapValue(), right.getMapValue());
      default:
        throw fail("Invalid value type: " + leftType);
    }
  }

  public static int lowerBoundCompare(
      Value left, boolean leftInclusive, Value right, boolean rightInclusive) {
    int cmp = compare(left, right);
    if (cmp != 0) {
      return cmp;
    }

    if (leftInclusive && !rightInclusive) {
      return -1;
    } else if (!leftInclusive && rightInclusive) {
      return 1;
    }

    return 0;
  }

  public static int upperBoundCompare(
      Value left, boolean leftInclusive, Value right, boolean rightInclusive) {
    int cmp = compare(left, right);
    if (cmp != 0) {
      return cmp;
    }

    if (leftInclusive && !rightInclusive) {
      return 1;
    } else if (!leftInclusive && rightInclusive) {
      return -1;
    }

    return 0;
  }

  private static int compareNumbers(Value left, Value right) {
    // If either argument is Decimal128, we cast both to wider (128-bit) representation, and compare
    // Quadruple values.
    if (isDecimal128Value(left) || isDecimal128Value(right)) {
      Quadruple leftQuadruple = convertNumberToQuadruple(left);
      Quadruple rightQuadruple = convertNumberToQuadruple(right);
      return Util.compareQuadruples(leftQuadruple, rightQuadruple);
    }

    if (isDouble(left)) {
      double leftDouble = left.getDoubleValue();
      if (isDouble(right)) {
        // left and right are both doubles.
        return Util.compareDoubles(leftDouble, right.getDoubleValue());
      } else if (isIntegerValue(right)) {
        // left is a double and right is a 32/64-bit integer value.
        return Util.compareMixed(leftDouble, getIntegerValue(right));
      }
    }

    if (isIntegerValue(left)) {
      long leftLong = getIntegerValue(left);
      if (isIntegerValue(right)) {
        // left and right both a 32/64-bit integer value.
        return Util.compareLongs(leftLong, getIntegerValue(right));
      } else if (isDouble(right)) {
        // left is a 32/64-bit integer and right is a double .
        return -1 * Util.compareMixed(right.getDoubleValue(), leftLong);
      }
    }

    throw fail("Unexpected values: %s vs %s", left, right);
  }

  /**
   * Converts the given number value to a Quadruple. Throws an exception if the value is not a
   * number.
   */
  private static Quadruple convertNumberToQuadruple(Value value) {
    // Doubles
    if (isDouble(value)) {
      return Quadruple.fromDouble(value.getDoubleValue());
    }

    // 64-bit or 32-bit integers.
    if (isInt64Value(value) || isInt32Value(value)) {
      return Quadruple.fromLong(getIntegerValue(value));
    }

    // Decimal128 numbers
    if (isDecimal128Value(value)) {
      return Quadruple.fromString(getDecimal128StringValue(value));
    }

    throw new IllegalArgumentException(
        "convertNumberToQuadruple was called on a non-numeric value.");
  }

  private static int compareTimestamps(Timestamp left, Timestamp right) {
    int cmp = Util.compareLongs(left.getSeconds(), right.getSeconds());
    if (cmp != 0) {
      return cmp;
    }
    return Util.compareIntegers(left.getNanos(), right.getNanos());
  }

  private static int compareReferences(String leftPath, String rightPath) {
    String[] leftSegments = leftPath.split("/", -1);
    String[] rightSegments = rightPath.split("/", -1);

    int minLength = Math.min(leftSegments.length, rightSegments.length);
    for (int i = 0; i < minLength; i++) {
      int cmp = leftSegments[i].compareTo(rightSegments[i]);
      if (cmp != 0) {
        return cmp;
      }
    }
    return Util.compareIntegers(leftSegments.length, rightSegments.length);
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
      int keyCompare = Util.compareUtf8Strings(entry1.getKey(), entry2.getKey());
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

  private static int compareRegex(MapValue left, MapValue right) {
    Map<String, Value> leftMap =
        left.getFieldsMap().get(RESERVED_REGEX_KEY).getMapValue().getFieldsMap();
    Map<String, Value> rightMap =
        right.getFieldsMap().get(RESERVED_REGEX_KEY).getMapValue().getFieldsMap();

    String leftPattern = leftMap.get(RESERVED_REGEX_PATTERN_KEY).getStringValue();
    String rightPattern = rightMap.get(RESERVED_REGEX_PATTERN_KEY).getStringValue();

    int comp = Util.compareUtf8Strings(leftPattern, rightPattern);
    if (comp != 0) return comp;

    String leftOption = leftMap.get(RESERVED_REGEX_OPTIONS_KEY).getStringValue();
    String rightOption = rightMap.get(RESERVED_REGEX_OPTIONS_KEY).getStringValue();

    return leftOption.compareTo(rightOption);
  }

  private static int compareBsonObjectId(MapValue left, MapValue right) {
    String lhs = left.getFieldsMap().get(RESERVED_OBJECT_ID_KEY).getStringValue();
    String rhs = right.getFieldsMap().get(RESERVED_OBJECT_ID_KEY).getStringValue();
    return Util.compareUtf8Strings(lhs, rhs);
  }

  private static int compareBsonTimestamp(MapValue left, MapValue right) {
    Map<String, Value> leftMap =
        left.getFieldsMap().get(RESERVED_BSON_TIMESTAMP_KEY).getMapValue().getFieldsMap();
    Map<String, Value> rightMap =
        right.getFieldsMap().get(RESERVED_BSON_TIMESTAMP_KEY).getMapValue().getFieldsMap();

    long leftSeconds = leftMap.get(RESERVED_BSON_TIMESTAMP_SECONDS_KEY).getIntegerValue();
    long rightSeconds = rightMap.get(RESERVED_BSON_TIMESTAMP_SECONDS_KEY).getIntegerValue();

    int comp = Util.compareLongs(leftSeconds, rightSeconds);
    if (comp != 0) return comp;

    long leftIncrement = leftMap.get(RESERVED_BSON_TIMESTAMP_INCREMENT_KEY).getIntegerValue();
    long rightIncrement = rightMap.get(RESERVED_BSON_TIMESTAMP_INCREMENT_KEY).getIntegerValue();

    return Util.compareLongs(leftIncrement, rightIncrement);
  }

  private static int compareBsonBinary(MapValue left, MapValue right) {
    ByteString lhs = left.getFieldsMap().get(RESERVED_BSON_BINARY_KEY).getBytesValue();
    ByteString rhs = right.getFieldsMap().get(RESERVED_BSON_BINARY_KEY).getBytesValue();
    return Util.compareByteStrings(lhs, rhs);
  }

  private static int compareVectors(MapValue left, MapValue right) {
    Map<String, Value> leftMap = left.getFieldsMap();
    Map<String, Value> rightMap = right.getFieldsMap();

    // The vector is a map, but only vector value is compared.
    ArrayValue leftArrayValue = leftMap.get(Values.VECTOR_MAP_VECTORS_KEY).getArrayValue();
    ArrayValue rightArrayValue = rightMap.get(Values.VECTOR_MAP_VECTORS_KEY).getArrayValue();

    int lengthCompare =
        Util.compareIntegers(leftArrayValue.getValuesCount(), rightArrayValue.getValuesCount());
    if (lengthCompare != 0) {
      return lengthCompare;
    }

    return compareArrays(leftArrayValue, rightArrayValue);
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
        builder.append(getIntegerValue(value));
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
    hardAssert(isReferenceValue(value), "Value should be a ReferenceValue");
    builder.append(DocumentKey.fromName(value.getReferenceValue()));
  }

  private static void canonifyObject(StringBuilder builder, MapValue mapValue) {
    // Even though MapValue are likely sorted correctly based on their insertion order (for example,
    // when received from the backend), local modifications can bring elements out of order. We need
    // to re-sort the elements to ensure that canonical IDs are independent of insertion order.
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

  /** Returns true if `value` is a INTEGER_VALUE. */
  public static boolean isInt64Value(@Nullable Value value) {
    return value != null && value.getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE;
  }

  /** Returns true if `value` is a DOUBLE_VALUE. */
  public static boolean isDouble(@Nullable Value value) {
    return value != null && value.getValueTypeCase() == Value.ValueTypeCase.DOUBLE_VALUE;
  }

  /** Returns true if `value` is either a INTEGER_VALUE or a DOUBLE_VALUE. */
  public static boolean isNumber(@Nullable Value value) {
    return isInt64Value(value) || isDouble(value);
  }

  /** Returns true if `value` is a INTEGER_VALUE or a Int32 Value. */
  public static boolean isIntegerValue(@Nullable Value value) {
    return isInt64Value(value) || isInt32Value(value);
  }

  /** Returns true if `value` is an ARRAY_VALUE. */
  public static boolean isArray(@Nullable Value value) {
    return value != null && value.getValueTypeCase() == Value.ValueTypeCase.ARRAY_VALUE;
  }

  public static boolean isReferenceValue(@Nullable Value value) {
    return value != null && value.getValueTypeCase() == Value.ValueTypeCase.REFERENCE_VALUE;
  }

  public static boolean isNullValue(@Nullable Value value) {
    return value != null && value.getValueTypeCase() == Value.ValueTypeCase.NULL_VALUE;
  }

  public static boolean isNanValue(@Nullable Value value) {
    if (value != null && Double.isNaN(value.getDoubleValue())) {
      return true;
    }

    if (isDecimal128Value(value)) {
      return value
          .getMapValue()
          .getFieldsMap()
          .get(RESERVED_DECIMAL128_KEY)
          .getStringValue()
          .equals("NaN");
    }

    return false;
  }

  public static boolean isMapValue(@Nullable Value value) {
    return value != null && value.getValueTypeCase() == Value.ValueTypeCase.MAP_VALUE;
  }

  public static Value refValue(DatabaseId databaseId, DocumentKey key) {
    Value value =
        Value.newBuilder()
            .setReferenceValue(
                String.format(
                    "projects/%s/databases/%s/documents/%s",
                    databaseId.getProjectId(), databaseId.getDatabaseId(), key.toString()))
            .build();
    return value;
  }

  public static Value MIN_BOOLEAN = Value.newBuilder().setBooleanValue(false).build();
  public static Value MIN_NUMBER = Value.newBuilder().setDoubleValue(Double.NaN).build();
  public static Value MIN_TIMESTAMP =
      Value.newBuilder()
          .setTimestampValue(Timestamp.newBuilder().setSeconds(Long.MIN_VALUE))
          .build();
  public static Value MIN_STRING = Value.newBuilder().setStringValue("").build();
  public static Value MIN_BYTES = Value.newBuilder().setBytesValue(ByteString.EMPTY).build();
  public static Value MIN_REFERENCE = refValue(DatabaseId.EMPTY, DocumentKey.empty());
  public static Value MIN_GEO_POINT =
      Value.newBuilder()
          .setGeoPointValue(LatLng.newBuilder().setLatitude(-90.0).setLongitude(-180.0))
          .build();
  public static Value MIN_ARRAY =
      Value.newBuilder().setArrayValue(ArrayValue.getDefaultInstance()).build();
  public static Value MIN_MAP =
      Value.newBuilder().setMapValue(MapValue.getDefaultInstance()).build();

  public static Value MIN_KEY_VALUE =
      Value.newBuilder()
          .setMapValue(
              MapValue.newBuilder()
                  .putFields(
                      RESERVED_MIN_KEY,
                      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()))
          .build();

  public static Value MAX_KEY_VALUE =
      Value.newBuilder()
          .setMapValue(
              MapValue.newBuilder()
                  .putFields(
                      RESERVED_MAX_KEY,
                      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()))
          .build();

  public static Value MIN_BSON_OBJECT_ID_VALUE =
      Value.newBuilder()
          .setMapValue(
              MapValue.newBuilder()
                  .putFields(RESERVED_OBJECT_ID_KEY, Value.newBuilder().setStringValue("").build()))
          .build();

  public static Value MIN_BSON_TIMESTAMP_VALUE =
      Value.newBuilder()
          .setMapValue(
              MapValue.newBuilder()
                  .putFields(
                      RESERVED_BSON_TIMESTAMP_KEY,
                      Value.newBuilder()
                          .setMapValue(
                              MapValue.newBuilder()
                                  // Both seconds and increment are 32 bit unsigned integers
                                  .putFields(
                                      RESERVED_BSON_TIMESTAMP_SECONDS_KEY,
                                      Value.newBuilder().setIntegerValue(0).build())
                                  .putFields(
                                      RESERVED_BSON_TIMESTAMP_INCREMENT_KEY,
                                      Value.newBuilder().setIntegerValue(0).build()))
                          .build()))
          .build();

  public static Value MIN_BSON_BINARY_VALUE =
      Value.newBuilder()
          .setMapValue(
              MapValue.newBuilder()
                  .putFields(
                      RESERVED_BSON_BINARY_KEY,
                      // bsonBinaryValue should have at least one byte as subtype
                      Value.newBuilder()
                          .setBytesValue(ByteString.copyFrom(new byte[] {0}))
                          .build()))
          .build();

  public static Value MIN_REGEX_VALUE =
      Value.newBuilder()
          .setMapValue(
              MapValue.newBuilder()
                  .putFields(
                      RESERVED_REGEX_KEY,
                      Value.newBuilder()
                          .setMapValue(
                              MapValue.newBuilder()
                                  .putFields(
                                      RESERVED_REGEX_PATTERN_KEY,
                                      Value.newBuilder().setStringValue("").build())
                                  .putFields(
                                      RESERVED_REGEX_OPTIONS_KEY,
                                      Value.newBuilder().setStringValue("").build()))
                          .build()))
          .build();

  /** Returns the lowest value for the given value type (inclusive). */
  public static Value getLowerBound(Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return Values.NULL_VALUE;
      case BOOLEAN_VALUE:
        return MIN_BOOLEAN;
      case INTEGER_VALUE:
      case DOUBLE_VALUE:
        return MIN_NUMBER;
      case TIMESTAMP_VALUE:
        return MIN_TIMESTAMP;
      case STRING_VALUE:
        return MIN_STRING;
      case BYTES_VALUE:
        return MIN_BYTES;
      case REFERENCE_VALUE:
        return MIN_REFERENCE;
      case GEO_POINT_VALUE:
        return MIN_GEO_POINT;
      case ARRAY_VALUE:
        return MIN_ARRAY;
      case MAP_VALUE:
        MapRepresentation mapType = detectMapRepresentation(value);
        // VectorValue sorts after ArrayValue and before an empty MapValue
        switch (mapType) {
          case VECTOR:
            return MIN_VECTOR_VALUE;
          case BSON_OBJECT_ID:
            return MIN_BSON_OBJECT_ID_VALUE;
          case BSON_TIMESTAMP:
            return MIN_BSON_TIMESTAMP_VALUE;
          case BSON_BINARY:
            return MIN_BSON_BINARY_VALUE;
          case REGEX:
            return MIN_REGEX_VALUE;
          case INT32:
          case DECIMAL128:
            // Int32Value and Decimal128Value are treated the same as integerValue and doubleValue
            return MIN_NUMBER;
          case MIN_KEY:
            return MIN_KEY_VALUE;
          case MAX_KEY:
            return MAX_KEY_VALUE;
          default:
            return MIN_MAP;
        }
      default:
        throw new IllegalArgumentException("Unknown value type: " + value.getValueTypeCase());
    }
  }

  /** Returns the largest value for the given value type (exclusive). */
  public static Value getUpperBound(Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return MIN_KEY_VALUE;
      case BOOLEAN_VALUE:
        return MIN_NUMBER;
      case INTEGER_VALUE:
      case DOUBLE_VALUE:
        return MIN_TIMESTAMP;
      case TIMESTAMP_VALUE:
        return MIN_BSON_TIMESTAMP_VALUE;
      case STRING_VALUE:
        return MIN_BYTES;
      case BYTES_VALUE:
        return MIN_BSON_BINARY_VALUE;
      case REFERENCE_VALUE:
        return MIN_BSON_OBJECT_ID_VALUE;
      case GEO_POINT_VALUE:
        return MIN_REGEX_VALUE;
      case ARRAY_VALUE:
        return MIN_VECTOR_VALUE;
      case MAP_VALUE:
        MapRepresentation mapType = detectMapRepresentation(value);
        switch (mapType) {
          case VECTOR:
            return MIN_MAP;
          case BSON_OBJECT_ID:
            return MIN_GEO_POINT;
          case BSON_TIMESTAMP:
            return MIN_STRING;
          case BSON_BINARY:
            return MIN_REFERENCE;
          case REGEX:
            return MIN_ARRAY;
          case INT32:
          case DECIMAL128:
            // Int32Value and decimal128Value are treated the same as integerValue and doubleValue
            return MIN_TIMESTAMP;
          case MIN_KEY:
            return MIN_BOOLEAN;
          case MAX_KEY:
            return INTERNAL_MAX_VALUE;
          default:
            return MAX_KEY_VALUE;
        }
      default:
        throw new IllegalArgumentException("Unknown value type: " + value.getValueTypeCase());
    }
  }

  private static boolean isMapWithSingleFieldOfType(
      Value value, String key, Value.ValueTypeCase typeCase) {
    if (value == null
        || value.getMapValue() == null
        || value.getMapValue().getFieldsMap() == null) {
      return false;
    }

    Map<String, Value> fields = value.getMapValue().getFieldsMap();
    return fields.size() == 1
        && fields.containsKey(key)
        && fields.get(key).getValueTypeCase() == typeCase;
  }

  static boolean isMinKey(Value value) {
    return isMapWithSingleFieldOfType(value, RESERVED_MIN_KEY, Value.ValueTypeCase.NULL_VALUE);
  }

  static boolean isMaxKey(Value value) {
    return isMapWithSingleFieldOfType(value, RESERVED_MAX_KEY, Value.ValueTypeCase.NULL_VALUE);
  }

  public static boolean isInt32Value(Value value) {
    return isMapWithSingleFieldOfType(value, RESERVED_INT32_KEY, Value.ValueTypeCase.INTEGER_VALUE);
  }

  public static boolean isDecimal128Value(Value value) {
    return isMapWithSingleFieldOfType(
        value, RESERVED_DECIMAL128_KEY, Value.ValueTypeCase.STRING_VALUE);
  }

  static boolean isBsonObjectId(Value value) {
    return isMapWithSingleFieldOfType(
        value, RESERVED_OBJECT_ID_KEY, Value.ValueTypeCase.STRING_VALUE);
  }

  static boolean isBsonBinaryData(Value value) {
    return isMapWithSingleFieldOfType(
        value, RESERVED_BSON_BINARY_KEY, Value.ValueTypeCase.BYTES_VALUE);
  }

  static boolean isRegexValue(Value value) {
    if (!isMapWithSingleFieldOfType(value, RESERVED_REGEX_KEY, Value.ValueTypeCase.MAP_VALUE)) {
      return false;
    }

    MapValue innerMapValue =
        value.getMapValue().getFieldsMap().get(RESERVED_REGEX_KEY).getMapValue();
    Map<String, Value> values = innerMapValue.getFieldsMap();
    return innerMapValue.getFieldsCount() == 2
        && values.containsKey(RESERVED_REGEX_PATTERN_KEY)
        && values.containsKey(RESERVED_REGEX_OPTIONS_KEY)
        && values.get(RESERVED_REGEX_PATTERN_KEY).hasStringValue()
        && values.get(RESERVED_REGEX_OPTIONS_KEY).hasStringValue();
  }

  static boolean isBsonTimestamp(Value value) {
    if (!isMapWithSingleFieldOfType(
        value, RESERVED_BSON_TIMESTAMP_KEY, Value.ValueTypeCase.MAP_VALUE)) {
      return false;
    }

    MapValue innerMapValue =
        value.getMapValue().getFieldsMap().get(RESERVED_BSON_TIMESTAMP_KEY).getMapValue();
    Map<String, Value> values = innerMapValue.getFieldsMap();
    return innerMapValue.getFieldsCount() == 2
        && values.containsKey(RESERVED_BSON_TIMESTAMP_SECONDS_KEY)
        && values.containsKey(RESERVED_BSON_TIMESTAMP_INCREMENT_KEY)
        && values.get(RESERVED_BSON_TIMESTAMP_SECONDS_KEY).hasIntegerValue()
        && values.get(RESERVED_BSON_TIMESTAMP_INCREMENT_KEY).hasIntegerValue();
  }

  public enum MapRepresentation {
    REGEX,
    BSON_OBJECT_ID,
    INT32,
    DECIMAL128,
    BSON_TIMESTAMP,
    BSON_BINARY,
    MIN_KEY,
    MAX_KEY,
    INTERNAL_MAX,
    VECTOR,
    SERVER_TIMESTAMP,
    REGULAR_MAP
  }

  public static MapRepresentation detectMapRepresentation(Value value) {
    if (value == null
        || value.getMapValue() == null
        || value.getMapValue().getFieldsMap() == null) {
      return MapRepresentation.REGULAR_MAP;
    }

    // Check for BSON-related mappings
    if (isRegexValue(value)) {
      return MapRepresentation.REGEX;
    }
    if (isBsonObjectId(value)) {
      return MapRepresentation.BSON_OBJECT_ID;
    }
    if (isInt32Value(value)) {
      return MapRepresentation.INT32;
    }
    if (isDecimal128Value(value)) {
      return MapRepresentation.DECIMAL128;
    }
    if (isBsonTimestamp(value)) {
      return MapRepresentation.BSON_TIMESTAMP;
    }
    if (isBsonBinaryData(value)) {
      return MapRepresentation.BSON_BINARY;
    }
    if (isMinKey(value)) {
      return MapRepresentation.MIN_KEY;
    }
    if (isMaxKey(value)) {
      return MapRepresentation.MAX_KEY;
    }

    Map<String, Value> fields = value.getMapValue().getFieldsMap();

    // Check for type-based mappings
    if (fields.containsKey(TYPE_KEY)) {
      String typeString = fields.get(TYPE_KEY).getStringValue();
      if (typeString.equals(RESERVED_VECTOR_KEY)) {
        return MapRepresentation.VECTOR;
      }
      if (typeString.equals(RESERVED_MAX_KEY)) {
        return MapRepresentation.INTERNAL_MAX;
      }
      if (typeString.equals(RESERVED_SERVER_TIMESTAMP_KEY)) {
        return MapRepresentation.SERVER_TIMESTAMP;
      }
    }

    return MapRepresentation.REGULAR_MAP;
  }
}
