// Copyright 2020 Google LLC
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
import com.google.firestore.v1.Value;
import java.util.Comparator;

public class MutableDocument implements Cloneable {
  private static final Comparator<MutableDocument> KEY_COMPARATOR =
      (left, right) -> left.getKey().compareTo(right.getKey());

  /** A document comparator that returns document by key and key only. */
  public static Comparator<MutableDocument> keyComparator() {
    return KEY_COMPARATOR;
  }

  private enum Type {
    INVALID,
    FOUND_DOCUMENT,
    NO_DOCUMENT,
    UNKNOWN_DOCUMENT;
  }

  private final DocumentKey key;
  private Type type;
  private SnapshotVersion version;
  private ObjectValue value;
  boolean hasLocalMutations;
  boolean hasCommittedMutations;

  public MutableDocument(DocumentKey key) {
    this.key = key;
    this.version = SnapshotVersion.NONE;
    this.type = Type.INVALID;
    this.value = new ObjectValue();
  }

  private MutableDocument(
      DocumentKey key,
      Type type,
      SnapshotVersion version,
      ObjectValue value,
      boolean hasLocalMutations,
      boolean hasCommittedMutations) {
    this.key = key;
    this.version = version;
    this.type = type;
    this.hasLocalMutations = hasLocalMutations;
    this.hasCommittedMutations = hasCommittedMutations;
    this.value = value;
  }

  /** Changes the document type to FOUND_DOCUMENT and sets the given version and data. */
  public MutableDocument setFoundDocument(SnapshotVersion version, ObjectValue value) {
    this.version = version;
    this.type = Type.FOUND_DOCUMENT;
    this.value = value;
    this.hasLocalMutations = false;
    this.hasCommittedMutations = false;
    return this;
  }

  /** Changes the document type to NO_DOCUMENT and sets the given version. */
  public MutableDocument setNoDocument(SnapshotVersion version) {
    this.version = version;
    this.type = Type.NO_DOCUMENT;
    this.value = new ObjectValue();
    this.hasLocalMutations = false;
    this.hasCommittedMutations = false;
    return this;
  }

  /** Changes the document type to UNKNOWN_DOCUMENT and sets the given version. */
  public MutableDocument setUnknownDocument(SnapshotVersion version) {
    this.version = version;
    this.type = Type.UNKNOWN_DOCUMENT;
    this.value = new ObjectValue();
    this.hasLocalMutations = false;
    this.hasCommittedMutations = true;
    return this;
  }

  public MutableDocument setCommittedMutations() {
    this.hasLocalMutations = false;
    this.hasCommittedMutations = true;
    return this;
  }

  public MutableDocument setLocalMutations() {
    this.hasLocalMutations = true;
    this.hasCommittedMutations = true;
    return this;
  }

  /** The key for this document */
  public DocumentKey getKey() {
    return key;
  }

  /**
   * Returns the version of this document if it exists or a version at which this document was
   * guaranteed to not exist.
   */
  public SnapshotVersion getVersion() {
    return version;
  }

  /** Returns whether local mutations were applied via the mutation queue. */
  public boolean hasLocalMutations() {
    return hasLocalMutations;
  }

  /** Returns whether mutations were applied based on a write acknowledgment. */
  public boolean hasCommittedMutations() {
    return hasCommittedMutations;
  }

  /**
   * Whether this document has a local mutation applied that has not yet been acknowledged by Watch.
   */
  public boolean hasPendingWrites() {
    return hasLocalMutations() || hasCommittedMutations();
  }

  public ObjectValue getData() {
    return value;
  }

  public Value getField(FieldPath field) {
    return getData().get(field);
  }

  public boolean isValidDocument() {
    return !type.equals(Type.INVALID);
  }

  public boolean isFoundDocument() {
    return type.equals(Type.FOUND_DOCUMENT);
  }

  public boolean isNoDocument() {
    return type.equals(Type.NO_DOCUMENT);
  }

  public boolean isUnknownDocument() {
    return type.equals(Type.UNKNOWN_DOCUMENT);
  }

  @Override
  @NonNull
  public MutableDocument clone() {
    return new MutableDocument(
        key, type, version, value.clone(), hasLocalMutations, hasCommittedMutations);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MutableDocument document = (MutableDocument) o;

    if (hasLocalMutations != document.hasLocalMutations) return false;
    if (hasCommittedMutations != document.hasCommittedMutations) return false;
    if (!key.equals(document.key)) return false;
    if (!version.equals(document.version)) return false;
    if (type != document.type) return false;
    return value.equals(document.value);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    return "Document{"
        + "key="
        + key
        + ", version="
        + version
        + ", type="
        + type
        + ", hasLocalMutations="
        + hasLocalMutations
        + ", hasCommittedMutations="
        + hasCommittedMutations
        + ", value="
        + value
        + '}';
  }
}
