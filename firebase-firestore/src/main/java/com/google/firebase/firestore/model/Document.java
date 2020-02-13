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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firestore.v1.Value;
import java.util.Comparator;

/**
 * Represents a document in Firestore with a key, version, data and whether the data has local
 * mutations applied to it.
 */
public final class Document extends MaybeDocument {

  /** Describes the `hasPendingWrites` state of a document. */
  public enum DocumentState {
    /** Local mutations applied via the mutation queue. Document is potentially inconsistent. */
    LOCAL_MUTATIONS,
    /** Mutations applied based on a write acknowledgment. Document is potentially inconsistent. */
    COMMITTED_MUTATIONS,
    /** No mutations applied. Document was sent to us by Watch. */
    SYNCED
  }

  private static final Comparator<Document> KEY_COMPARATOR =
      (left, right) -> left.getKey().compareTo(right.getKey());

  /** A document comparator that returns document by key and key only. */
  public static Comparator<Document> keyComparator() {
    return KEY_COMPARATOR;
  }

  private final DocumentState documentState;
  private ObjectValue objectValue;

  public Document(
      DocumentKey key,
      SnapshotVersion version,
      ObjectValue objectValue,
      DocumentState documentState) {
    super(key, version);
    this.documentState = documentState;
    this.objectValue = objectValue;
  }

  @NonNull
  public ObjectValue getData() {
    return objectValue;
  }

  public @Nullable Value getField(FieldPath path) {
    return objectValue.get(path);
  }

  public boolean hasLocalMutations() {
    return documentState.equals(DocumentState.LOCAL_MUTATIONS);
  }

  public boolean hasCommittedMutations() {
    return documentState.equals(DocumentState.COMMITTED_MUTATIONS);
  }

  @Override
  public boolean hasPendingWrites() {
    return this.hasLocalMutations() || this.hasCommittedMutations();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Document)) {
      return false;
    }

    Document document = (Document) o;

    return getVersion().equals(document.getVersion())
        && getKey().equals(document.getKey())
        && documentState.equals(document.documentState)
        && objectValue.equals(document.objectValue);
  }

  @Override
  public int hashCode() {
    int result = getKey().hashCode();
    result = 31 * result + getVersion().hashCode();
    result = 31 * result + documentState.hashCode();
    result = 31 * result + objectValue.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Document{"
        + "key="
        + getKey()
        + ", data="
        + getData()
        + ", version="
        + getVersion()
        + ", documentState="
        + documentState.name()
        + '}';
  }
}
