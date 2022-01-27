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

import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;
import java.util.Map;

/** Firestore index value writer. */
public class FirestoreIndexValueWriter {
  // Note: This code is copied from the backend. Code that is not used by Firestore was removed.

  // The client SDK only supports references to documents from the same database. We can skip the
  // first five segments.
  public static final int DOCUMENT_NAME_OFFSET = 5;

  public static final int INDEX_TYPE_NULL = 5;
  public static final int INDEX_TYPE_BOOLEAN = 10;
  public static final int INDEX_TYPE_NAN = 13;
  public static final int INDEX_TYPE_NUMBER = 15;
  public static final int INDEX_TYPE_TIMESTAMP = 20;
  public static final int INDEX_TYPE_STRING = 25;
  public static final int INDEX_TYPE_BLOB = 30;
  public static final int INDEX_TYPE_REFERENCE = 37;
  public static final int INDEX_TYPE_GEOPOINT = 45;
  public static final int INDEX_TYPE_ARRAY = 50;
  public static final int INDEX_TYPE_MAP = 55;
  public static final int INDEX_TYPE_REFERENCE_SEGMENT = 60;

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
        double number = indexValue.getDoubleValue();
        if (Double.isNaN(number)) {
          writeValueTypeLabel(encoder, INDEX_TYPE_NAN);
          break;
        }
        writeValueTypeLabel(encoder, INDEX_TYPE_NUMBER);
        if (number == -0.0) {
          encoder.writeDouble(0.0); // -0.0, 0 and 0.0 are all considered the same
        } else {
          encoder.writeDouble(number);
        }
        break;
      case INTEGER_VALUE:
        writeValueTypeLabel(encoder, INDEX_TYPE_NUMBER);
        // Double and long sort the same
        encoder.writeDouble(indexValue.getIntegerValue());
        break;
      case TIMESTAMP_VALUE:
        Timestamp timestamp = indexValue.getTimestampValue();
        writeValueTypeLabel(encoder, INDEX_TYPE_TIMESTAMP);
        encoder.writeLong(timestamp.getSeconds());
        encoder.writeLong(timestamp.getNanos());
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
        LatLng geoPoint = indexValue.getGeoPointValue();
        writeValueTypeLabel(encoder, INDEX_TYPE_GEOPOINT);
        encoder.writeDouble(geoPoint.getLatitude());
        encoder.writeDouble(geoPoint.getLongitude());
        break;
      case MAP_VALUE:
        if (Values.isMaxValue(indexValue)) {
          writeValueTypeLabel(encoder, Integer.MAX_VALUE);
          break;
        }
        writeIndexMap(indexValue.getMapValue(), encoder);
        writeTruncationMarker(encoder);
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

    ResourcePath path = ResourcePath.fromString(referenceValue);
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
