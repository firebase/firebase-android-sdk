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

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.util.Assert;
import javax.annotation.Nullable;

/**
 * Represents a locally-applied Server Timestamp.
 *
 * <p>Notes: - ServerTimestampValue instances are created as the result of applying a
 * TransformMutation (see TransformMutation.applyTo()). They can only exist in the local view of a
 * document. Therefore they do not need to be parsed or serialized. - When evaluated locally (e.g.
 * via DocumentSnapshot data), they evaluate to null. - They sort after all TimestampValues. With
 * respect to other ServerTimestampValues, they sort by their localWriteTime.
 */
public final class ServerTimestampValue extends FieldValue {
  private final Timestamp localWriteTime;
  @Nullable private final FieldValue previousValue;

  public ServerTimestampValue(Timestamp localWriteTime, @Nullable FieldValue previousValue) {
    this.localWriteTime = localWriteTime;
    this.previousValue = previousValue;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_TIMESTAMP;
  }

  @Override
  @Nullable
  public Object value() {
    return null;
  }

  @Override
  @Nullable
  public Object value(FieldValueOptions options) {
    switch (options.getServerTimestampBehavior()) {
      case PREVIOUS:
        return previousValue != null ? previousValue.value(options) : null;
      case ESTIMATE:
        return new TimestampValue(localWriteTime).value(options);
      case NONE:
        return null;
      default:
        throw Assert.fail(
            "Unexpected case for ServerTimestampBehavior: %s",
            options.getServerTimestampBehavior().name());
    }
  }

  @Override
  public String toString() {
    return "<ServerTimestamp localTime=" + localWriteTime.toString() + ">";
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof ServerTimestampValue)
        && localWriteTime.equals(((ServerTimestampValue) o).localWriteTime);
  }

  @Override
  public int hashCode() {
    return localWriteTime.hashCode();
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof ServerTimestampValue) {
      return localWriteTime.compareTo(((ServerTimestampValue) o).localWriteTime);
    } else if (o instanceof TimestampValue) {
      // Server timestamps come after all concrete timestamps.
      return 1;
    } else {
      return defaultCompareTo(o);
    }
  }
}
