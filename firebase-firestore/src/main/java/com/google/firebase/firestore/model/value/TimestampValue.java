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

import androidx.annotation.NonNull;
import com.google.firebase.Timestamp;

/** A wrapper for Date values in Timestamp. */
public final class TimestampValue extends FieldValue {
  private final Timestamp internalValue;

  TimestampValue(Timestamp t) {
    internalValue = t;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_TIMESTAMP;
  }

  @Override
  @NonNull
  public Timestamp value() {
    return internalValue;
  }

  public Timestamp getInternalValue() {
    return internalValue;
  }

  @Override
  public String toString() {
    return internalValue.toString();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof TimestampValue)
        && internalValue.equals(((TimestampValue) o).internalValue);
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof TimestampValue) {
      return internalValue.compareTo(((TimestampValue) o).internalValue);
    } else if (o instanceof ServerTimestampValue) {
      // Concrete timestamps come before server timestamps.
      return -1;
    } else {
      return defaultCompareTo(o);
    }
  }

  public static TimestampValue valueOf(Timestamp t) {
    return new TimestampValue(t);
  }
}
