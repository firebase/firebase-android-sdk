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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * A {@code DocumentChange} represents a change to the documents matching a query. It contains the
 * document affected and a the type of change that occurred (added, modified, or removed).
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class DocumentChange {
  /** An enumeration of snapshot diff types. */
  public enum Type {
    /** Indicates a new document was added to the set of documents matching the query. */
    ADDED,
    /** Indicates a document within the query was modified. */
    MODIFIED,
    /**
     * Indicates a document within the query was removed (either deleted or no longer matches the
     * query.
     */
    REMOVED
  }

  private final Type type;

  private final QueryDocumentSnapshot document;

  /** The index in the old snapshot, after processing all previous changes. */
  private final int oldIndex;

  /** The index in the new snapshot, after processing all previous changes. */
  private final int newIndex;

  @VisibleForTesting
  DocumentChange(QueryDocumentSnapshot document, Type type, int oldIndex, int newIndex) {
    this.type = type;
    this.document = document;
    this.oldIndex = oldIndex;
    this.newIndex = newIndex;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof DocumentChange) {
      DocumentChange that = (DocumentChange) object;
      return this.type.equals(that.type)
          && this.document.equals(that.document)
          && this.oldIndex == that.oldIndex
          && this.newIndex == that.newIndex;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = type.hashCode();
    result = result * 31 + document.hashCode();
    result = result * 31 + oldIndex;
    result = result * 31 + newIndex;
    return result;
  }

  @NonNull
  public Type getType() {
    return type;
  }

  /**
   * Returns the newly added or modified document if this {@code DocumentChange} is for an updated
   * document. Returns the deleted document if this document change represents a removal.
   *
   * @return A snapshot of the new data (for {@link DocumentChange.Type#ADDED} or {@link
   *     DocumentChange.Type#MODIFIED}) or the removed data (for {@link
   *     DocumentChange.Type#REMOVED}).
   */
  @NonNull
  public QueryDocumentSnapshot getDocument() {
    return document;
  }

  /**
   * The index of the changed document in the result set immediately prior to this {@code
   * DocumentChange} (assuming that all prior {@code DocumentChange} objects have been applied).
   * Returns -1 for 'added' events.
   */
  public int getOldIndex() {
    return oldIndex;
  }

  /**
   * The index of the changed document in the result set immediately after this {@code
   * DocumentChange} (assuming that all prior {@code DocumentChange} objects and the current {@code
   * DocumentChange} object have been applied). Returns -1 for 'removed' events.
   */
  public int getNewIndex() {
    return newIndex;
  }
}
