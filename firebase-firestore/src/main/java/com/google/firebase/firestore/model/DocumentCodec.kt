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

internal class DocumentInputStream(val stream: DataInputStream, buffer: ByteArray? = null) {

  private val buffer: ByteArray = run {
    if (buffer === null) {
      ByteArray(128)
    } else {
      require(buffer.size > 16) { "invalid buffer.size: ${buffer.size} (must be at least 16)" }
      require(buffer.size % 2 == 0) {
        "invalid buffer.size: ${buffer.size} (must be a multiple of 2)"
      }
      buffer
    }
  }

  fun close() {
    stream.close()
  }

  class DocumentDecodeException(message: String) : Exception(message)

  fun readDocument(): MutableDocument {
    val documentKey = readDocumentKey()
    val version = readSnapshotVersion()
    val hasCommittedMutations = stream.readBoolean()

    val documentType = DocumentCodecDocumentType.decodeFrom(stream)
    val mutableDocument: MutableDocument =
      when (documentType) {
        DocumentCodecDocumentType.NO_DOCUMENT -> MutableDocument.newNoDocument(documentKey, version)
        DocumentCodecDocumentType.UNKNOWN_DOCUMENT ->
          MutableDocument.newUnknownDocument(documentKey, version)
        DocumentCodecDocumentType.FOUND_DOCUMENT -> {
          val value = readObjectValue()
          MutableDocument.newFoundDocument(documentKey, version, value)
        }
      }

    if (hasCommittedMutations) {
      mutableDocument.setHasCommittedMutations()
    }

    return mutableDocument
  }

  private fun readDocumentKey(): DocumentKey {
    val segmentCount = stream.readInt()
    val segments = mutableListOf<String>()
    repeat(segmentCount) { segments.add(readString()) }
    return DocumentKey.fromPath(ResourcePath.fromSegments(segments))
  }

  private fun readSnapshotVersion(): SnapshotVersion {
    val timestamp = readTimestamp()
    return SnapshotVersion(timestamp)
  }

  private fun readTimestamp(): Timestamp {
    val seconds = stream.readLong()
    val nanoseconds = stream.readInt()
    return Timestamp(seconds, nanoseconds)
  }

  private fun readString(): String {
    val stringLength = stream.readInt()
    val chars = CharArray(stringLength)
    var charsOffset = 0
    while (charsOffset < chars.size) {
      charsOffset = readStringChunk(chars, charsOffset)
    }
    return String(chars)
  }

  private fun readStringChunk(chars: CharArray, charsOffset: Int): Int {
    val desiredByteReadCount = (chars.size - charsOffset) * 2
    val bufferLength = desiredByteReadCount.coerceAtMost(buffer.size)
    // Make absolute sure that the number of bytes to read is a multiple of 2, to avoid the
    // possibility of reading a _partial_ character. With a bit of extra bookkeeping this
    // restriction could be lifted, but, for simplicity, leave the restriction in place.
    check(bufferLength % 2 == 0) { "bufferLength % 2 == ${bufferLength % 2} (expected 2)" }
    stream.readFully(buffer, 0, bufferLength)

    var charsIndex = charsOffset
    var bufferIndex = 0
    while (bufferIndex < bufferLength) {
      val c1 = buffer[bufferIndex++].toInt() shl 8
      val c2 = buffer[bufferIndex++].toInt()
      chars[charsIndex++] = (c1 or c2).toChar()
    }
    return charsIndex
  }

  private fun readBytes(stream: DataInputStream): ByteString {
    val length = stream.readInt()
    val bytes = ByteArray(length)
    stream.readFully(bytes)
    return ByteString.copyFrom(bytes)
  }

  private fun readObjectValue(): ObjectValue {
    val mapValue = readMapValue()
    return ObjectValue.fromMapValue(mapValue)
  }

  private fun readMapValue(): MapValue {
    val entryCount = stream.readInt()
    val mapBuilder = MapValue.newBuilder()
    repeat(entryCount) {
      val key = readString()
      val value = readValue()
      mapBuilder.putFields(key, value)
    }
    return mapBuilder.build()
  }

  private fun readDocumentCodecValueType(): DocumentCodecValueType {
    val serializedValue = stream.readByte()
    return DocumentCodecValueType.entries.firstOrNull { it.serializedValue == serializedValue }
      ?: throw DocumentDecodeException(
        "unknown DocumentCodecValueType serialized value: $serializedValue"
      )
  }

  private fun readValue(): Value {
    val valueType = readDocumentCodecValueType()
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
        builder.setStringValue(readString())
      }
      DocumentCodecValueType.BYTES_VALUE -> {
        val bytes = readBytes(stream)
        builder.setBytesValue(bytes)
      }
      DocumentCodecValueType.REFERENCE_VALUE -> {
        builder.setReferenceValue(readString())
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
        repeat(arrayLength) { arrayBuilder.addValues(readValue()) }
        builder.setArrayValue(arrayBuilder)
      }
      DocumentCodecValueType.MAP_VALUE -> {
        builder.setMapValue(readMapValue())
      }
    }

    return builder.build()
  }

  companion object {

    fun decode(bytes: ByteArray, buffer: ByteArray? = null): MutableDocument =
      decodeFrom(ByteArrayInputStream(bytes), buffer)

    fun decodeFrom(inputStream: InputStream, buffer: ByteArray? = null): MutableDocument =
      decodeFrom(DataInputStream(inputStream), buffer)

    fun decodeFrom(dataInputStream: DataInputStream, buffer: ByteArray? = null): MutableDocument {
      val documentInputStream = DocumentInputStream(dataInputStream, buffer)
      return documentInputStream.readDocument()
    }
  }
}

internal class DocumentOutputStream(val stream: DataOutputStream, buffer: ByteArray? = null) {

  private val buffer: ByteArray = run {
    if (buffer === null) {
      ByteArray(128)
    } else {
      require(buffer.size > 16) { "invalid buffer.size: ${buffer.size} (must be at least 16)" }
      require(buffer.size % 2 == 0) {
        "invalid buffer.size: ${buffer.size} (must be a multiple of 2)"
      }
      buffer
    }
  }

  fun close() {
    stream.close()
  }

  class DocumentEncodeException(message: String) : Exception(message)

  fun writeDocument(document: Document) {
    writeDocumentKey(document.key)
    writeSnapshotVersion(document.version)
    stream.writeBoolean(document.hasCommittedMutations())

    if (document.isNoDocument) {
      writeDocumentCodecDocumentType(DocumentCodecDocumentType.NO_DOCUMENT)
    } else if (document.isUnknownDocument) {
      writeDocumentCodecDocumentType(DocumentCodecDocumentType.UNKNOWN_DOCUMENT)
    } else if (document.isFoundDocument) {
      writeDocumentCodecDocumentType(DocumentCodecDocumentType.FOUND_DOCUMENT)
      writeObjectValue(document.data)
    } else {
      throw DocumentEncodeException("unsupported document type")
    }
  }

  private fun writeDocumentCodecDocumentType(value: DocumentCodecDocumentType) {
    stream.writeByte(value.serializedValue.toInt())
  }

  private fun writeDocumentKey(documentKey: DocumentKey) {
    val segments = documentKey.path.segments
    stream.writeInt(segments.size)
    for (segment in segments) {
      writeString(segment)
    }
  }

  private fun writeSnapshotVersion(version: SnapshotVersion) {
    writeTimestamp(version.timestamp)
  }

  private fun writeTimestamp(timestamp: Timestamp) {
    stream.writeLong(timestamp.seconds)
    stream.writeInt(timestamp.nanoseconds)
  }

  private fun writeString(string: String) {
    stream.writeInt(string.length)
    var stringOffset = 0
    while (stringOffset < string.length) {
      stringOffset = writeStringChunk(string, stringOffset)
    }
  }

  private fun writeStringChunk(string: String, stringOffset: Int): Int {
    var stringIndex = stringOffset
    var bufferIndex = 0
    while (stringIndex < string.length && bufferIndex + 2 < buffer.size) {
      val c = string[stringIndex++].code
      buffer[bufferIndex] = ((c ushr 8) and 0xFF).toByte()
      buffer[bufferIndex + 1] = ((c ushr 0) and 0xFF).toByte()
      bufferIndex += 2
    }
    stream.write(buffer, 0, bufferIndex)
    return stringIndex
  }

  private fun writeObjectValue(objectValue: ObjectValue) {
    writeMapValue(objectValue.fieldsMap)
  }

  private fun writeMapValue(mapValue: Map<String, Value>) {
    stream.writeInt(mapValue.size)
    mapValue.forEach { (key, value) ->
      writeString(key)
      writeValue(value)
    }
  }

  private fun writeDocumentCodecValueType(value: DocumentCodecValueType) {
    stream.writeByte(value.serializedValue.toInt())
  }

  private fun writeValue(value: Value) {
    val valueType = DocumentCodecValueType.fromValueTypeCase(value.valueTypeCase)
    writeDocumentCodecValueType(valueType)

    when (valueType) {
      DocumentCodecValueType.NULL_VALUE -> {
        // nothing to write
      }
      DocumentCodecValueType.BOOLEAN_VALUE -> {
        stream.writeBoolean(value.booleanValue)
      }
      DocumentCodecValueType.INTEGER_VALUE -> {
        stream.writeLong(value.integerValue)
      }
      DocumentCodecValueType.DOUBLE_VALUE -> {
        stream.writeDouble(value.doubleValue)
      }
      DocumentCodecValueType.TIMESTAMP_VALUE -> {
        val timestamp = value.timestampValue
        stream.writeLong(timestamp.seconds)
        stream.writeInt(timestamp.nanos)
      }
      DocumentCodecValueType.STRING_VALUE -> {
        writeString(value.stringValue)
      }
      DocumentCodecValueType.BYTES_VALUE -> {
        val bytes = value.bytesValue
        stream.writeInt(bytes.size())
        bytes.writeTo(stream)
      }
      DocumentCodecValueType.REFERENCE_VALUE -> {
        val reference = value.referenceValue
        stream.writeInt(reference.length)
        stream.writeChars(reference)
      }
      DocumentCodecValueType.GEO_POINT_VALUE -> {
        val geoPoint = value.geoPointValue
        stream.writeDouble(geoPoint.latitude)
        stream.writeDouble(geoPoint.longitude)
      }
      DocumentCodecValueType.ARRAY_VALUE -> {
        val array = value.arrayValue.valuesList
        stream.writeInt(array.size)
        array.forEach { writeValue(it) }
      }
      DocumentCodecValueType.MAP_VALUE -> {
        writeMapValue(value.mapValue.fieldsMap)
      }
    }
  }

  companion object {

    fun encode(document: Document, buffer: ByteArray? = null): ByteArray {
      val byteArrayOutputStream = ByteArrayOutputStream()
      encodeTo(byteArrayOutputStream, document, buffer)
      byteArrayOutputStream.close()
      return byteArrayOutputStream.toByteArray()
    }

    fun encodeTo(outputStream: OutputStream, document: Document, buffer: ByteArray? = null) {
      val dataOutputStream = DataOutputStream(outputStream)
      encodeTo(dataOutputStream, document, buffer)
      dataOutputStream.flush()
    }

    fun encodeTo(
      dataOutputStream: DataOutputStream,
      document: Document,
      buffer: ByteArray? = null
    ) {
      val writer = DocumentOutputStream(dataOutputStream, buffer)
      writer.writeDocument(document)
    }
  }
}

private enum class DocumentCodecDocumentType(val serializedValue: Byte) {
  NO_DOCUMENT(0),
  UNKNOWN_DOCUMENT(1),
  FOUND_DOCUMENT(2);

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

  class InvalidDocumentCodecValueTypeException(message: String) : Exception(message)

  companion object {

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
