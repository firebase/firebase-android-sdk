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

/**
 * Represents a document in Firestore with a key, version, data and whether it has local mutations
 * applied to it.
 *
 * <p>Documents can transition between states via {@link #convertToFoundDocument}, {@link
 * #convertToNoDocument} and {@link #convertToUnknownDocument}. If a document does not transition to
 * one of these states even after all mutations have been applied, {@link #isValidDocument} returns
 * false and the document should be removed from all views.
 */
public final class MutableDocument implements Document {

  private enum DocumentType {
    /**
     * Represents the initial state of a MutableDocument when only the document key is known.
     * Invalid documents transition to other states as mutations are applied. If a document remains
     * invalid after applying mutations, it should be discarded.
     */
    INVALID,
    /**
     * Represents a document in Firestore with a key, version, data and whether the data has local
     * mutations applied to it.
     */
    FOUND_DOCUMENT,
    /** Represents that no documents exists for the key at the given version. */
    NO_DOCUMENT,
    /**
     * Represents an existing document whose data is unknown (for example, a document that was
     * updated without a known base document).
     */
    UNKNOWN_DOCUMENT;
  }

  /** Describes the `hasPendingWrites` state of a document. */
  private enum DocumentState {
    /** Local mutations applied via the mutation queue. Document is potentially inconsistent. */
    HAS_LOCAL_MUTATIONS,
    /** Mutations applied based on a write acknowledgment. Document is potentially inconsistent. */
    HAS_COMMITTED_MUTATIONS,
    /** No mutations applied. Document was sent to us by Watch. */
    SYNCED
  }

  private final DocumentKey key;
  private DocumentType documentType;
  private SnapshotVersion version;
  private SnapshotVersion readTime;
  private ObjectValue value;
  private DocumentState documentState;

  private MutableDocument(DocumentKey key) {
    this.key = key;
    this.readTime = SnapshotVersion.NONE;
  }

  private MutableDocument(
      DocumentKey key,
      DocumentType documentType,
      SnapshotVersion version,
      SnapshotVersion readTime,
      ObjectValue value,
      DocumentState documentState) {
    this.key = key;
    this.version = version;
    this.readTime = readTime;
    this.documentType = documentType;
    this.documentState = documentState;
    this.value = value;
  }

  /**
   * Creates a document with no known version or data, but which can serve as base document for
   * mutations.
   */
  public static MutableDocument newInvalidDocument(DocumentKey documentKey) {
    return new MutableDocument(
        documentKey,
        DocumentType.INVALID,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        new ObjectValue(),
        DocumentState.SYNCED);
  }

  /** Creates a new document that is known to exist with the given data at the given version. */
  public static MutableDocument newFoundDocument(
      DocumentKey documentKey, SnapshotVersion version, ObjectValue value) {
    return new MutableDocument(documentKey).convertToFoundDocument(version, value);
  }

  /** Creates a new document that is known to not exisr at the given version. */
  public static MutableDocument newNoDocument(DocumentKey documentKey, SnapshotVersion version) {
    return new MutableDocument(documentKey).convertToNoDocument(version);
  }

  /**
   * Creates a new document that is known to exist at the given version but whose data is not known
   * (for example, a document that was updated without a known base document).
   */
  public static MutableDocument newUnknownDocument(
      DocumentKey documentKey, SnapshotVersion version) {
    return new MutableDocument(documentKey).convertToUnknownDocument(version);
  }

  /**
   * Changes the document type to indicate that it exists and that its version and data are known.
   */
  public MutableDocument convertToFoundDocument(SnapshotVersion version, ObjectValue value) {
    this.version = version;
    this.documentType = DocumentType.FOUND_DOCUMENT;
    this.value = value;
    this.documentState = DocumentState.SYNCED;
    return this;
  }

  /** Changes the document type to indicate that it doesn't exist at the given version. */
  public MutableDocument convertToNoDocument(SnapshotVersion version) {
    this.version = version;
    this.documentType = DocumentType.NO_DOCUMENT;
    this.value = new ObjectValue();
    this.documentState = DocumentState.SYNCED;
    return this;
  }

  /**
   * Changes the document type to indicate that it exists at a given version but that its data is
   * not known (for example, a document that was updated without a known base document).
   */
  public MutableDocument convertToUnknownDocument(SnapshotVersion version) {
    this.version = version;
    this.documentType = DocumentType.UNKNOWN_DOCUMENT;
    this.value = new ObjectValue();
    this.documentState = DocumentState.HAS_COMMITTED_MUTATIONS;
    return this;
  }

  public MutableDocument setHasCommittedMutations() {
    this.documentState = DocumentState.HAS_COMMITTED_MUTATIONS;
    return this;
  }

  public MutableDocument setHasLocalMutations() {
    this.documentState = DocumentState.HAS_LOCAL_MUTATIONS;
    this.version = SnapshotVersion.NONE;
    return this;
  }

  public MutableDocument setReadTime(SnapshotVersion readTime) {
    this.readTime = readTime;
    return this;
  }

  @Override
  public DocumentKey getKey() {
    return key;
  }

  @Override
  public SnapshotVersion getVersion() {
    return version;
  }

  @Override
  public SnapshotVersion getReadTime() {
    return readTime;
  }

  @Override
  public boolean hasLocalMutations() {
    return documentState.equals(DocumentState.HAS_LOCAL_MUTATIONS);
  }

  @Override
  public boolean hasCommittedMutations() {
    return documentState.equals(DocumentState.HAS_COMMITTED_MUTATIONS);
  }

  @Override
  public boolean hasPendingWrites() {
    return hasLocalMutations() || hasCommittedMutations();
  }

  @Override
  public ObjectValue getData() {
    return value;
  }

  @Override
  public Value getField(FieldPath field) {
    return getData().get(field);
  }

  @Override
  public boolean isValidDocument() {
    return !documentType.equals(DocumentType.INVALID);
  }

  @Override
  public boolean isFoundDocument() {
    return documentType.equals(DocumentType.FOUND_DOCUMENT);
  }

  @Override
  public boolean isNoDocument() {
    return documentType.equals(DocumentType.NO_DOCUMENT);
  }

  @Override
  public boolean isUnknownDocument() {
    return documentType.equals(DocumentType.UNKNOWN_DOCUMENT);
  }

  @Override
  @NonNull
  public MutableDocument mutableCopy() {
    return new MutableDocument(key, documentType, version, readTime, value.clone(), documentState);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MutableDocument document = (MutableDocument) o;

    if (!key.equals(document.key)) return false;
    if (!version.equals(document.version)) return false;
    // TODO(mrschmidt): Include readTime (requires a lot of test updates)
    // if (!readTime.equals(document.readTime)) return false;
    if (!documentType.equals(document.documentType)) return false;
    if (!documentState.equals(document.documentState)) return false;
    return value.equals(document.value);
  }

  @Override
  public int hashCode() {
    // We only use the key for the hashcode as all other document properties are mutable.
    // While mutable documents should not be uses as keys in collections, the hash code is used
    // in DocumentSet, which tracks Documents that are no longer being mutated but which are
    // backed by this class.
    return key.hashCode();
  }

  @Override
  public String toString() {
    return "Document{"
        + "key="
        + key
        + ", version="
        + version
        + ", readTime="
        + readTime
        + ", type="
        + documentType
        + ", documentState="
        + documentState
        + ", value="
        + value
        + '}';
  }
}
