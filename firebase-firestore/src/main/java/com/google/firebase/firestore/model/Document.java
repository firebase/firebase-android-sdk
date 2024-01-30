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

import androidx.annotation.Nullable;
import com.google.firestore.v1.Value;
import java.util.Comparator;

/**
 * Represents a document in Firestore with a key, version, data and whether the data has local
 * mutations applied to it.
 */
public interface Document {
  /** A document comparator that returns document by key and key only. */
  Comparator<Document> KEY_COMPARATOR = (left, right) -> left.getKey().compareTo(right.getKey());

  /** The key for this document */
  DocumentKey getKey();

  /**
   * Returns the version of this document if it exists or a version at which this document was
   * guaranteed to not exist.
   */
  SnapshotVersion getVersion();

  /**
   * Returns the timestamp at which this document was read from the remote server. Returns
   * `SnapshotVersion.NONE` for documents created by the user.
   */
  SnapshotVersion getReadTime();

  /**
   * Returns whether this document is valid (specifically, it is an entry in the
   * RemoteDocumentCache, was created by a mutation or read from the backend).
   */
  boolean isValidDocument();

  /** Returns whether the document exists and its data is known at the current version. */
  boolean isFoundDocument();

  /** Returns whether the document is known to not exist at the current version. */
  boolean isNoDocument();

  /** Returns whether the document exists and its data is unknown at the current version. */
  boolean isUnknownDocument();

  /** Returns the underlying data of this document. Returns an empty value if no data exists. */
  ObjectValue getData();

  /** Returns the data of the given path. Returns null if no data exists. */
  @Nullable
  Value getField(FieldPath path);

  /** Returns whether local mutations were applied via the mutation queue. */
  boolean hasLocalMutations();

  /** Returns whether mutations were applied based on a write acknowledgment. */
  boolean hasCommittedMutations();

  /**
   * Whether this document has a local mutation applied that has not yet been acknowledged by Watch.
   */
  boolean hasPendingWrites();

  /** Creates a mutable copy of this document. */
  MutableDocument mutableCopy();
}
