// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.index;

import static com.google.firebase.firestore.model.Values.NULL_VALUE;

import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Firestore index value writer. */
public class FirestoreIndexValueWriter {
  // Note: This file is copied from the backend. Code that is not used by
  // Firestore was removed. Code that has different behavior was modified.

  // The client SDK only supports references to documents from the same database. We can skip the
  // first five segments.
  public static final int DOCUMENT_NAME_OFFSET = 5;

  public static final int INDEX_TYPE_NULL = 5;
  public static final int INDEX_TYPE_MIN_KEY = 7;
  public static final int INDEX_TYPE_BOOLEAN = 10;
  public static final int INDEX_TYPE_NAN = 13;
  public static final int INDEX_TYPE_NUMBER = 15;
  public static final int INDEX_TYPE_TIMESTAMP = 20;
  public static final int INDEX_TYPE_BSON_TIMESTAMP = 22;
  public static final int INDEX_TYPE_STRING = 25;
  public static final int INDEX_TYPE_BLOB = 30;
  public static final int INDEX_TYPE_BSON_BINARY = 31;
  public static final int INDEX_TYPE_REFERENCE = 37;
  public static final int INDEX_TYPE_BSON_OBJECT_ID = 43;
  public static final int INDEX_TYPE_GEOPOINT = 45;
  public static final int INDEX_TYPE_REGEX = 47;
  public static final int INDEX_TYPE_ARRAY = 50;
  public static final int INDEX_TYPE_VECTOR = 53;
  public static final int INDEX_TYPE_MAP = 55;
  public static final int INDEX_TYPE_REFERENCE_SEGMENT = 60;
  public static final int INDEX_TYPE_MAX_KEY = 999;

  // A terminator that indicates that a truncatable value was not truncated.
  // This must be smaller than all other type labels.
  public static final int NOT_TRUNCATED = 2;

  public static final FirestoreIndexValueWriter INSTANCE = new FirestoreIndexValueWriter();

  private FirestoreIndexValueWriter() {}

  /** Writes an index value. */
  public void writeIndexValue(Value value, DirectionalIndexByteEncoder encoder) {
    writeIndexValueAux(value, encoder);
    // Write separator to split index values (see go/firestore-storage-format#encodings).
    encoder.writeInfinity();
  }

  private void writeIndexValueAux(Value indexValue, DirectionalIndexByteEncoder encoder) {
    switch (indexValue.getValueTypeCase()) {
      case NULL_VALUE:
        writeValueTypeLabel(encoder, INDEX_TYPE_NULL);
        break;
      case BOOLEAN_VALUE:
        writeValueTypeLabel(encoder, INDEX_TYPE_BOOLEAN);
        encoder.writeLong(indexValue.getBooleanValue() ? 1 : 0);
        break;
      case DOUBLE_VALUE:
        writeIndexDouble(indexValue.getDoubleValue(), encoder);
        break;
      case INTEGER_VALUE:
        writeValueTypeLabel(encoder, INDEX_TYPE_NUMBER);
        // Double and long sort the same
        encoder.writeDouble(indexValue.getIntegerValue());
        break;
      case TIMESTAMP_VALUE:
        writeIndexTimestamp(indexValue.getTimestampValue(), encoder);
        break;
      case STRING_VALUE:
        writeIndexString(indexValue.getStringValue(), encoder);
        writeTruncationMarker(encoder);
        break;
      case BYTES_VALUE:
        writeValueTypeLabel(encoder, INDEX_TYPE_BLOB);
        encoder.writeBytes(indexValue.getBytesValue());
        writeTruncationMarker(encoder);
        break;
      case REFERENCE_VALUE:
        writeIndexEntityRef(indexValue.getReferenceValue(), encoder);
        break;
      case GEO_POINT_VALUE:
        writeIndexGeoPoint(indexValue.getGeoPointValue(), encoder);
        break;
      case MAP_VALUE:
        Values.MapRepresentation mapType = Values.detectMapRepresentation(indexValue);
        switch (mapType) {
          case INTERNAL_MAX:
            writeValueTypeLabel(encoder, Integer.MAX_VALUE);
            break;
          case VECTOR:
            writeIndexVector(indexValue.getMapValue(), encoder);
            break;
          case REGEX:
            writeIndexRegex(indexValue.getMapValue(), encoder);
            break;
          case BSON_TIMESTAMP:
            writeIndexBsonTimestamp(indexValue.getMapValue(), encoder);
            break;
          case BSON_OBJECT_ID:
            writeIndexBsonObjectId(indexValue.getMapValue(), encoder);
            break;
          case BSON_BINARY:
            writeIndexBsonBinaryData(indexValue.getMapValue(), encoder);
            break;
          case INT32:
            writeIndexInt32(indexValue.getMapValue(), encoder);
            break;
          case DECIMAL128:
            // Double and Decimal128 sort the same
            // Decimal128 is written as double with precision lost
            double number =
                Double.parseDouble(
                    indexValue
                        .getMapValue()
                        .getFieldsMap()
                        .get(Values.RESERVED_DECIMAL128_KEY)
                        .getStringValue());
            writeIndexDouble(number, encoder);
            break;
          case MIN_KEY:
            writeValueTypeLabel(encoder, INDEX_TYPE_MIN_KEY);
            break;
          case MAX_KEY:
            writeValueTypeLabel(encoder, INDEX_TYPE_MAX_KEY);
            break;
          default:
            writeIndexMap(indexValue.getMapValue(), encoder);
            writeTruncationMarker(encoder);
        }
        break;
      case ARRAY_VALUE:
        writeIndexArray(indexValue.getArrayValue(), encoder);
        writeTruncationMarker(encoder);
        break;
      default:
        throw new IllegalArgumentException(
            "unknown index value type " + indexValue.getValueTypeCase());
    }
  }

  private void writeIndexString(String stringIndexValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_STRING);
    writeUnlabeledIndexString(stringIndexValue, encoder);
  }

  private void writeUnlabeledIndexString(
      String stringIndexValue, DirectionalIndexByteEncoder encoder) {
    encoder.writeString(stringIndexValue);
  }

  private void writeIndexDouble(double number, DirectionalIndexByteEncoder encoder) {
    if (Double.isNaN(number)) {
      writeValueTypeLabel(encoder, INDEX_TYPE_NAN);
      return;
    }
    writeValueTypeLabel(encoder, INDEX_TYPE_NUMBER);
    if (number == -0.0) {
      encoder.writeDouble(0.0); // -0.0, 0 and 0.0 are all considered the same
    } else {
      encoder.writeDouble(number);
    }
  }

  private void writeIndexInt32(MapValue mapValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_NUMBER);
    // Double and Int32 sort the same
    encoder.writeDouble(mapValue.getFieldsMap().get(Values.RESERVED_INT32_KEY).getIntegerValue());
  }

  private void writeIndexTimestamp(Timestamp timestamp, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_TIMESTAMP);
    encoder.writeLong(timestamp.getSeconds());
    encoder.writeLong(timestamp.getNanos());
  }

  private void writeIndexGeoPoint(LatLng geoPoint, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_GEOPOINT);
    encoder.writeDouble(geoPoint.getLatitude());
    encoder.writeDouble(geoPoint.getLongitude());
  }

  private void writeIndexVector(MapValue mapIndexValue, DirectionalIndexByteEncoder encoder) {
    Map<String, Value> map = mapIndexValue.getFieldsMap();
    String key = Values.VECTOR_MAP_VECTORS_KEY;
    writeValueTypeLabel(encoder, INDEX_TYPE_VECTOR);

    // Vectors sort first by length
    int length = map.get(key).getArrayValue().getValuesCount();
    writeValueTypeLabel(encoder, INDEX_TYPE_NUMBER);
    encoder.writeLong(length);

    // Vectors then sort by position value
    this.writeIndexString(key, encoder);
    this.writeIndexValueAux(map.get(key), encoder);
  }

  private void writeIndexRegex(MapValue mapIndexValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_REGEX);

    Map<String, Value> fields =
        mapIndexValue.getFieldsMap().get(Values.RESERVED_REGEX_KEY).getMapValue().getFieldsMap();
    encoder.writeString(fields.get(Values.RESERVED_REGEX_PATTERN_KEY).getStringValue());
    encoder.writeString(fields.get(Values.RESERVED_REGEX_OPTIONS_KEY).getStringValue());
    writeTruncationMarker(encoder);
  }

  private void writeIndexBsonTimestamp(MapValue mapValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_BSON_TIMESTAMP);

    Map<String, Value> timestampFields =
        mapValue
            .getFieldsMap()
            .get(Values.RESERVED_BSON_TIMESTAMP_KEY)
            .getMapValue()
            .getFieldsMap();

    long unsignedSeconds =
        timestampFields.get(Values.RESERVED_BSON_TIMESTAMP_SECONDS_KEY).getIntegerValue();
    long unsignedIncrement =
        timestampFields.get(Values.RESERVED_BSON_TIMESTAMP_INCREMENT_KEY).getIntegerValue();

    // BSON Timestamps are encoded as a 64-bit long with the lower 32 bits being the increment
    // and the upper 32 bits being the seconds
    long value = (unsignedSeconds << 32) | (unsignedIncrement & 0xFFFFFFFFL);

    encoder.writeLong(value);
  }

  private void writeIndexBsonObjectId(MapValue mapValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_BSON_OBJECT_ID);

    String oid = mapValue.getFieldsMap().get(Values.RESERVED_OBJECT_ID_KEY).getStringValue();
    encoder.writeBytes(ByteString.copyFrom(oid.getBytes()));
  }

  private void writeIndexBsonBinaryData(MapValue mapValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_BSON_BINARY);

    encoder.writeBytes(
        mapValue.getFieldsMap().get(Values.RESERVED_BSON_BINARY_KEY).getBytesValue());
    writeTruncationMarker(encoder);
  }

  private void writeIndexMap(MapValue mapIndexValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_MAP);
    for (Map.Entry<String, Value> entry : mapIndexValue.getFieldsMap().entrySet()) {
      String key = entry.getKey();
      Value value = entry.getValue();
      writeIndexString(key, encoder);
      writeIndexValueAux(value, encoder);
    }
  }

  private void writeIndexArray(ArrayValue arrayIndexValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_ARRAY);
    for (Value element : arrayIndexValue.getValuesList()) {
      writeIndexValueAux(element, encoder);
    }
  }

  private void writeIndexEntityRef(String referenceValue, DirectionalIndexByteEncoder encoder) {
    writeValueTypeLabel(encoder, INDEX_TYPE_REFERENCE);

    List<String> segments = new ArrayList<>();
    String[] parts = referenceValue.split("/");
    for (String part : parts) {
      if (!part.isEmpty()) {
        segments.add(part);
      }
    }
    ResourcePath path = ResourcePath.fromSegments(segments);

    int numSegments = path.length();
    for (int index = DOCUMENT_NAME_OFFSET; index < numSegments; ++index) {
      String segment = path.getSegment(index);
      writeValueTypeLabel(encoder, INDEX_TYPE_REFERENCE_SEGMENT);
      writeUnlabeledIndexString(segment, encoder);
    }
  }

  private void writeValueTypeLabel(DirectionalIndexByteEncoder encoder, int typeOrder) {
    encoder.writeLong(typeOrder);
  }

  private void writeTruncationMarker(DirectionalIndexByteEncoder encoder) {
    // While the SDK does not implement truncation, the truncation marker is used to terminate
    // all variable length values (which are strings, bytes, references, arrays and maps).
    encoder.writeLong(NOT_TRUNCATED);
  }
}
