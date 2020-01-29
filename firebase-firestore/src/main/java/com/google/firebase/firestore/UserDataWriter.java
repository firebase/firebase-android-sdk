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

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firebase.firestore.model.value.ReferenceValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;
import com.google.firebase.firestore.model.value.TimestampValue;
import com.google.firebase.firestore.util.Logger;
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

  /** Holds settings that define field value deserialization options. */
  public static class UserDataWriterOptions {
    final DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior;
    final boolean timestampsInSnapshotsEnabled;

    UserDataWriterOptions(
        DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior,
        boolean timestampsInSnapshotsEnabled) {
      this.serverTimestampBehavior = serverTimestampBehavior;
      this.timestampsInSnapshotsEnabled = timestampsInSnapshotsEnabled;
    }
  }

  UserDataWriter(FirebaseFirestore firestore) {
    this.firestore = firestore;
  }

  @Nullable
  Object convertValue(FieldValue value, UserDataWriterOptions options) {
    if (value instanceof ObjectValue) {
      return convertObject((ObjectValue) value, options);
    } else if (value instanceof com.google.firebase.firestore.model.value.ArrayValue) {
      return convertArray((com.google.firebase.firestore.model.value.ArrayValue) value, options);
    } else if (value instanceof ReferenceValue) {
      return convertReference((ReferenceValue) value);
    } else if (value instanceof TimestampValue) {
      return convertTimestamp((TimestampValue) value, options);
    } else if (value instanceof ServerTimestampValue) {
      return convertServerTimestamp((ServerTimestampValue) value, options);
    } else {
      return value.value();
    }
  }

  private Object convertServerTimestamp(ServerTimestampValue value, UserDataWriterOptions options) {
    switch (options.serverTimestampBehavior) {
      case PREVIOUS:
        return value.getPreviousValue();
      case ESTIMATE:
        return value.getLocalWriteTime();
      default:
        return value.value();
    }
  }

  private Object convertTimestamp(TimestampValue value, UserDataWriterOptions options) {
    Timestamp timestamp = value.value();
    if (options.timestampsInSnapshotsEnabled) {
      return timestamp;
    } else {
      return timestamp.toDate();
    }
  }

  private Map<String, Object> convertObject(
      ObjectValue objectValue, UserDataWriterOptions options) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, FieldValue> entry : objectValue.getInternalValue()) {
      result.put(entry.getKey(), convertValue(entry.getValue(), options));
    }
    return result;
  }

  private List<Object> convertArray(ArrayValue arrayValue, UserDataWriterOptions options) {
    ArrayList<Object> result = new ArrayList<>(arrayValue.getInternalValue().size());
    for (FieldValue v : arrayValue.getInternalValue()) {
      result.add(convertValue(v, options));
    }
    return result;
  }

  protected Object convertReference(ReferenceValue value) {
    DocumentKey key = value.value();
    DatabaseId refDatabase = value.getDatabaseId();
    DatabaseId database = this.firestore.getDatabaseId();
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
