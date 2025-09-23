/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore.model

import com.google.firebase.Timestamp
import com.google.firestore.v1.ArrayValue
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value
import com.google.protobuf.ByteString
import com.google.protobuf.NullValue
import com.google.type.LatLng
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal object DocumentDecoder {

  class DocumentDecodeException(message: String) : Exception(message)

  fun decode(bytes: ByteArray): MutableDocument = decodeFrom(ByteArrayInputStream(bytes))

  fun decodeFrom(stream: InputStream): MutableDocument = decodeFrom(DataInputStream(stream))

  fun decodeFrom(stream: DataInputStream): MutableDocument {
    val documentKey = decodeDocumentKey(stream)
    val version = decodeSnapshotVersion(stream)
    val hasCommittedMutations = stream.readBoolean()

    val documentType = DocumentCodecDocumentType.decodeFrom(stream)
    val mutableDocument: MutableDocument =
      when (documentType) {
        DocumentCodecDocumentType.NO_DOCUMENT -> MutableDocument.newNoDocument(documentKey, version)
        DocumentCodecDocumentType.UNKNOWN_DOCUMENT ->
          MutableDocument.newUnknownDocument(documentKey, version)
        DocumentCodecDocumentType.FOUND_DOCUMENT -> {
          val value = decodeObjectValue(stream)
          MutableDocument.newFoundDocument(documentKey, version, value)
        }
      }

    if (hasCommittedMutations) {
      mutableDocument.setHasCommittedMutations()
    }

    return mutableDocument
  }

  private fun decodeDocumentKey(stream: DataInputStream): DocumentKey {
    val segmentCount = stream.readInt()
    val segments = mutableListOf<String>()
    repeat(segmentCount) { segments.add(decodeString(stream)) }
    return DocumentKey.fromPath(ResourcePath.fromSegments(segments))
  }

  private fun decodeSnapshotVersion(stream: DataInputStream): SnapshotVersion {
    val timestamp = decodeTimestamp(stream)
    return SnapshotVersion(timestamp)
  }

  private fun decodeTimestamp(stream: DataInputStream): Timestamp {
    val seconds = stream.readLong()
    val nanoseconds = stream.readInt()
    return Timestamp(seconds, nanoseconds)
  }

  private fun decodeString(stream: DataInputStream): String {
    val length = stream.readInt()
    val chars = CharArray(length)
    repeat(length) { chars[it] = stream.readChar() }
    return String(chars)
  }

  private fun decodeBytes(stream: DataInputStream): ByteString {
    val length = stream.readInt()
    val bytes = ByteArray(length)
    stream.readFully(bytes)
    return ByteString.copyFrom(bytes)
  }

  private fun decodeObjectValue(stream: DataInputStream): ObjectValue {
    val mapValue = decodeMapValue(stream)
    return ObjectValue.fromMapValue(mapValue)
  }

  private fun decodeMapValue(stream: DataInputStream): MapValue {
    val entryCount = stream.readInt()
    val mapBuilder = MapValue.newBuilder()
    repeat(entryCount) {
      val key = decodeString(stream)
      val value = decodeValue(stream)
      mapBuilder.putFields(key, value)
    }
    return mapBuilder.build()
  }

  private fun decodeValue(stream: DataInputStream): Value {
    val valueType = DocumentCodecValueType.decodeFrom(stream)
    val builder = Value.newBuilder()
    when (valueType) {
      DocumentCodecValueType.NULL_VALUE -> builder.setNullValue(NullValue.NULL_VALUE)
      DocumentCodecValueType.BOOLEAN_VALUE -> builder.setBooleanValue(stream.readBoolean())
      DocumentCodecValueType.INTEGER_VALUE -> builder.setIntegerValue(stream.readLong())
      DocumentCodecValueType.DOUBLE_VALUE -> builder.setDoubleValue(stream.readDouble())
      DocumentCodecValueType.TIMESTAMP_VALUE -> {
        val seconds = stream.readLong()
        val nanos = stream.readInt()
        val timestamp =
          com.google.protobuf.Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos)
        builder.setTimestampValue(timestamp)
      }
      DocumentCodecValueType.STRING_VALUE -> {
        val string = decodeString(stream)
        builder.setStringValue(string)
      }
      DocumentCodecValueType.BYTES_VALUE -> {
        val bytes = decodeBytes(stream)
        builder.setBytesValue(bytes)
      }
      DocumentCodecValueType.REFERENCE_VALUE -> {
        val reference = decodeString(stream)
        builder.setReferenceValue(reference)
      }
      DocumentCodecValueType.GEO_POINT_VALUE -> {
        val latitude = stream.readDouble()
        val longitude = stream.readDouble()
        val geoPoint = LatLng.newBuilder().setLatitude(latitude).setLongitude(longitude)
        builder.setGeoPointValue(geoPoint)
      }
      DocumentCodecValueType.ARRAY_VALUE -> {
        val arrayLength = stream.readInt()
        val arrayBuilder = ArrayValue.newBuilder()
        repeat(arrayLength) {
          val value = decodeValue(stream)
          arrayBuilder.addValues(value)
        }
        builder.setArrayValue(arrayBuilder)
      }
      DocumentCodecValueType.MAP_VALUE -> {
        val mapValue = decodeMapValue(stream)
        builder.setMapValue(mapValue)
      }
    }

    return builder.build()
  }
}

internal object DocumentEncoder {

  class DocumentEncodeException(message: String) : Exception(message)

  fun encode(document: Document): ByteArray =
    ByteArrayOutputStream().also { encodeTo(it, document) }.toByteArray()

  fun encodeTo(outputStream: OutputStream, document: Document) {
    encodeTo(DataOutputStream(outputStream), document)
  }

  fun encodeTo(out: DataOutputStream, document: Document) {
    encodeDocumentKey(out, document.key)
    encodeSnapshotVersion(out, document.version)
    out.writeBoolean(document.hasCommittedMutations())

    if (document.isNoDocument) {
      DocumentCodecDocumentType.NO_DOCUMENT.encodeTo(out)
    } else if (document.isUnknownDocument) {
      DocumentCodecDocumentType.UNKNOWN_DOCUMENT.encodeTo(out)
    } else if (document.isFoundDocument) {
      DocumentCodecDocumentType.FOUND_DOCUMENT.encodeTo(out)
      encodeObjectValue(out, document.data)
    } else {
      throw DocumentEncodeException("unsupported document type")
    }
  }

  private fun encodeDocumentKey(out: DataOutputStream, documentKey: DocumentKey) {
    val segments = documentKey.path.segments
    out.writeInt(segments.size)
    for (segment in segments) {
      encodeString(out, segment)
    }
  }

  private fun encodeSnapshotVersion(out: DataOutputStream, version: SnapshotVersion) {
    encodeTimestamp(out, version.timestamp)
  }

  private fun encodeTimestamp(out: DataOutputStream, timestamp: Timestamp) {
    out.writeLong(timestamp.seconds)
    out.writeInt(timestamp.nanoseconds)
  }

  private fun encodeString(out: DataOutputStream, string: String) {
    out.writeInt(string.length)
    out.writeChars(string)
  }

  private fun encodeObjectValue(out: DataOutputStream, objectValue: ObjectValue) {
    encodeMapValue(out, objectValue.fieldsMap)
  }

  private fun encodeMapValue(out: DataOutputStream, mapValue: Map<String, Value>) {
    out.writeInt(mapValue.size)
    mapValue.forEach { (key, value) ->
      encodeString(out, key)
      encodeValue(out, value)
    }
  }

  private fun encodeValue(out: DataOutputStream, value: Value) {
    val valueType = DocumentCodecValueType.fromValueTypeCase(value.valueTypeCase)
    valueType.encodeTo(out)

    when (valueType) {
      DocumentCodecValueType.NULL_VALUE -> {
        // nothing to write
      }
      DocumentCodecValueType.BOOLEAN_VALUE -> {
        out.writeBoolean(value.booleanValue)
      }
      DocumentCodecValueType.INTEGER_VALUE -> {
        out.writeLong(value.integerValue)
      }
      DocumentCodecValueType.DOUBLE_VALUE -> {
        out.writeDouble(value.doubleValue)
      }
      DocumentCodecValueType.TIMESTAMP_VALUE -> {
        val timestamp = value.timestampValue
        out.writeLong(timestamp.seconds)
        out.writeInt(timestamp.nanos)
      }
      DocumentCodecValueType.STRING_VALUE -> {
        val string = value.stringValue
        out.writeInt(string.length)
        out.writeChars(string)
      }
      DocumentCodecValueType.BYTES_VALUE -> {
        val bytes = value.bytesValue
        out.writeInt(bytes.size())
        bytes.writeTo(out)
      }
      DocumentCodecValueType.REFERENCE_VALUE -> {
        val reference = value.referenceValue
        out.writeInt(reference.length)
        out.writeChars(reference)
      }
      DocumentCodecValueType.GEO_POINT_VALUE -> {
        val geoPoint = value.geoPointValue
        out.writeDouble(geoPoint.latitude)
        out.writeDouble(geoPoint.longitude)
      }
      DocumentCodecValueType.ARRAY_VALUE -> {
        val array = value.arrayValue.valuesList
        out.writeInt(array.size)
        array.forEach { encodeValue(out, it) }
      }
      DocumentCodecValueType.MAP_VALUE -> {
        encodeMapValue(out, value.mapValue.fieldsMap)
      }
    }
  }
}

private enum class DocumentCodecDocumentType(val serializedValue: Byte) {
  NO_DOCUMENT(0),
  UNKNOWN_DOCUMENT(1),
  FOUND_DOCUMENT(2);

  fun encodeTo(stream: DataOutputStream) {
    stream.writeByte(serializedValue.toInt())
  }

  class InvalidDocumentCodecDocumentTypeException(message: String) : Exception(message)

  companion object {
    fun decodeFrom(stream: DataInputStream): DocumentCodecDocumentType {
      val serializedValue = stream.readByte()
      return entries.firstOrNull { it.serializedValue == serializedValue }
        ?: throw InvalidDocumentCodecDocumentTypeException(
          "unknown serialized value: $serializedValue"
        )
    }
  }
}

private enum class DocumentCodecValueType(val serializedValue: Byte) {
  NULL_VALUE(0),
  BOOLEAN_VALUE(1),
  INTEGER_VALUE(2),
  DOUBLE_VALUE(3),
  TIMESTAMP_VALUE(4),
  STRING_VALUE(5),
  BYTES_VALUE(6),
  REFERENCE_VALUE(7),
  GEO_POINT_VALUE(8),
  ARRAY_VALUE(9),
  MAP_VALUE(10);

  fun encodeTo(stream: DataOutputStream) {
    stream.writeByte(serializedValue.toInt())
  }

  class InvalidDocumentCodecValueTypeException(message: String) : Exception(message)

  companion object {
    fun decodeFrom(stream: DataInputStream): DocumentCodecValueType {
      val serializedValue = stream.readByte()
      return entries.firstOrNull { it.serializedValue == serializedValue }
        ?: throw InvalidDocumentCodecValueTypeException(
          "unknown serialized value: $serializedValue"
        )
    }

    fun fromValueTypeCase(valueTypeCase: Value.ValueTypeCase): DocumentCodecValueType =
      when (valueTypeCase) {
        Value.ValueTypeCase.NULL_VALUE -> NULL_VALUE
        Value.ValueTypeCase.BOOLEAN_VALUE -> BOOLEAN_VALUE
        Value.ValueTypeCase.INTEGER_VALUE -> INTEGER_VALUE
        Value.ValueTypeCase.DOUBLE_VALUE -> DOUBLE_VALUE
        Value.ValueTypeCase.TIMESTAMP_VALUE -> TIMESTAMP_VALUE
        Value.ValueTypeCase.STRING_VALUE -> STRING_VALUE
        Value.ValueTypeCase.BYTES_VALUE -> BYTES_VALUE
        Value.ValueTypeCase.REFERENCE_VALUE -> REFERENCE_VALUE
        Value.ValueTypeCase.GEO_POINT_VALUE -> GEO_POINT_VALUE
        Value.ValueTypeCase.ARRAY_VALUE -> ARRAY_VALUE
        Value.ValueTypeCase.MAP_VALUE -> MAP_VALUE
        Value.ValueTypeCase.VALUETYPE_NOT_SET ->
          throw InvalidDocumentCodecValueTypeException(
            "unable to encode value with type=VALUETYPE_NOT_SET"
          )
      }
  }
}
