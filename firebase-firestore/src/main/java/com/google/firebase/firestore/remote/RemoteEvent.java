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

import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Map;
import java.util.Set;

/**
 * An event from the RemoteStore. It is split into targetChanges (changes to the state or the set of
 * documents in our watched targets) and documentUpdates (changes to the actual documents).
 */
public final class RemoteEvent {
  private final SnapshotVersion snapshotVersion;
  private final Map<Integer, TargetChange> targetChanges;
  private final Map<Integer, QueryPurpose> targetMismatches;
  private final Map<DocumentKey, MutableDocument> documentUpdates;
  private final Set<DocumentKey> resolvedLimboDocuments;

  public RemoteEvent(
      SnapshotVersion snapshotVersion,
      Map<Integer, TargetChange> targetChanges,
      Map<Integer, QueryPurpose> targetMismatches,
      Map<DocumentKey, MutableDocument> documentUpdates,
      Set<DocumentKey> resolvedLimboDocuments) {
    this.snapshotVersion = snapshotVersion;
    this.targetChanges = targetChanges;
    this.targetMismatches = targetMismatches;
    this.documentUpdates = documentUpdates;
    this.resolvedLimboDocuments = resolvedLimboDocuments;
  }

  /** Returns the snapshot version this event brings us up to. */
  public SnapshotVersion getSnapshotVersion() {
    return snapshotVersion;
  }

  /** Returns a map from target to changes to the target. */
  public Map<Integer, TargetChange> getTargetChanges() {
    return targetChanges;
  }

  /**
   * Returns a map of targets that is known to be inconsistent, and the purpose for re-listening.
   * Listens for these targets should be re-established without resume tokens.
   */
  public Map<Integer, QueryPurpose> getTargetMismatches() {
    return targetMismatches;
  }

  /**
   * Returns a set of which documents have changed or been deleted, along with the doc's new values
   * (if not deleted).
   */
  public Map<DocumentKey, MutableDocument> getDocumentUpdates() {
    return documentUpdates;
  }

  /** Returns the set of document updates that are due only to limbo resolution targets. */
  public Set<DocumentKey> getResolvedLimboDocuments() {
    return resolvedLimboDocuments;
  }

  @Override
  public String toString() {
    return "RemoteEvent{"
        + "snapshotVersion="
        + snapshotVersion
        + ", targetChanges="
        + targetChanges
        + ", targetMismatches="
        + targetMismatches
        + ", documentUpdates="
        + documentUpdates
        + ", resolvedLimboDocuments="
        + resolvedLimboDocuments
        + '}';
  }
}
