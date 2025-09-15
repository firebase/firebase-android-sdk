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

package com.google.firebase.firestore.remote;

import com.google.firebase.database.collection.ImmutableHashSet;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.protobuf.ByteString;

/**
 * A TargetChange specifies the set of changes for a specific target as part of a RemoteEvent. These
 * changes track which documents are added, modified or removed, as well as the target's resume
 * token and whether the target is marked CURRENT. The actual changes *to* documents are not part of
 * the TargetChange since documents may be part of multiple targets.
 */
public final class TargetChange {
  private final ByteString resumeToken;
  private final boolean current;
  private final ImmutableHashSet<DocumentKey> addedDocuments;
  private final ImmutableHashSet<DocumentKey> modifiedDocuments;
  private final ImmutableHashSet<DocumentKey> removedDocuments;

  public static TargetChange createSynthesizedTargetChangeForCurrentChange(
      boolean isCurrent, ByteString resumeToken) {
    return new TargetChange(
        resumeToken,
        isCurrent,
        ImmutableHashSet.emptySet(),
        ImmutableHashSet.emptySet(),
        ImmutableHashSet.emptySet());
  }

  public TargetChange(
      ByteString resumeToken,
      boolean current,
      ImmutableHashSet<DocumentKey> addedDocuments,
      ImmutableHashSet<DocumentKey> modifiedDocuments,
      ImmutableHashSet<DocumentKey> removedDocuments) {
    this.resumeToken = resumeToken;
    this.current = current;
    this.addedDocuments = addedDocuments;
    this.modifiedDocuments = modifiedDocuments;
    this.removedDocuments = removedDocuments;
  }

  /**
   * Returns the opaque, server-assigned token that allows watching a query to be resumed after
   * disconnecting without retransmitting all the data that matches the query. The resume token
   * essentially identifies a point in time from which the server should resume sending results.
   */
  public ByteString getResumeToken() {
    return resumeToken;
  }

  /**
   * Returns the "current" (synced) status of this target. Note that "current" has special meaning
   * in the RPC protocol that implies that a target is both up-to-date and consistent with the rest
   * of the watch stream.
   */
  public boolean isCurrent() {
    return current;
  }

  /**
   * Returns the set of documents that were newly assigned to this target as part of this remote
   * event.
   */
  public ImmutableHashSet<DocumentKey> getAddedDocuments() {
    return addedDocuments;
  }

  /**
   * Returns the set of documents that were already assigned to this target but received an update
   * during this remote event.
   */
  public ImmutableHashSet<DocumentKey> getModifiedDocuments() {
    return modifiedDocuments;
  }

  /**
   * Returns the set of documents that were removed from this target as part of this remote event.
   */
  public ImmutableHashSet<DocumentKey> getRemovedDocuments() {
    return removedDocuments;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TargetChange that = (TargetChange) o;

    if (current != that.current) return false;
    if (!resumeToken.equals(that.resumeToken)) return false;
    if (!addedDocuments.equals(that.addedDocuments)) return false;
    if (!modifiedDocuments.equals(that.modifiedDocuments)) return false;
    return removedDocuments.equals(that.removedDocuments);
  }

  @Override
  public int hashCode() {
    int result = resumeToken.hashCode();
    result = 31 * result + (current ? 1 : 0);
    result = 31 * result + addedDocuments.hashCode();
    result = 31 * result + modifiedDocuments.hashCode();
    result = 31 * result + removedDocuments.hashCode();
    return result;
  }
}
