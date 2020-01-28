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

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.RestrictTo;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ReferenceValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;
import com.google.firebase.firestore.util.Logger;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class UserDataWriter {
  private static UserDataWriter CANONICAL_ID_WRITER = new CanonicalIdWriter();

  private UserDataWriter() {}

  static UserDataWriter forDocumentSnapshots(FirebaseFirestore firestore) {
    return new DocumentSnapshotWriter(firestore);
  }

  public static UserDataWriter forCanonicalIds() {
    return CANONICAL_ID_WRITER;
  }

  /** Holds settings that define field value deserialization options. */
  public static class FieldValueOptions {
    public static final FieldValueOptions DEFAULT =
        new FieldValueOptions(DocumentSnapshot.ServerTimestampBehavior.DEFAULT, true);
    final DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior;
    final boolean timestampsInSnapshotsEnabled;

    FieldValueOptions(
        DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior,
        boolean timestampsInSnapshotsEnabled) {
      this.serverTimestampBehavior = serverTimestampBehavior;
      this.timestampsInSnapshotsEnabled = timestampsInSnapshotsEnabled;
    }
  }

  public Object convertValue(Value value, FieldValueOptions options) {
    switch (value.getValueTypeCase()) {
      case MAP_VALUE:
        if (ServerTimestampValue.isServerTimestamp(value)) {
          return convertServerTimestamp(ServerTimestampValue.valueOf(value), options);
        }
        return convertObject(value.getMapValue().getFieldsMap(), options);
      case ARRAY_VALUE:
        return convertArray(value.getArrayValue(), options);
      case REFERENCE_VALUE:
        return convertReference(value);
      case TIMESTAMP_VALUE:
        return convertTimestamp(value.getTimestampValue(), options);
      case NULL_VALUE:
        return null;
      case BOOLEAN_VALUE:
        return value.getBooleanValue();
      case INTEGER_VALUE:
        return value.getIntegerValue();
      case DOUBLE_VALUE:
        return value.getDoubleValue();
      case STRING_VALUE:
        return value.getStringValue();
      case BYTES_VALUE:
        return Blob.fromByteString(value.getBytesValue());
      case GEO_POINT_VALUE:
        return new GeoPoint(
            value.getGeoPointValue().getLatitude(), value.getGeoPointValue().getLongitude());
      default:
        throw fail("Unknown value type: " + value.getValueTypeCase());
    }
  }

  private Object convertServerTimestamp(ServerTimestampValue value, FieldValueOptions options) {
    switch (options.serverTimestampBehavior) {
      case PREVIOUS:
        return value.getPreviousValue() == null
            ? null
            : convertValue(value.getPreviousValue().getProto(), options);
      case ESTIMATE:
        return !options.timestampsInSnapshotsEnabled
            ? value.getLocalWriteTime().toDate()
            : value.getLocalWriteTime();
      default:
        return null;
    }
  }

  private Object convertTimestamp(com.google.protobuf.Timestamp value, FieldValueOptions options) {
    Timestamp timestamp = new Timestamp(value.getSeconds(), value.getNanos());
    if (options.timestampsInSnapshotsEnabled) {
      return timestamp;
    } else {
      return timestamp.toDate();
    }
  }

  private Map<String, Object> convertObject(
      Map<String, Value> mapValue, FieldValueOptions options) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Value> entry : mapValue.entrySet()) {
      result.put(entry.getKey(), convertValue(entry.getValue(), options));
    }
    return result;
  }

  private List<Object> convertArray(ArrayValue arrayValue, FieldValueOptions options) {
    ArrayList<Object> result = new ArrayList<>(arrayValue.getValuesCount());
    for (Value v : arrayValue.getValuesList()) {
      result.add(convertValue(v, options));
    }
    return result;
  }

  protected abstract Object convertReference(Value value);

  private static class DocumentSnapshotWriter extends UserDataWriter {
    FirebaseFirestore firestore;

    DocumentSnapshotWriter(FirebaseFirestore firestore) {
      this.firestore = firestore;
    }

    @Override
    protected Object convertReference(Value value) {
      FieldValue fieldValue = FieldValue.valueOf(value);
      hardAssert(
          fieldValue instanceof ReferenceValue,
          "FieldValue conversion returned invalid type for %s",
          value);

      DatabaseId refDatabase = ((ReferenceValue) fieldValue).getDatabaseId();
      DocumentKey key = ((ReferenceValue) fieldValue).getKey();
      DatabaseId database = firestore.getDatabaseId();
      if (!refDatabase.equals(database)) {
        // TODO: Somehow support foreign references.
        Logger.warn(
            "DocumentSnapshot",
            "Document %s contains a document reference within a different database "
                + "(%s/%s) which is not supported. It will be treated as a reference in "
                + "the current database (%s/%s) instead.",
            key.getPath(),
            refDatabase.getProjectId(),
            refDatabase.getDatabaseId(),
            database.getProjectId(),
            database.getDatabaseId());
      }
      return new DocumentReference(key, firestore);
    }
  }

  private static class CanonicalIdWriter extends UserDataWriter {
    @Override
    protected Object convertReference(Value value) {
      com.google.firebase.firestore.model.value.FieldValue fieldValue = FieldValue.valueOf(value);
      hardAssert(
          fieldValue instanceof ReferenceValue,
          "FieldValue conversion returned invalid type for %s",
          value);
      return ((ReferenceValue) fieldValue).getKey();
    }
  }
}
