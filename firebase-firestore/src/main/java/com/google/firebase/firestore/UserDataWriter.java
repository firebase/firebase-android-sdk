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

import static com.google.firebase.firestore.model.ServerTimestamps.getLocalWriteTime;
import static com.google.firebase.firestore.model.ServerTimestamps.getPreviousValue;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_ARRAY;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_BLOB;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_BOOLEAN;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_GEOPOINT;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_MAP;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_NULL;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_NUMBER;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_REFERENCE;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_SERVER_TIMESTAMP;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_STRING;
import static com.google.firebase.firestore.model.Values.TYPE_ORDER_TIMESTAMP;
import static com.google.firebase.firestore.model.Values.typeOrder;
import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.RestrictTo;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.util.Logger;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Firestore's internal types to the Java API types that we expose to the user.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class UserDataWriter {
  private final FirebaseFirestore firestore;
  private final DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior;

  UserDataWriter(
      FirebaseFirestore firestore,
      DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior) {
    this.firestore = firestore;
    this.serverTimestampBehavior = serverTimestampBehavior;
  }

  Object convertValue(Value value) {
    switch (typeOrder(value)) {
      case TYPE_ORDER_MAP:
        return convertObject(value.getMapValue().getFieldsMap());
      case TYPE_ORDER_ARRAY:
        return convertArray(value.getArrayValue());
      case TYPE_ORDER_REFERENCE:
        return convertReference(value);
      case TYPE_ORDER_TIMESTAMP:
        return convertTimestamp(value.getTimestampValue());
      case TYPE_ORDER_SERVER_TIMESTAMP:
        return convertServerTimestamp(value);
      case TYPE_ORDER_NULL:
        return null;
      case TYPE_ORDER_BOOLEAN:
        return value.getBooleanValue();
      case TYPE_ORDER_NUMBER:
        return value.getValueTypeCase().equals(Value.ValueTypeCase.INTEGER_VALUE)
            ? (Object) value.getIntegerValue() // Cast to Object to prevent type coercion to double
            : (Object) value.getDoubleValue();
      case TYPE_ORDER_STRING:
        return value.getStringValue();
      case TYPE_ORDER_BLOB:
        return Blob.fromByteString(value.getBytesValue());
      case TYPE_ORDER_GEOPOINT:
        return new GeoPoint(
            value.getGeoPointValue().getLatitude(), value.getGeoPointValue().getLongitude());
      default:
        throw fail("Unknown value type: " + value.getValueTypeCase());
    }
  }

  Map<String, Object> convertObject(Map<String, Value> mapValue) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Value> entry : mapValue.entrySet()) {
      result.put(entry.getKey(), convertValue(entry.getValue()));
    }
    return result;
  }

  private Object convertServerTimestamp(Value serverTimestampValue) {
    switch (serverTimestampBehavior) {
      case PREVIOUS:
        Value previousValue = getPreviousValue(serverTimestampValue);
        if (previousValue == null) {
          return null;
        }
        return convertValue(previousValue);
      case ESTIMATE:
        return convertTimestamp(getLocalWriteTime(serverTimestampValue));
      default:
        return null;
    }
  }

  private Object convertTimestamp(com.google.protobuf.Timestamp value) {
    return new Timestamp(value.getSeconds(), value.getNanos());
  }

  private List<Object> convertArray(ArrayValue arrayValue) {
    ArrayList<Object> result = new ArrayList<>(arrayValue.getValuesCount());
    for (Value v : arrayValue.getValuesList()) {
      result.add(convertValue(v));
    }
    return result;
  }

  private Object convertReference(Value value) {
    DatabaseId refDatabase = DatabaseId.fromName(value.getReferenceValue());
    DocumentKey key = DocumentKey.fromName(value.getReferenceValue());
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
