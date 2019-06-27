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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.common.base.Function;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firestore.v1.Value;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
  private @Nullable final com.google.firestore.v1.Document proto;
  private @Nullable final Function<Value, FieldValue> converter;
  private @Nullable ObjectValue objectValue;

  /** A cache for FieldValues that have already been deserialized in `getField()`. */
  private @Nullable Map<FieldPath, FieldValue> fieldValueCache;

  public Document(
      DocumentKey key,
      SnapshotVersion version,
      DocumentState documentState,
      ObjectValue objectValue) {
    super(key, version);
    this.documentState = documentState;
    this.objectValue = objectValue;
    this.proto = null;
    this.converter = null;
  }

  public Document(
      DocumentKey key,
      SnapshotVersion version,
      DocumentState documentState,
      com.google.firestore.v1.Document proto,
      Function<com.google.firestore.v1.Value, FieldValue> converter) {
    super(key, version);
    this.documentState = documentState;
    this.proto = proto;
    this.converter = converter;
  }

  /**
   * Memoized serialized form of the document for optimization purposes (avoids repeated
   * serialization). Might be null.
   */
  public @Nullable com.google.firestore.v1.Document getProto() {
    return proto;
  }

  @Nonnull
  public ObjectValue getData() {
    if (objectValue == null) {
      hardAssert(proto != null && converter != null, "Expected proto and converter to be non-null");

      ObjectValue result = ObjectValue.emptyObject();
      for (Map.Entry<String, com.google.firestore.v1.Value> entry :
          proto.getFieldsMap().entrySet()) {
        FieldPath path = FieldPath.fromSingleSegment(entry.getKey());
        FieldValue value = converter.apply(entry.getValue());
        result = result.set(path, value);
      }
      objectValue = result;

      // Once objectValue is computed, values inside the fieldValueCache are no longer accessed.
      fieldValueCache = null;
    }

    return objectValue;
  }

  public @Nullable FieldValue getField(FieldPath path) {
    if (objectValue != null) {
      return objectValue.get(path);
    } else {
      hardAssert(proto != null && converter != null, "Expected proto and converter to be non-null");

      if (fieldValueCache == null) {
        // TODO(b/136090445): Remove the cache when `getField` is no longer called during Query
        // ordering.
        fieldValueCache = new ConcurrentHashMap<>();
      }

      FieldValue fieldValue = fieldValueCache.get(path);
      if (fieldValue == null) {
        // Instead of deserializing the full Document proto, we only deserialize the value at
        // the requested field path. This speeds up Query execution as query filters can discard
        // documents based on a single field.
        Value protoValue = proto.getFieldsMap().get(path.getFirstSegment());
        for (int i = 1; protoValue != null && i < path.length(); ++i) {
          if (protoValue.getValueTypeCase() != Value.ValueTypeCase.MAP_VALUE) {
            return null;
          }
          protoValue = protoValue.getMapValue().getFieldsMap().get(path.getSegment(i));
        }

        if (protoValue != null) {
          fieldValue = converter.apply(protoValue);
          fieldValueCache.put(path, fieldValue);
        }
      }

      return fieldValue;
    }
  }

  public @Nullable Object getFieldValue(FieldPath path) {
    FieldValue value = getField(path);
    return (value == null) ? null : value.value();
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
        && getData().equals(document.getData());
  }

  @Override
  public int hashCode() {
    // Note: We deliberately decided to omit `getData()` since its computation is expensive.
    int result = getKey().hashCode();
    result = 31 * result + getVersion().hashCode();
    result = 31 * result + documentState.hashCode();
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
