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

package com.google.firebase.firestore.model;

import com.google.firebase.Timestamp;

/**
 * A version of a document in Firestore. This corresponds to the version timestamp, such as
 * update_time or read_time.
 */
public final class SnapshotVersion implements Comparable<SnapshotVersion> {

  /** A version that is smaller than all other versions. */
  public static final SnapshotVersion NONE = new SnapshotVersion(new Timestamp(0L, 0));

  private final Timestamp timestamp;

  /** Creates a new version representing the given timestamp. */
  public SnapshotVersion(Timestamp timestamp) {
    this.timestamp = timestamp;
  }

  public Timestamp getTimestamp() {
    return timestamp;
  }

  @Override
  public int compareTo(SnapshotVersion other) {
    return timestamp.compareTo(other.timestamp);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SnapshotVersion)) {
      return false;
    }
    SnapshotVersion other = (SnapshotVersion) obj;
    return compareTo(other) == 0;
  }

  @Override
  public int hashCode() {
    return getTimestamp().hashCode();
  }

  @Override
  public String toString() {
    return "SnapshotVersion(seconds="
        + timestamp.getSeconds()
        + ", nanos="
        + timestamp.getNanoseconds()
        + ")";
  }
}
