// Copyright 2025 Google LLC
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

/** Represents a BSON Timestamp type in Firestore documents. */
public final class BsonTimestamp {
  public final long seconds;
  public final long increment;

  /**
   * Constructor that creates a new BSON Timestamp value with the given values.
   *
   * @param seconds An unsigned 32-bit integer value stored as long representing the seconds.
   * @param increment An unsigned 32-bit integer value stored as long representing the increment.
   */
  public BsonTimestamp(long seconds, long increment) {
    if (seconds < 0 || seconds > 4294967295L) {
      throw new IllegalArgumentException(
          String.format(
              "The field 'seconds' value (%s) does not represent an unsigned 32-bit integer.",
              seconds));
    }
    if (increment < 0 || increment > 4294967295L) {
      throw new IllegalArgumentException(
          String.format(
              "The field 'increment' value (%s) does not represent an unsigned 32-bit integer.",
              increment));
    }
    this.seconds = seconds;
    this.increment = increment;
  }

  /**
   * Returns true if this BsonTimestampValue is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this BsonTimestampValue is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BsonTimestamp)) {
      return false;
    }
    BsonTimestamp other = (BsonTimestamp) obj;
    return seconds == other.seconds && increment == other.increment;
  }

  @Override
  public int hashCode() {
    return (int) (31 * seconds + increment);
  }

  @Override
  public String toString() {
    return "BsonTimestampValue{seconds=" + seconds + ", increment=" + increment + "}";
  }
}
