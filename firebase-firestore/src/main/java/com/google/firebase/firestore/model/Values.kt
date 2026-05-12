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
package com.google.firebase.firestore.model

import com.google.cloud.datastore.core.number.NumberComparisonHelper.firestoreCompareDoubleWithLong
import com.google.cloud.datastore.core.number.NumberComparisonHelper.firestoreCompareDoubles
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Quadruple
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.util.Assert
import com.google.firebase.firestore.util.Util
import com.google.firestore.v1.ArrayValue
import com.google.firestore.v1.ArrayValueOrBuilder
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase
import com.google.protobuf.ByteString
import com.google.protobuf.NullValue
import com.google.protobuf.Timestamp
import com.google.type.LatLng
import java.util.Date
import java.util.TreeMap
import kotlin.math.min

object Values {
  const val TYPE_KEY: String = "__type__"
  const val RESERVED_VECTOR_KEY: String = "__vector__"
  const val RESERVED_MIN_KEY: String = "__min__"
  const val RESERVED_MAX_KEY: String = "__max__"
  const val RESERVED_REGEX_KEY: String = "__regex__"
  const val RESERVED_REGEX_PATTERN_KEY: String = "pattern"
  const val RESERVED_REGEX_OPTIONS_KEY: String = "options"
  const val RESERVED_OBJECT_ID_KEY: String = "__oid__"
  const val RESERVED_INT32_KEY: String = "__int__"
  const val RESERVED_DECIMAL128_KEY: String = "__decimal128__"
  const val RESERVED_BSON_TIMESTAMP_KEY: String = "__request_timestamp__"
  const val RESERVED_BSON_TIMESTAMP_SECONDS_KEY: String = "seconds"
  const val RESERVED_BSON_TIMESTAMP_INCREMENT_KEY: String = "increment"
  const val RESERVED_BSON_BINARY_KEY: String = "__binary__"
  const val RESERVED_SERVER_TIMESTAMP_KEY: String = "server_timestamp"

  @JvmField val NAN_VALUE: Value = Value.newBuilder().setDoubleValue(Double.NaN).build()
  @JvmField val NULL_VALUE: Value = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
  @JvmField val INTERNAL_MIN_VALUE: Value = NULL_VALUE
  @JvmField val MAX_VALUE_TYPE: Value = Value.newBuilder().setStringValue(RESERVED_MAX_KEY).build()
  @JvmField
  val INTERNAL_MAX_VALUE: Value =
    Value.newBuilder()
      .setMapValue(MapValue.newBuilder().putFields(TYPE_KEY, MAX_VALUE_TYPE))
      .build()

  @JvmField
  val VECTOR_VALUE_TYPE: Value = Value.newBuilder().setStringValue(RESERVED_VECTOR_KEY).build()
  const val VECTOR_MAP_VECTORS_KEY: String = "value"
  private val MIN_VECTOR_VALUE: Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields(TYPE_KEY, VECTOR_VALUE_TYPE)
          .putFields(
            VECTOR_MAP_VECTORS_KEY,
            Value.newBuilder().setArrayValue(ArrayValue.newBuilder()).build()
          )
      )
      .build()

  private val MIN_BSON_BINARY_VALUE: Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields(
            RESERVED_BSON_BINARY_KEY,
            Value.newBuilder().setBytesValue(ByteString.copyFrom(byteArrayOf(0))).build()
          )
      )
      .build()

  private val MIN_BSON_TIMESTAMP_VALUE: Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields(
            RESERVED_BSON_TIMESTAMP_KEY,
            Value.newBuilder()
              .setMapValue(
                MapValue.newBuilder()
                  .putFields(
                    RESERVED_BSON_TIMESTAMP_SECONDS_KEY,
                    Value.newBuilder().setIntegerValue(0L).build()
                  )
                  .putFields(
                    RESERVED_BSON_TIMESTAMP_INCREMENT_KEY,
                    Value.newBuilder().setIntegerValue(0L).build()
                  )
              )
              .build()
          )
      )
      .build()

  private val MIN_BSON_OBJECT_ID_VALUE: Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields(RESERVED_OBJECT_ID_KEY, Value.newBuilder().setStringValue("").build())
      )
      .build()

  private val MIN_REGEX_VALUE: Value =
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
                    Value.newBuilder().setStringValue("").build()
                  )
                  .putFields(
                    RESERVED_REGEX_OPTIONS_KEY,
                    Value.newBuilder().setStringValue("").build()
                  )
              )
              .build()
          )
      )
      .build()

  @JvmField
  val MIN_KEY_VALUE: Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields(
            RESERVED_MIN_KEY,
            Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
          )
      )
      .build()

  @JvmField
  val MAX_KEY_VALUE: Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields(
            RESERVED_MAX_KEY,
            Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
          )
      )
      .build()

  /**
   * The order of types in Firestore. This order is based on the backend's ordering, but modified to
   * support server timestamps and [.INTERNAL_MAX_VALUE].
   */
  const val TYPE_ORDER_NULL: Int = 0
  const val TYPE_ORDER_UNSET: Int = 0

  const val TYPE_ORDER_MIN_KEY: Int = 1
  const val TYPE_ORDER_BOOLEAN: Int = 2
  const val TYPE_ORDER_NUMBER_NAN: Int = 3
  const val TYPE_ORDER_NUMBER: Int = 4
  const val TYPE_ORDER_TIMESTAMP: Int = 5
  const val TYPE_ORDER_BSON_TIMESTAMP: Int = 6
  const val TYPE_ORDER_SERVER_TIMESTAMP: Int = 7
  const val TYPE_ORDER_STRING: Int = 8
  const val TYPE_ORDER_BLOB: Int = 9
  const val TYPE_ORDER_BSON_BINARY: Int = 10
  const val TYPE_ORDER_REFERENCE: Int = 11
  const val TYPE_ORDER_BSON_OBJECT_ID: Int = 12
  const val TYPE_ORDER_GEOPOINT: Int = 13
  const val TYPE_ORDER_REGEX: Int = 14
  const val TYPE_ORDER_ARRAY: Int = 15
  const val TYPE_ORDER_VECTOR: Int = 16
  const val TYPE_ORDER_MAP: Int = 17
  const val TYPE_ORDER_MAX_KEY: Int = 18

  const val TYPE_ORDER_MAX_VALUE: Int = Int.MAX_VALUE

  /** Returns the backend's type order of the given Value type. */
  @JvmStatic
  fun typeOrder(value: Value?): Int {
    return when (value?.valueTypeCase) {
      null -> TYPE_ORDER_UNSET
      ValueTypeCase.NULL_VALUE -> TYPE_ORDER_NULL
      ValueTypeCase.BOOLEAN_VALUE -> TYPE_ORDER_BOOLEAN
      ValueTypeCase.INTEGER_VALUE -> TYPE_ORDER_NUMBER
      ValueTypeCase.DOUBLE_VALUE -> {
        if (java.lang.Double.isNaN(value.doubleValue)) {
          TYPE_ORDER_NUMBER_NAN
        } else {
          TYPE_ORDER_NUMBER
        }
      }
      ValueTypeCase.TIMESTAMP_VALUE -> TYPE_ORDER_TIMESTAMP
      ValueTypeCase.STRING_VALUE -> TYPE_ORDER_STRING
      ValueTypeCase.BYTES_VALUE -> TYPE_ORDER_BLOB
      ValueTypeCase.REFERENCE_VALUE -> TYPE_ORDER_REFERENCE
      ValueTypeCase.GEO_POINT_VALUE -> TYPE_ORDER_GEOPOINT
      ValueTypeCase.ARRAY_VALUE -> TYPE_ORDER_ARRAY
      ValueTypeCase.MAP_VALUE -> {
        val mapType = detectMapRepresentation(value)
        when (mapType) {
          MapRepresentation.SERVER_TIMESTAMP -> TYPE_ORDER_SERVER_TIMESTAMP
          MapRepresentation.INTERNAL_MAX -> TYPE_ORDER_MAX_VALUE
          MapRepresentation.VECTOR -> TYPE_ORDER_VECTOR
          MapRepresentation.MIN_KEY -> TYPE_ORDER_MIN_KEY
          MapRepresentation.MAX_KEY -> TYPE_ORDER_MAX_KEY
          MapRepresentation.REGEX -> TYPE_ORDER_REGEX
          MapRepresentation.BSON_TIMESTAMP -> TYPE_ORDER_BSON_TIMESTAMP
          MapRepresentation.BSON_OBJECT_ID -> TYPE_ORDER_BSON_OBJECT_ID
          MapRepresentation.BSON_BINARY -> TYPE_ORDER_BSON_BINARY
          MapRepresentation.INT32 -> TYPE_ORDER_NUMBER
          MapRepresentation.DECIMAL128 -> {
            if (isDecimal128Nan(value)) {
              TYPE_ORDER_NUMBER_NAN
            } else {
              TYPE_ORDER_NUMBER
            }
          }
          else -> TYPE_ORDER_MAP
        }
      }
      else -> throw Assert.fail("Invalid value type: " + value.valueTypeCase)
    }
  }

  @JvmStatic
  fun equals(left: Value?, right: Value?): Boolean {
    if (left === right) {
      return true
    }

    if (left == null || right == null) {
      return false
    }

    val leftType = typeOrder(left)
    val rightType = typeOrder(right)
    if (leftType != rightType) {
      return false
    }

    return when (leftType) {
      TYPE_ORDER_NUMBER_NAN,
      TYPE_ORDER_NUMBER -> numberEquals(left, right)
      TYPE_ORDER_ARRAY -> arrayEquals(left, right)
      TYPE_ORDER_VECTOR,
      TYPE_ORDER_MAP -> objectEquals(left, right)
      TYPE_ORDER_SERVER_TIMESTAMP ->
        ServerTimestamps.getLocalWriteTime(left) == ServerTimestamps.getLocalWriteTime(right)
      TYPE_ORDER_MAX_VALUE -> true
      else -> left == right
    }
  }

  private fun numberEquals(left: Value, right: Value): Boolean = compareNumbers(left, right) == 0

  private fun arrayEquals(left: Value, right: Value): Boolean {
    val leftArray = left.arrayValue
    val rightArray = right.arrayValue

    if (leftArray.valuesCount != rightArray.valuesCount) {
      return false
    }

    for (i in 0 until leftArray.valuesCount) {
      if (!equals(leftArray.getValues(i), rightArray.getValues(i))) {
        return false
      }
    }

    return true
  }

  private fun objectEquals(left: Value, right: Value): Boolean {
    val leftMap = left.mapValue
    val rightMap = right.mapValue

    if (leftMap.fieldsCount != rightMap.fieldsCount) {
      return false
    }

    for ((key, value) in leftMap.fieldsMap) {
      val otherEntry = rightMap.fieldsMap[key] ?: return false
      if (!equals(value, otherEntry)) {
        return false
      }
    }

    return true
  }

  internal object Enterprise {

    internal fun equals(left: Value?, right: Value?): Boolean {
      return Values.equals(left, right)
    }

    internal val compare = Values::compare

    internal enum class CompareResult {
      LESS_THAN,
      EQUAL,
      GREATER_THAN,
      TYPE_MISMATCH
    }

    internal fun strictCompare(left: Value?, right: Value?): CompareResult {
      // both are UNSET
      if (left == null && right == null) {
        return CompareResult.EQUAL
      }

      // One is UNSET
      if (left == null || right == null) {
        return CompareResult.TYPE_MISMATCH
      }

      val leftType = typeOrder(left)
      val rightType = typeOrder(right)
      if (leftType != rightType) {
        return CompareResult.TYPE_MISMATCH
      }

      // It's OK to use !! here because they cannot be null
      val cmp = compareInternal(leftType, left!!, right!!)
      if (cmp < 0) {
        return CompareResult.LESS_THAN
      } else if (cmp > 0) {
        return CompareResult.GREATER_THAN
      }
      return CompareResult.EQUAL
    }
  }

  /** Returns true if the Value list contains the specified element. */
  @JvmStatic
  fun contains(haystack: ArrayValueOrBuilder, needle: Value?): Boolean {
    for (haystackElement in haystack.valuesList) {
      if (equals(haystackElement, needle)) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun compare(left: Value?, right: Value?): Int {
    val leftType = typeOrder(left)
    val rightType = typeOrder(right)

    if (leftType != rightType) {
      return leftType.compareTo(rightType)
    }

    return compareInternal(leftType, left ?: NULL_VALUE, right ?: NULL_VALUE)
  }

  private fun compareInternal(leftType: Int, left: Value, right: Value): Int =
    when (leftType) {
      TYPE_ORDER_NULL,
      TYPE_ORDER_MIN_KEY,
      TYPE_ORDER_MAX_KEY,
      TYPE_ORDER_NUMBER_NAN,
      TYPE_ORDER_MAX_VALUE -> 0
      TYPE_ORDER_BOOLEAN -> left.booleanValue.compareTo(right.booleanValue)
      TYPE_ORDER_NUMBER -> compareNumbers(left, right)
      TYPE_ORDER_TIMESTAMP -> compareTimestamps(left.timestampValue, right.timestampValue)
      TYPE_ORDER_BSON_TIMESTAMP -> compareBsonTimestamp(left.mapValue, right.mapValue)
      TYPE_ORDER_SERVER_TIMESTAMP ->
        compareTimestamps(
          ServerTimestamps.getLocalWriteTime(left),
          ServerTimestamps.getLocalWriteTime(right)
        )
      TYPE_ORDER_STRING -> Util.compareUtf8Strings(left.stringValue, right.stringValue)
      TYPE_ORDER_BLOB -> Util.compareByteStrings(left.bytesValue, right.bytesValue)
      TYPE_ORDER_BSON_BINARY -> compareBsonBinary(left.mapValue, right.mapValue)
      TYPE_ORDER_REFERENCE -> compareReferences(left.referenceValue, right.referenceValue)
      TYPE_ORDER_BSON_OBJECT_ID -> compareBsonObjectId(left.mapValue, right.mapValue)
      TYPE_ORDER_GEOPOINT -> compareGeoPoints(left.geoPointValue, right.geoPointValue)
      TYPE_ORDER_REGEX -> compareRegex(left.mapValue, right.mapValue)
      TYPE_ORDER_ARRAY -> compareArrays(left.arrayValue, right.arrayValue)
      TYPE_ORDER_MAP -> compareMaps(left.mapValue, right.mapValue)
      TYPE_ORDER_VECTOR -> compareVectors(left.mapValue, right.mapValue)
      else -> throw Assert.fail("Invalid value type: $leftType")
    }

  @JvmStatic
  fun lowerBoundCompare(
    left: Value,
    leftInclusive: Boolean,
    right: Value,
    rightInclusive: Boolean
  ): Int {
    val cmp = compare(left, right)
    if (cmp != 0) {
      return cmp
    }

    if (leftInclusive && !rightInclusive) {
      return -1
    } else if (!leftInclusive && rightInclusive) {
      return 1
    }

    return 0
  }

  @JvmStatic
  fun upperBoundCompare(
    left: Value,
    leftInclusive: Boolean,
    right: Value,
    rightInclusive: Boolean
  ): Int {
    val cmp = compare(left, right)
    if (cmp != 0) {
      return cmp
    }

    if (leftInclusive && !rightInclusive) {
      return 1
    } else if (!leftInclusive && rightInclusive) {
      return -1
    }

    return 0
  }

  private fun compareNumbers(left: Value, right: Value): Int {
    if (isDecimal128Value(left) || isDecimal128Value(right)) {
      val leftQuadruple = convertNumberToQuadruple(left)
      val rightQuadruple = convertNumberToQuadruple(right)
      return Util.compareQuadruples(leftQuadruple, rightQuadruple)
    }

    if (isDouble(left)) {
      val leftDouble = left.doubleValue
      if (isDouble(right)) {
        return firestoreCompareDoubles(leftDouble, right.doubleValue)
      } else if (isIntegerValue(right)) {
        return firestoreCompareDoubleWithLong(leftDouble, getIntegerValue(right))
      }
    }

    if (isIntegerValue(left)) {
      val leftLong = getIntegerValue(left)
      if (isIntegerValue(right)) {
        return leftLong.compareTo(getIntegerValue(right))
      } else if (isDouble(right)) {
        return -1 * firestoreCompareDoubleWithLong(right.doubleValue, leftLong)
      }
    }

    throw Assert.fail("Unexpected values: %s vs %s", left, right)
  }

  private fun convertNumberToQuadruple(value: Value): Quadruple {
    if (isDecimal128Value(value)) {
      return Quadruple.fromString(value.mapValue.fieldsMap[RESERVED_DECIMAL128_KEY]!!.stringValue)
    }
    if (isDouble(value)) {
      return Quadruple.fromDouble(value.doubleValue)
    }
    if (isIntegerValue(value)) {
      return Quadruple.fromLong(getIntegerValue(value))
    }
    throw IllegalArgumentException("convertNumberToQuadruple called with non-numeric argument")
  }

  private fun getIntegerValue(value: Value): Long {
    if (value.hasIntegerValue()) {
      return value.integerValue
    }
    if (isInt32Value(value)) {
      return value.mapValue.fieldsMap[RESERVED_INT32_KEY]!!.integerValue
    }
    throw IllegalArgumentException("getIntegerValue was called with a non-integer argument")
  }

  private fun isIntegerValue(value: Value): Boolean = value.hasIntegerValue() || isInt32Value(value)

  private fun compareBsonBinary(left: MapValue, right: MapValue): Int {
    val lhs = left.fieldsMap[RESERVED_BSON_BINARY_KEY]!!.bytesValue
    val rhs = right.fieldsMap[RESERVED_BSON_BINARY_KEY]!!.bytesValue
    return Util.compareByteStrings(lhs, rhs)
  }

  private fun compareBsonTimestamp(left: MapValue, right: MapValue): Int {
    val leftFields = left.fieldsMap[RESERVED_BSON_TIMESTAMP_KEY]!!.mapValue.fieldsMap
    val rightFields = right.fieldsMap[RESERVED_BSON_TIMESTAMP_KEY]!!.mapValue.fieldsMap
    val cmp =
      leftFields[RESERVED_BSON_TIMESTAMP_SECONDS_KEY]!!
        .integerValue
        .compareTo(rightFields[RESERVED_BSON_TIMESTAMP_SECONDS_KEY]!!.integerValue)
    if (cmp != 0) {
      return cmp
    }
    return leftFields[RESERVED_BSON_TIMESTAMP_INCREMENT_KEY]!!
      .integerValue
      .compareTo(rightFields[RESERVED_BSON_TIMESTAMP_INCREMENT_KEY]!!.integerValue)
  }

  private fun compareBsonObjectId(left: MapValue, right: MapValue): Int {
    val lhs = left.fieldsMap[RESERVED_OBJECT_ID_KEY]!!.stringValue
    val rhs = right.fieldsMap[RESERVED_OBJECT_ID_KEY]!!.stringValue
    return Util.compareUtf8Strings(lhs, rhs)
  }

  private fun compareRegex(left: MapValue, right: MapValue): Int {
    val leftFields = left.fieldsMap[RESERVED_REGEX_KEY]!!.mapValue.fieldsMap
    val rightFields = right.fieldsMap[RESERVED_REGEX_KEY]!!.mapValue.fieldsMap
    val cmp =
      Util.compareUtf8Strings(
        leftFields[RESERVED_REGEX_PATTERN_KEY]!!.stringValue,
        rightFields[RESERVED_REGEX_PATTERN_KEY]!!.stringValue
      )
    if (cmp != 0) {
      return cmp
    }
    return Util.compareUtf8Strings(
      leftFields[RESERVED_REGEX_OPTIONS_KEY]!!.stringValue,
      rightFields[RESERVED_REGEX_OPTIONS_KEY]!!.stringValue
    )
  }

  private fun compareTimestamps(left: Timestamp, right: Timestamp): Int {
    val cmp = left.seconds.compareTo(right.seconds)
    if (cmp != 0) {
      return cmp
    }
    return left.nanos.compareTo(right.nanos)
  }

  private fun compareReferences(leftPath: String, rightPath: String): Int {
    val leftSegments = leftPath.split("/".toRegex()).toTypedArray()
    val rightSegments = rightPath.split("/".toRegex()).toTypedArray()

    val minLength = min(leftSegments.size.toDouble(), rightSegments.size.toDouble()).toInt()
    for (i in 0 until minLength) {
      val cmp = leftSegments[i].compareTo(rightSegments[i])
      if (cmp != 0) {
        return cmp
      }
    }
    return leftSegments.size.compareTo(rightSegments.size)
  }

  private fun compareGeoPoints(left: LatLng, right: LatLng): Int {
    val comparison = firestoreCompareDoubles(left.latitude, right.latitude)
    if (comparison == 0) {
      return firestoreCompareDoubles(left.longitude, right.longitude)
    }
    return comparison
  }

  private fun compareArrays(left: ArrayValue, right: ArrayValue): Int {
    val minLength = min(left.valuesCount.toDouble(), right.valuesCount.toDouble()).toInt()
    for (i in 0 until minLength) {
      val cmp = compare(left.getValues(i), right.getValues(i))
      if (cmp != 0) {
        return cmp
      }
    }
    return left.valuesCount.compareTo(right.valuesCount)
  }

  private fun compareMaps(left: MapValue, right: MapValue): Int {
    val iterator1: Iterator<Map.Entry<String, Value>> = TreeMap(left.fieldsMap).entries.iterator()
    val iterator2: Iterator<Map.Entry<String, Value>> = TreeMap(right.fieldsMap).entries.iterator()
    while (iterator1.hasNext() && iterator2.hasNext()) {
      val entry1 = iterator1.next()
      val entry2 = iterator2.next()
      val keyCompare = Util.compareUtf8Strings(entry1.key, entry2.key)
      if (keyCompare != 0) {
        return keyCompare
      }
      val valueCompare = compare(entry1.value, entry2.value)
      if (valueCompare != 0) {
        return valueCompare
      }
    }

    // Only equal if both iterators are exhausted.
    return iterator1.hasNext().compareTo(iterator2.hasNext())
  }

  private fun compareVectors(left: MapValue, right: MapValue): Int {
    val leftMap = left.fieldsMap
    val rightMap = right.fieldsMap

    // The vector is a map, but only vector value is compared.
    val leftArrayValue = leftMap[VECTOR_MAP_VECTORS_KEY]!!.arrayValue
    val rightArrayValue = rightMap[VECTOR_MAP_VECTORS_KEY]!!.arrayValue

    val lengthCompare = leftArrayValue.valuesCount.compareTo(rightArrayValue.valuesCount)
    if (lengthCompare != 0) {
      return lengthCompare
    }

    return compareArrays(leftArrayValue, rightArrayValue)
  }

  /** Generate the canonical ID for the provided field value (as used in Target serialization). */
  @JvmStatic
  fun canonicalId(value: Value): String {
    val builder = StringBuilder()
    canonifyValue(builder, value)
    return builder.toString()
  }

  private fun canonifyValue(builder: StringBuilder, value: Value) {
    when (value.valueTypeCase) {
      ValueTypeCase.NULL_VALUE -> builder.append("null")
      ValueTypeCase.BOOLEAN_VALUE -> builder.append(value.booleanValue)
      ValueTypeCase.INTEGER_VALUE -> builder.append(value.integerValue)
      ValueTypeCase.DOUBLE_VALUE -> builder.append(value.doubleValue)
      ValueTypeCase.TIMESTAMP_VALUE -> canonifyTimestamp(builder, value.timestampValue)
      ValueTypeCase.STRING_VALUE -> builder.append(value.stringValue)
      ValueTypeCase.BYTES_VALUE -> builder.append(Util.toDebugString(value.bytesValue))
      ValueTypeCase.REFERENCE_VALUE -> canonifyReference(builder, value)
      ValueTypeCase.GEO_POINT_VALUE -> canonifyGeoPoint(builder, value.geoPointValue)
      ValueTypeCase.ARRAY_VALUE -> canonifyArray(builder, value.arrayValue)
      ValueTypeCase.MAP_VALUE -> canonifyObject(builder, value.mapValue)
      else -> throw Assert.fail("Invalid value type: " + value.valueTypeCase)
    }
  }

  private fun canonifyTimestamp(builder: StringBuilder, timestamp: Timestamp) {
    builder.append(String.format("time(%s,%s)", timestamp.seconds, timestamp.nanos))
  }

  private fun canonifyGeoPoint(builder: StringBuilder, latLng: LatLng) {
    builder.append(String.format("geo(%s,%s)", latLng.latitude, latLng.longitude))
  }

  private fun canonifyReference(builder: StringBuilder, value: Value) {
    Assert.hardAssert(isReferenceValue(value), "Value should be a ReferenceValue")
    builder.append(DocumentKey.fromName(value.referenceValue))
  }

  private fun canonifyObject(builder: StringBuilder, mapValue: MapValue) {
    // Even though MapValue are likely sorted correctly based on their insertion order (for example,
    // when received from the backend), local modifications can bring elements out of order. We need
    // to re-sort the elements to ensure that canonical IDs are independent of insertion order.
    val keys = ArrayList(mapValue.fieldsMap.keys)
    keys.sort()

    builder.append("{")
    val iterator = keys.iterator()
    while (iterator.hasNext()) {
      val key = iterator.next()
      builder.append(key).append(":")
      canonifyValue(builder, mapValue.getFieldsOrThrow(key))
      if (iterator.hasNext()) {
        builder.append(",")
      }
    }
    builder.append("}")
  }

  private fun canonifyArray(builder: StringBuilder, arrayValue: ArrayValue) {
    builder.append("[")
    if (arrayValue.valuesCount > 0) {
      canonifyValue(builder, arrayValue.getValues(0))
      for (i in 1 until arrayValue.valuesCount) {
        builder.append(",")
        canonifyValue(builder, arrayValue.getValues(i))
      }
    }
    builder.append("]")
  }

  /** Returns true if `value` is a INTEGER_VALUE. */
  @JvmStatic
  fun isInteger(value: Value?): Boolean {
    return value != null && value.hasIntegerValue()
  }

  @JvmStatic fun isInt64Value(value: Value?): Boolean = isInteger(value)

  /** Returns true if `value` is a DOUBLE_VALUE. */
  @JvmStatic
  fun isDouble(value: Value?): Boolean {
    return value != null && value.hasDoubleValue()
  }

  /** Returns true if `value` is either a INTEGER_VALUE or a DOUBLE_VALUE. */
  @JvmStatic
  fun isNumber(value: Value?): Boolean {
    return isInteger(value) || isDouble(value)
  }

  /** Returns true if `value` is an ARRAY_VALUE. */
  @JvmStatic
  fun isArray(value: Value?): Boolean {
    return value != null && value.hasArrayValue()
  }

  @JvmStatic
  fun isReferenceValue(value: Value?): Boolean {
    return value != null && value.hasReferenceValue()
  }

  @JvmStatic
  fun isNullValue(value: Value?): Boolean {
    return value != null && value.hasNullValue()
  }

  @JvmStatic
  fun isNanValue(value: Value?): Boolean {
    return value != null && java.lang.Double.isNaN(value.doubleValue)
  }

  @JvmStatic
  fun isDecimal128Nan(value: Value?): Boolean {
    if (value == null || !isDecimal128Value(value)) {
      return false
    }
    val str = value.mapValue.fieldsMap[RESERVED_DECIMAL128_KEY]!!.stringValue
    return str.equals("NaN", ignoreCase = true)
  }

  @JvmStatic
  fun isMapValue(value: Value?): Boolean {
    return value != null && value.hasMapValue()
  }

  @JvmStatic
  fun refValue(databaseId: DatabaseId, key: DocumentKey): Value {
    val value =
      Value.newBuilder()
        .setReferenceValue(
          String.format(
            "projects/%s/databases/%s/documents/%s",
            databaseId.projectId,
            databaseId.databaseId,
            key.toString()
          )
        )
        .build()
    return value
  }

  private val MIN_BOOLEAN: Value = Value.newBuilder().setBooleanValue(false).build()
  private val MIN_NUMBER: Value = Value.newBuilder().setDoubleValue(Double.NaN).build()
  private val MIN_TIMESTAMP: Value =
    Value.newBuilder().setTimestampValue(Timestamp.newBuilder().setSeconds(Long.MIN_VALUE)).build()
  private val MIN_STRING: Value = Value.newBuilder().setStringValue("").build()
  private val MIN_BYTES: Value = Value.newBuilder().setBytesValue(ByteString.EMPTY).build()
  private val MIN_REFERENCE: Value = refValue(DatabaseId.EMPTY, DocumentKey.empty())
  private val MIN_GEO_POINT: Value =
    Value.newBuilder()
      .setGeoPointValue(LatLng.newBuilder().setLatitude(-90.0).setLongitude(-180.0))
      .build()
  private val MIN_ARRAY: Value =
    Value.newBuilder().setArrayValue(ArrayValue.getDefaultInstance()).build()
  private val MIN_MAP: Value = Value.newBuilder().setMapValue(MapValue.getDefaultInstance()).build()

  /** Returns the lowest value for the given value type (inclusive). */
  @JvmStatic
  fun getLowerBound(value: Value): Value {
    val result =
      when (value.valueTypeCase) {
        ValueTypeCase.NULL_VALUE -> NULL_VALUE
        ValueTypeCase.BOOLEAN_VALUE -> MIN_BOOLEAN
        ValueTypeCase.INTEGER_VALUE,
        ValueTypeCase.DOUBLE_VALUE -> MIN_NUMBER
        ValueTypeCase.TIMESTAMP_VALUE -> MIN_TIMESTAMP
        ValueTypeCase.STRING_VALUE -> MIN_STRING
        ValueTypeCase.BYTES_VALUE -> MIN_BYTES
        ValueTypeCase.REFERENCE_VALUE -> MIN_REFERENCE
        ValueTypeCase.GEO_POINT_VALUE -> MIN_GEO_POINT
        ValueTypeCase.ARRAY_VALUE -> MIN_ARRAY
        ValueTypeCase.MAP_VALUE -> {
          val mapType = detectMapRepresentation(value)
          when (mapType) {
            MapRepresentation.VECTOR -> MIN_VECTOR_VALUE
            MapRepresentation.BSON_OBJECT_ID -> MIN_BSON_OBJECT_ID_VALUE
            MapRepresentation.BSON_TIMESTAMP -> MIN_BSON_TIMESTAMP_VALUE
            MapRepresentation.BSON_BINARY -> MIN_BSON_BINARY_VALUE
            MapRepresentation.REGEX -> MIN_REGEX_VALUE
            MapRepresentation.INT32,
            MapRepresentation.DECIMAL128 -> MIN_NUMBER
            MapRepresentation.MIN_KEY -> MIN_KEY_VALUE
            MapRepresentation.MAX_KEY -> MAX_KEY_VALUE
            else -> MIN_MAP
          }
        }
        else -> throw IllegalArgumentException("Unknown value type: " + value.valueTypeCase)
      }
    return result
  }

  /** Returns the largest value for the given value type (exclusive). */
  @JvmStatic
  fun getUpperBound(value: Value): Value {
    val result =
      when (value.valueTypeCase) {
        ValueTypeCase.NULL_VALUE -> MIN_KEY_VALUE
        ValueTypeCase.BOOLEAN_VALUE -> MIN_NUMBER
        ValueTypeCase.INTEGER_VALUE,
        ValueTypeCase.DOUBLE_VALUE -> MIN_TIMESTAMP
        ValueTypeCase.TIMESTAMP_VALUE -> MIN_BSON_TIMESTAMP_VALUE
        ValueTypeCase.STRING_VALUE -> MIN_BYTES
        ValueTypeCase.BYTES_VALUE -> MIN_BSON_BINARY_VALUE
        ValueTypeCase.REFERENCE_VALUE -> MIN_BSON_OBJECT_ID_VALUE
        ValueTypeCase.GEO_POINT_VALUE -> MIN_REGEX_VALUE
        ValueTypeCase.ARRAY_VALUE -> MIN_VECTOR_VALUE
        ValueTypeCase.MAP_VALUE -> {
          val mapType = detectMapRepresentation(value)
          when (mapType) {
            MapRepresentation.VECTOR -> MIN_MAP
            MapRepresentation.BSON_OBJECT_ID -> MIN_GEO_POINT
            MapRepresentation.BSON_TIMESTAMP -> MIN_STRING
            MapRepresentation.BSON_BINARY -> MIN_REFERENCE
            MapRepresentation.REGEX -> MIN_ARRAY
            MapRepresentation.INT32,
            MapRepresentation.DECIMAL128 -> MIN_TIMESTAMP
            MapRepresentation.MIN_KEY -> MIN_BOOLEAN
            MapRepresentation.MAX_KEY -> INTERNAL_MAX_VALUE
            else -> MAX_KEY_VALUE
          }
        }
        else -> throw IllegalArgumentException("Unknown value type: " + value.valueTypeCase)
      }
    return result
  }

  /** Returns true if the Value represents the canonical [.MAX_VALUE] . */
  @JvmStatic
  fun isMaxValue(value: Value): Boolean {
    return MAX_VALUE_TYPE == value.mapValue.fieldsMap[TYPE_KEY]
  }

  /** Returns true if the Value represents a VectorValue . */
  @JvmStatic
  fun isVectorValue(value: Value): Boolean {
    return VECTOR_VALUE_TYPE == value.mapValue.fieldsMap[TYPE_KEY]
  }

  @JvmStatic fun encodeValue(value: Long): Value = Value.newBuilder().setIntegerValue(value).build()

  @JvmStatic
  fun encodeValue(value: Int): Value = Value.newBuilder().setIntegerValue(value.toLong()).build()

  @JvmStatic
  fun encodeValue(value: Double): Value = Value.newBuilder().setDoubleValue(value).build()

  @JvmStatic
  fun encodeValue(value: Float): Value = Value.newBuilder().setDoubleValue(value.toDouble()).build()

  @JvmStatic
  fun encodeValue(value: Number): Value =
    when (value) {
      is Long -> encodeValue(value)
      is Int -> encodeValue(value)
      is Double -> encodeValue(value)
      is Float -> encodeValue(value)
      else -> throw IllegalArgumentException("Unexpected number type: $value")
    }

  @JvmStatic
  fun encodeValue(value: String): Value = Value.newBuilder().setStringValue(value).build()

  @JvmStatic
  fun encodeValue(value: ResourcePath): Value =
    Value.newBuilder().setReferenceValue("/${value.canonicalString()}").build()

  @JvmStatic fun encodeValue(date: Date): Value = encodeValue(com.google.firebase.Timestamp((date)))

  @JvmStatic
  fun encodeValue(timestamp: com.google.firebase.Timestamp): Value =
    encodeValue(timestamp(timestamp.seconds, timestamp.nanoseconds))

  @JvmStatic
  fun encodeValue(value: Timestamp): Value = Value.newBuilder().setTimestampValue(value).build()

  @JvmField val TRUE_VALUE: Value = Value.newBuilder().setBooleanValue(true).build()

  @JvmField val FALSE_VALUE: Value = Value.newBuilder().setBooleanValue(false).build()

  @JvmStatic fun encodeValue(value: Boolean): Value = if (value) TRUE_VALUE else FALSE_VALUE

  @JvmStatic
  fun encodeValue(geoPoint: GeoPoint): Value =
    Value.newBuilder()
      .setGeoPointValue(
        LatLng.newBuilder().setLatitude(geoPoint.latitude).setLongitude(geoPoint.longitude)
      )
      .build()

  @JvmStatic
  fun encodeValue(value: ByteArray): Value =
    Value.newBuilder().setBytesValue(ByteString.copyFrom(value)).build()

  @JvmStatic
  fun encodeValue(value: Blob): Value =
    Value.newBuilder().setBytesValue(value.toByteString()).build()

  @JvmStatic
  fun encodeValue(docRef: DocumentReference): Value =
    Value.newBuilder().setReferenceValue(docRef.fullPath).build()

  @JvmStatic fun encodeValue(vector: VectorValue): Value = encodeVectorValue(vector.toArray())

  @JvmStatic
  fun encodeVectorValue(vector: DoubleArray): Value {
    val listBuilder = ArrayValue.newBuilder()
    for (value in vector) {
      listBuilder.addValues(encodeValue(value))
    }
    return Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields(TYPE_KEY, VECTOR_VALUE_TYPE)
          .putFields(VECTOR_MAP_VECTORS_KEY, Value.newBuilder().setArrayValue(listBuilder).build())
      )
      .build()
  }

  @JvmStatic
  fun encodeValue(map: Map<String, Value>): Value =
    Value.newBuilder().setMapValue(MapValue.newBuilder().putAllFields(map)).build()

  @JvmStatic
  fun encodeValue(values: Iterable<Value>): Value =
    Value.newBuilder().setArrayValue(ArrayValue.newBuilder().addAllValues(values)).build()

  @JvmStatic
  fun encodeAnyValue(value: Any?): Value =
    when (value) {
      null -> NULL_VALUE
      is String -> encodeValue(value)
      is Number -> encodeValue(value)
      is Date -> encodeValue(value)
      is com.google.firebase.Timestamp -> encodeValue(value)
      is Boolean -> encodeValue(value)
      is GeoPoint -> encodeValue(value)
      is Blob -> encodeValue(value)
      is VectorValue -> encodeValue(value)
      else -> throw IllegalArgumentException("Unexpected type: $value")
    }

  @JvmStatic
  fun timestamp(seconds: Long, nanos: Int): Timestamp {
    validateRange(seconds, nanos)

    // Firestore backend truncates precision down to microseconds. To ensure offline mode works
    // the same with regards to truncation, perform the truncation immediately without waiting for
    // the backend to do that.
    val truncatedNanoseconds: Int = nanos / 1000 * 1000
    return Timestamp.newBuilder().setSeconds(seconds).setNanos(truncatedNanoseconds).build()
  }

  @JvmStatic
  fun getVectorValue(value: Value?): DoubleArray? {
    if (value?.valueTypeCase != ValueTypeCase.MAP_VALUE || !isVectorValue(value)) {
      return null
    }

    return value.mapValue.fieldsMap[VECTOR_MAP_VECTORS_KEY]
      ?.arrayValue
      ?.valuesList
      ?.map { it.doubleValue }
      ?.toDoubleArray()
  }

  /**
   * Ensures that the date and time are within what we consider valid ranges.
   *
   * More specifically, the nanoseconds need to be less than 1 billion- otherwise it would trip over
   * into seconds, and need to be greater than zero.
   *
   * The seconds need to be after the date `1/1/1` and before the date `1/1/10000`.
   *
   * @throws IllegalArgumentException if the date and time are considered invalid
   */
  private fun validateRange(seconds: Long, nanoseconds: Int) {
    require(nanoseconds in 0 until 1_000_000_000) {
      "Timestamp nanoseconds out of range: $nanoseconds"
    }

    require(seconds in -62_135_596_800 until 253_402_300_800) {
      "Timestamp seconds out of range: $seconds"
    }
  }
  private fun isMapWithSingleFieldOfType(
    value: Value?,
    key: String,
    typeCase: ValueTypeCase
  ): Boolean {
    if (value == null || !value.hasMapValue()) {
      return false
    }
    val fields = value.mapValue.fieldsMap
    return fields.size == 1 && fields.containsKey(key) && fields[key]!!.valueTypeCase == typeCase
  }

  @JvmStatic
  fun isMinKey(value: Value?): Boolean =
    isMapWithSingleFieldOfType(value, RESERVED_MIN_KEY, ValueTypeCase.NULL_VALUE)

  @JvmStatic
  fun isMaxKey(value: Value?): Boolean =
    isMapWithSingleFieldOfType(value, RESERVED_MAX_KEY, ValueTypeCase.NULL_VALUE)

  @JvmStatic
  fun isInt32Value(value: Value?): Boolean =
    isMapWithSingleFieldOfType(value, RESERVED_INT32_KEY, ValueTypeCase.INTEGER_VALUE)

  @JvmStatic
  fun isDecimal128Value(value: Value?): Boolean =
    isMapWithSingleFieldOfType(value, RESERVED_DECIMAL128_KEY, ValueTypeCase.STRING_VALUE)

  @JvmStatic
  fun isBsonObjectId(value: Value?): Boolean =
    isMapWithSingleFieldOfType(value, RESERVED_OBJECT_ID_KEY, ValueTypeCase.STRING_VALUE)

  @JvmStatic
  fun isBsonBinaryData(value: Value?): Boolean =
    isMapWithSingleFieldOfType(value, RESERVED_BSON_BINARY_KEY, ValueTypeCase.BYTES_VALUE)

  @JvmStatic
  fun isRegexValue(value: Value?): Boolean {
    if (!isMapWithSingleFieldOfType(value, RESERVED_REGEX_KEY, ValueTypeCase.MAP_VALUE)) {
      return false
    }
    val innerMapValue = value!!.mapValue.fieldsMap[RESERVED_REGEX_KEY]!!.mapValue
    val values = innerMapValue.fieldsMap
    return innerMapValue.fieldsCount == 2 &&
      values.containsKey(RESERVED_REGEX_PATTERN_KEY) &&
      values.containsKey(RESERVED_REGEX_OPTIONS_KEY) &&
      values[RESERVED_REGEX_PATTERN_KEY]!!.hasStringValue() &&
      values[RESERVED_REGEX_OPTIONS_KEY]!!.hasStringValue()
  }

  @JvmStatic
  fun isBsonTimestamp(value: Value?): Boolean {
    if (!isMapWithSingleFieldOfType(value, RESERVED_BSON_TIMESTAMP_KEY, ValueTypeCase.MAP_VALUE)) {
      return false
    }
    val innerMapValue = value!!.mapValue.fieldsMap[RESERVED_BSON_TIMESTAMP_KEY]!!.mapValue
    val values = innerMapValue.fieldsMap
    return innerMapValue.fieldsCount == 2 &&
      values.containsKey(RESERVED_BSON_TIMESTAMP_SECONDS_KEY) &&
      values.containsKey(RESERVED_BSON_TIMESTAMP_INCREMENT_KEY) &&
      values[RESERVED_BSON_TIMESTAMP_SECONDS_KEY]!!.hasIntegerValue() &&
      values[RESERVED_BSON_TIMESTAMP_INCREMENT_KEY]!!.hasIntegerValue()
  }

  enum class MapRepresentation {
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

  @JvmStatic
  fun detectMapRepresentation(value: Value?): MapRepresentation {
    if (value == null || !value.hasMapValue()) {
      return MapRepresentation.REGULAR_MAP
    }

    // Check for BSON-related mappings
    if (isRegexValue(value)) {
      return MapRepresentation.REGEX
    }
    if (isBsonObjectId(value)) {
      return MapRepresentation.BSON_OBJECT_ID
    }
    if (isInt32Value(value)) {
      return MapRepresentation.INT32
    }
    if (isDecimal128Value(value)) {
      return MapRepresentation.DECIMAL128
    }
    if (isBsonTimestamp(value)) {
      return MapRepresentation.BSON_TIMESTAMP
    }
    if (isBsonBinaryData(value)) {
      return MapRepresentation.BSON_BINARY
    }
    if (isMinKey(value)) {
      return MapRepresentation.MIN_KEY
    }
    if (isMaxKey(value)) {
      return MapRepresentation.MAX_KEY
    }

    val fields = value.mapValue.fieldsMap

    // Check for type-based mappings
    if (fields.containsKey(TYPE_KEY)) {
      val typeString = fields[TYPE_KEY]!!.stringValue
      if (typeString == RESERVED_VECTOR_KEY) {
        return MapRepresentation.VECTOR
      }
      if (typeString == RESERVED_MAX_KEY) {
        return MapRepresentation.INTERNAL_MAX
      }
      if (typeString == RESERVED_SERVER_TIMESTAMP_KEY) {
        return MapRepresentation.SERVER_TIMESTAMP
      }
    }

    return MapRepresentation.REGULAR_MAP
  }
}
