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

import static com.google.firebase.firestore.util.Assert.fail;

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.DocumentViewChange;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;

/** Tracks the internal state of a Watch target. */
final class TargetState {
  /**
   * The number of outstanding responses (adds or removes) that we are waiting on. We only consider
   * targets active that have no outstanding responses.
   */
  private int outstandingResponses = 0;

  /**
   * Keeps track of the document changes since the last raised snapshot.
   *
   * <p>These changes are continuously updated as we receive document updates and always reflect the
   * current set of changes against the last issued snapshot.
   */
  private final Map<DocumentKey, DocumentViewChange.Type> documentChanges = new HashMap<>();

  /**
   * Whether this target state should be included in the next snapshot. We initialize to true so
   * that newly-added targets are included in the next RemoteEvent.
   */
  private boolean hasChanges = true;

  /** The last resume token sent to us for this target. */
  private ByteString resumeToken = ByteString.EMPTY;

  private boolean current = false;

  /**
   * Whether this target has been marked 'current'.
   *
   * <p>'Current' has special meaning in the RPC protocol: It implies that the Watch backend has
   * sent us all changes up to the point at which the target was added and that the target is
   * consistent with the rest of the watch stream.
   */
  boolean isCurrent() {
    return current;
  }

  /** Whether this target has pending target adds or target removes. */
  boolean isPending() {
    return outstandingResponses != 0;
  }

  /** Whether we have modified any state that should trigger a snapshot. */
  boolean hasChanges() {
    return hasChanges;
  }

  /**
   * Applies the resume token to the TargetChange, but only when it has a new value. Empty
   * resumeTokens are discarded.
   */
  void updateResumeToken(ByteString resumeToken) {
    if (!resumeToken.isEmpty()) {
      hasChanges = true;
      this.resumeToken = resumeToken;
    }
  }

  /**
   * Creates a target change from the current set of changes.
   *
   * <p>To reset the document changes after raising this snapshot, call `clearChanges()`.
   */
  TargetChange toTargetChange() {
    ImmutableSortedSet<DocumentKey> addedDocuments = DocumentKey.emptyKeySet();
    ImmutableSortedSet<DocumentKey> modifiedDocuments = DocumentKey.emptyKeySet();
    ImmutableSortedSet<DocumentKey> removedDocuments = DocumentKey.emptyKeySet();

    for (Map.Entry<DocumentKey, DocumentViewChange.Type> entry : this.documentChanges.entrySet()) {
      DocumentKey key = entry.getKey();
      DocumentViewChange.Type changeType = entry.getValue();
      switch (changeType) {
        case ADDED:
          addedDocuments = addedDocuments.insert(key);
          break;
        case MODIFIED:
          modifiedDocuments = modifiedDocuments.insert(key);
          break;
        case REMOVED:
          removedDocuments = removedDocuments.insert(key);
          break;
        default:
          throw fail("Encountered invalid change type: %s", changeType);
      }
    }

    return new TargetChange(
        resumeToken, current, addedDocuments, modifiedDocuments, removedDocuments);
  }

  /** Resets the document changes and sets `hasPendingChanges` to false. */
  void clearChanges() {
    hasChanges = false;
    documentChanges.clear();
  }

  void addDocumentChange(DocumentKey key, DocumentViewChange.Type changeType) {
    hasChanges = true;
    documentChanges.put(key, changeType);
  }

  void removeDocumentChange(DocumentKey key) {
    hasChanges = true;
    documentChanges.remove(key);
  }

  void recordPendingTargetRequest() {
    ++outstandingResponses;
  }

  void recordTargetResponse() {
    --outstandingResponses;
  }

  void markCurrent() {
    hasChanges = true;
    current = true;
  }
}
