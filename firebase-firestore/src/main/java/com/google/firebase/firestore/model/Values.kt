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

internal object Values {
  const val TYPE_KEY: String = "__type__"
  @JvmField val NAN_VALUE: Value = Value.newBuilder().setDoubleValue(Double.NaN).build()
  @JvmField val NULL_VALUE: Value = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
  @JvmField val MIN_VALUE: Value = NULL_VALUE
  @JvmField val MAX_VALUE_TYPE: Value = Value.newBuilder().setStringValue("__max__").build()
  @JvmField
  val MAX_VALUE: Value =
    Value.newBuilder()
      .setMapValue(MapValue.newBuilder().putFields(TYPE_KEY, MAX_VALUE_TYPE))
      .build()

  @JvmField val VECTOR_VALUE_TYPE: Value = Value.newBuilder().setStringValue("__vector__").build()
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

  /**
   * The order of types in Firestore. This order is based on the backend's ordering, but modified to
   * support server timestamps and [.MAX_VALUE].
   */
  const val TYPE_ORDER_NULL: Int = 0

  const val TYPE_ORDER_BOOLEAN: Int = 1
  const val TYPE_ORDER_NUMBER: Int = 2
  const val TYPE_ORDER_TIMESTAMP: Int = 3
  const val TYPE_ORDER_SERVER_TIMESTAMP: Int = 4
  const val TYPE_ORDER_STRING: Int = 5
  const val TYPE_ORDER_BLOB: Int = 6
  const val TYPE_ORDER_REFERENCE: Int = 7
  const val TYPE_ORDER_GEOPOINT: Int = 8
  const val TYPE_ORDER_ARRAY: Int = 9
  const val TYPE_ORDER_VECTOR: Int = 10
  const val TYPE_ORDER_MAP: Int = 11

  const val TYPE_ORDER_MAX_VALUE: Int = Int.MAX_VALUE

  /** Returns the backend's type order of the given Value type. */
  @JvmStatic
  fun typeOrder(value: Value): Int {
    return when (value.valueTypeCase) {
      ValueTypeCase.NULL_VALUE -> TYPE_ORDER_NULL
      ValueTypeCase.BOOLEAN_VALUE -> TYPE_ORDER_BOOLEAN
      ValueTypeCase.INTEGER_VALUE -> TYPE_ORDER_NUMBER
      ValueTypeCase.DOUBLE_VALUE -> TYPE_ORDER_NUMBER
      ValueTypeCase.TIMESTAMP_VALUE -> TYPE_ORDER_TIMESTAMP
      ValueTypeCase.STRING_VALUE -> TYPE_ORDER_STRING
      ValueTypeCase.BYTES_VALUE -> TYPE_ORDER_BLOB
      ValueTypeCase.REFERENCE_VALUE -> TYPE_ORDER_REFERENCE
      ValueTypeCase.GEO_POINT_VALUE -> TYPE_ORDER_GEOPOINT
      ValueTypeCase.ARRAY_VALUE -> TYPE_ORDER_ARRAY
      ValueTypeCase.MAP_VALUE ->
        if (ServerTimestamps.isServerTimestamp(value)) {
          TYPE_ORDER_SERVER_TIMESTAMP
        } else if (isMaxValue(value)) {
          TYPE_ORDER_MAX_VALUE
        } else if (isVectorValue(value)) {
          TYPE_ORDER_VECTOR
        } else {
          TYPE_ORDER_MAP
        }
      else -> throw Assert.fail("Invalid value type: " + value.valueTypeCase)
    }
  }

  fun strictEquals(left: Value, right: Value): Boolean? {
    if (left.hasNullValue() || right.hasNullValue()) return null
    val leftType = typeOrder(left)
    val rightType = typeOrder(right)
    if (leftType != rightType) {
      return false
    }

    return when (leftType) {
      TYPE_ORDER_NULL -> null
      TYPE_ORDER_NUMBER -> strictNumberEquals(left, right)
      TYPE_ORDER_ARRAY -> strictArrayEquals(left, right)
      TYPE_ORDER_VECTOR,
      TYPE_ORDER_MAP -> strictObjectEquals(left, right)
      TYPE_ORDER_SERVER_TIMESTAMP ->
        ServerTimestamps.getLocalWriteTime(left) == ServerTimestamps.getLocalWriteTime(right)
      TYPE_ORDER_MAX_VALUE -> true
      else -> left == right
    }
  }

  fun strictCompare(left: Value, right: Value): Int? {
    val leftType = typeOrder(left)
    val rightType = typeOrder(right)
    if (leftType != rightType) {
      return null
    }
    return compareInternal(leftType, left, right)
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

  private fun strictNumberEquals(left: Value, right: Value): Boolean {
    if (left.doubleValue.isNaN() || right.doubleValue.isNaN()) return false
    return numberEquals(left, right)
  }

  private fun numberEquals(left: Value, right: Value): Boolean =
    when (left.valueTypeCase) {
      ValueTypeCase.INTEGER_VALUE ->
        when (right.valueTypeCase) {
          ValueTypeCase.INTEGER_VALUE -> left.integerValue == right.integerValue
          ValueTypeCase.DOUBLE_VALUE ->
            firestoreCompareDoubleWithLong(right.doubleValue, left.integerValue) == 0
          else -> false
        }
      ValueTypeCase.DOUBLE_VALUE ->
        when (right.valueTypeCase) {
          ValueTypeCase.INTEGER_VALUE ->
            firestoreCompareDoubleWithLong(left.doubleValue, right.integerValue) == 0
          ValueTypeCase.DOUBLE_VALUE ->
            firestoreCompareDoubles(left.doubleValue, right.doubleValue) == 0
          else -> false
        }
      else -> false
    }

    private fun strictArrayEquals(left: Value, right: Value): Boolean? {
        val leftArray = left.arrayValue
        val rightArray = right.arrayValue

        if (leftArray.valuesCount != rightArray.valuesCount) {
            return false
        }

        var foundNull = false
        for (i in 0 until leftArray.valuesCount) {
            val equals = strictEquals(leftArray.getValues(i), rightArray.getValues(i))
            if (equals === null) {
                foundNull = true
            } else if (!equals) {
                return false
            }
        }
        return if (foundNull) null else true
    }

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

  private fun strictObjectEquals(left: Value, right: Value): Boolean? {
    val leftMap = left.mapValue
    val rightMap = right.mapValue

    if (leftMap.fieldsCount != rightMap.fieldsCount) {
      return false
    }

    var foundNull = false
    for ((key, value) in leftMap.fieldsMap) {
      val otherEntry = rightMap.fieldsMap[key] ?: return false
      val equals = strictEquals(value, otherEntry)
      if (equals === null) {
        foundNull = true
      } else if (!equals) {
        return false
      }
    }

    return if (foundNull) null else true
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
  fun compare(left: Value, right: Value): Int {
    val leftType = typeOrder(left)
    val rightType = typeOrder(right)

    if (leftType != rightType) {
      return leftType.compareTo(rightType)
    }

    return compareInternal(leftType, left, right)
  }

  private fun compareInternal(leftType: Int, left: Value, right: Value): Int =
    when (leftType) {
      TYPE_ORDER_NULL,
      TYPE_ORDER_MAX_VALUE -> 0
      TYPE_ORDER_BOOLEAN -> left.booleanValue.compareTo(right.booleanValue)
      TYPE_ORDER_NUMBER -> compareNumbers(left, right)
      TYPE_ORDER_TIMESTAMP -> compareTimestamps(left.timestampValue, right.timestampValue)
      TYPE_ORDER_SERVER_TIMESTAMP ->
        compareTimestamps(
          ServerTimestamps.getLocalWriteTime(left),
          ServerTimestamps.getLocalWriteTime(right)
        )
      TYPE_ORDER_STRING -> Util.compareUtf8Strings(left.stringValue, right.stringValue)
      TYPE_ORDER_BLOB -> Util.compareByteStrings(left.bytesValue, right.bytesValue)
      TYPE_ORDER_REFERENCE -> compareReferences(left.referenceValue, right.referenceValue)
      TYPE_ORDER_GEOPOINT -> compareGeoPoints(left.geoPointValue, right.geoPointValue)
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
    if (left.hasDoubleValue()) {
      if (right.hasDoubleValue()) {
        return firestoreCompareDoubles(left.doubleValue, right.doubleValue)
      } else if (right.hasIntegerValue()) {
        return firestoreCompareDoubleWithLong(left.doubleValue, right.integerValue)
      }
    } else if (left.hasIntegerValue()) {
      if (right.hasIntegerValue()) {
        return java.lang.Long.compare(left.integerValue, right.integerValue)
      } else if (right.hasDoubleValue()) {
        return -1 * firestoreCompareDoubleWithLong(right.doubleValue, left.integerValue)
      }
    }

    throw Assert.fail("Unexpected values: %s vs %s", left, right)
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
    return when (value.valueTypeCase) {
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
      // VectorValue sorts after ArrayValue and before an empty MapValue
      ValueTypeCase.MAP_VALUE -> if (isVectorValue(value)) MIN_VECTOR_VALUE else MIN_MAP
      else -> throw IllegalArgumentException("Unknown value type: " + value.valueTypeCase)
    }
  }

  /** Returns the largest value for the given value type (exclusive). */
  @JvmStatic
  fun getUpperBound(value: Value): Value {
    return when (value.valueTypeCase) {
      ValueTypeCase.NULL_VALUE -> MIN_BOOLEAN
      ValueTypeCase.BOOLEAN_VALUE -> MIN_NUMBER
      ValueTypeCase.INTEGER_VALUE,
      ValueTypeCase.DOUBLE_VALUE -> MIN_TIMESTAMP
      ValueTypeCase.TIMESTAMP_VALUE -> MIN_STRING
      ValueTypeCase.STRING_VALUE -> MIN_BYTES
      ValueTypeCase.BYTES_VALUE -> MIN_REFERENCE
      ValueTypeCase.REFERENCE_VALUE -> MIN_GEO_POINT
      ValueTypeCase.GEO_POINT_VALUE -> MIN_ARRAY
      ValueTypeCase.ARRAY_VALUE -> MIN_VECTOR_VALUE
      // VectorValue sorts after ArrayValue and before an empty MapValue
      ValueTypeCase.MAP_VALUE -> if (isVectorValue(value)) MIN_MAP else MAX_VALUE
      else -> throw IllegalArgumentException("Unknown value type: " + value.valueTypeCase)
    }
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

  @JvmStatic fun encodeValue(date: Date): Value = encodeValue(com.google.firebase.Timestamp((date)))

  @JvmStatic
  fun encodeValue(timestamp: com.google.firebase.Timestamp): Value =
    encodeValue(timestamp(timestamp.seconds, timestamp.nanoseconds))

  @JvmStatic fun encodeValue(value: Timestamp): Value = Value.newBuilder().setTimestampValue(value).build()

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
}
