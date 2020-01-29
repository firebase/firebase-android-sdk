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

package com.google.firebase.firestore;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.NullValue;
import com.google.type.LatLng;
import java.util.List;
import java.util.Map;

/** Test helper to create Firestore Value protos from Java types. */
public class Values {

  // TODO(mrschmidt): Move into UserDataConverter
  public static Value valueOf(Object o) {
    if (o instanceof Value) {
      return (Value) o;
    } else if (o == null) {
      return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    } else if (o instanceof Boolean) {
      return Value.newBuilder().setBooleanValue((Boolean) o).build();
    } else if (o instanceof Integer) {
      return Value.newBuilder().setIntegerValue((Integer) o).build();
    } else if (o instanceof Long) {
      return Value.newBuilder().setIntegerValue((Long) o).build();
    } else if (o instanceof Double) {
      return Value.newBuilder().setDoubleValue((Double) o).build();
    } else if (o instanceof Timestamp) {
      Timestamp timestamp = (Timestamp) o;
      return Value.newBuilder()
          .setTimestampValue(
              com.google.protobuf.Timestamp.newBuilder()
                  .setSeconds(timestamp.getSeconds())
                  .setNanos(timestamp.getNanoseconds()))
          .build();
    } else if (o instanceof String) {
      return Value.newBuilder().setStringValue((String) o).build();
    } else if (o instanceof Blob) {
      return Value.newBuilder().setBytesValue(((Blob) o).toByteString()).build();
    } else if (o instanceof DocumentReference) {
      return Value.newBuilder()
          .setReferenceValue(
              "projects/project/databases/(default)/documents/" + ((DocumentReference) o).getPath())
          .build();
    } else if (o instanceof GeoPoint) {
      GeoPoint geoPoint = (GeoPoint) o;
      return Value.newBuilder()
          .setGeoPointValue(
              LatLng.newBuilder()
                  .setLatitude(geoPoint.getLatitude())
                  .setLongitude(geoPoint.getLongitude()))
          .build();

    } else if (o instanceof List) {
      ArrayValue.Builder list = ArrayValue.newBuilder();
      for (Object element : (List) o) {
        list.addValues(valueOf(element));
      }
      return Value.newBuilder().setArrayValue(list).build();
    } else if (o instanceof Map) {
      MapValue.Builder builder = MapValue.newBuilder();
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) o).entrySet()) {
        builder.putFields(entry.getKey(), valueOf(entry.getValue()));
      }
      return Value.newBuilder().setMapValue(builder).build();
    }

    throw new UnsupportedOperationException("Failed to serialize object: " + o);
  }

  /** Creates a MapValue from a list of key/value arguments. */
  public static Value map(Object... entries) {
    MapValue.Builder builder = MapValue.newBuilder();
    for (int i = 0; i < entries.length; i += 2) {
      builder.putFields((String) entries[i], valueOf(entries[i + 1]));
    }
    return Value.newBuilder().setMapValue(builder).build();
  }

  public static Value refValue(DatabaseId dbId, DocumentKey key) {
    return Value.newBuilder()
        .setReferenceValue(
            String.format(
                "projects/%s/databases/%s/documents/%s",
                dbId.getProjectId(), dbId.getDatabaseId(), key.toString()))
        .build();
  }
}
