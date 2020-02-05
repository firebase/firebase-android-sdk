// Copyright 2018 Google LLC
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

package com.google.firebase.firestore.model.value;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;

/**
 * Represents a locally-applied Server Timestamp.
 *
 * <p>Server Timestamps are backed by MapValues that contain an internal field `__type__` with a
 * value of `server_timestamp`. The previous value and local write time are stored in its
 * `__previous_value__` and `__local_write_time__` fields respectively.
 *
 * <p>Notes:
 * <li>ServerTimestampValue instances are created as the result of applying a TransformMutation (see
 *     TransformMutation.applyTo()). They can only exist in the local view of a document. Therefore
 *     they do not need to be parsed or serialized.
 * <li>When evaluated locally (e.g. via DocumentSnapshot data), they evaluate to null.
 * <li>They sort after all TimestampValues. With respect to other ServerTimestampValues, they sort
 *     by their localWriteTime.
 */
public final class ServerTimestampValue extends FieldValue {
  private static final String SERVER_TIMESTAMP_SENTINEL = "server_timestamp";
  private static final String TYPE_KEY = "__type__";
  private static final String PREVIOUS_VALUE_KEY = "__previous_value__";
  private static final String LOCAL_WRITE_TIME_KEY = "__local_write_time__";

  public static boolean isServerTimestamp(@Nullable Value value) {
    Value type = value == null ? null : value.getMapValue().getFieldsOrDefault(TYPE_KEY, null);
    return type != null && SERVER_TIMESTAMP_SENTINEL.equals(type.getStringValue());
  }

  ServerTimestampValue(Value value) {
    super(value);
    hardAssert(
        ServerTimestampValue.isServerTimestamp(value),
        "Backing value is not a ServerTimestampValue");
  }

  public static ServerTimestampValue valueOf(Value value) {
    return new ServerTimestampValue(value);
  }

  public static FieldValue valueOf(Timestamp localWriteTime, @Nullable Value previousValue) {
    Value encodedType = Value.newBuilder().setStringValue(SERVER_TIMESTAMP_SENTINEL).build();
    Value encodeWriteTime =
        Value.newBuilder()
            .setTimestampValue(
                com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(localWriteTime.getSeconds())
                    .setNanos(localWriteTime.getNanoseconds()))
            .build();

    MapValue.Builder mapRepresentation =
        MapValue.newBuilder()
            .putFields(TYPE_KEY, encodedType)
            .putFields(LOCAL_WRITE_TIME_KEY, encodeWriteTime);

    if (previousValue != null) {
      mapRepresentation.putFields(PREVIOUS_VALUE_KEY, previousValue);
    }

    return new ServerTimestampValue(Value.newBuilder().setMapValue(mapRepresentation).build());
  }

  /**
   * Returns the value of the field before this ServerTimestamp was set.
   *
   * <p>Preserving the previous values allows the user to display the last resoled value until the
   * backend responds with the timestamp {@link DocumentSnapshot.ServerTimestampBehavior}.
   */
  @Nullable
  public FieldValue getPreviousValue() {
    Value previous = internalValue.getMapValue().getFieldsOrDefault(PREVIOUS_VALUE_KEY, null);

    FieldValue previousValue = FieldValue.valueOf(previous);
    if (previousValue instanceof ServerTimestampValue) {
      return ((ServerTimestampValue) previousValue).getPreviousValue();
    }
    return previousValue;
  }

  public Timestamp getLocalWriteTime() {
    com.google.protobuf.Timestamp localWriteTime =
        internalValue.getMapValue().getFieldsOrThrow(LOCAL_WRITE_TIME_KEY).getTimestampValue();
    return new Timestamp(localWriteTime.getSeconds(), localWriteTime.getNanos());
  }
}
