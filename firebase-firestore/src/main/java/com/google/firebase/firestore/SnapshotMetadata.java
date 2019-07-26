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

package com.google.firebase.firestore;

import androidx.annotation.Nullable;

/**
 * Metadata about a snapshot, describing the state of the snapshot.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class SnapshotMetadata {
  private final boolean hasPendingWrites;
  private final boolean isFromCache;

  SnapshotMetadata(boolean hasPendingWrites, boolean isFromCache) {
    this.hasPendingWrites = hasPendingWrites;
    this.isFromCache = isFromCache;
  }

  /**
   * @return true if the snapshot contains the result of local writes (for example, {@code set()} or
   *     {@code update()} calls) that have not yet been committed to the backend. If your listener
   *     has opted into metadata updates (via {@link MetadataChanges#INCLUDE}) you will receive
   *     another snapshot with {@code hasPendingWrites()} equal to false once the writes have been
   *     committed to the backend.
   */
  public boolean hasPendingWrites() {
    return hasPendingWrites;
  }

  /**
   * @return true if the snapshot was created from cached data rather than guaranteed up-to-date
   *     server data. If your listener has opted into metadata updates (via {@link
   *     MetadataChanges#INCLUDE}) you will receive another snapshot with {@code isFromCache()}
   *     equal to false once the client has received up-to-date data from the backend.
   */
  public boolean isFromCache() {
    return isFromCache;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SnapshotMetadata)) {
      return false;
    }
    SnapshotMetadata other = (SnapshotMetadata) obj;
    return hasPendingWrites == other.hasPendingWrites && isFromCache == other.isFromCache;
  }

  @Override
  public int hashCode() {
    int hash = hasPendingWrites ? 1 : 0;
    hash = hash * 31 + (isFromCache ? 1 : 0);
    return hash;
  }

  @Override
  public String toString() {
    return "SnapshotMetadata{"
        + "hasPendingWrites="
        + hasPendingWrites
        + ", isFromCache="
        + isFromCache
        + '}';
  }
}
