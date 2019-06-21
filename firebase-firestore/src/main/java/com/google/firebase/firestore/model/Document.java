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

import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firestore.v1.Value;
import java.util.Comparator;
import javax.annotation.Nullable;

/**
 * Represents a document in Firestore with a key, version, data and whether the data has local
 * mutations applied to it.
 */
public abstract class Document extends MaybeDocument {

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

  /** Creates a new Document from an existing ObjectValue. */
  public static Document fromObjectValue(
      DocumentKey key, SnapshotVersion version, ObjectValue data, DocumentState documentState) {
    return new Document(key, version, documentState) {
      @Nullable
      @Override
      public com.google.firestore.v1.Document getProto() {
        return null;
      }

      @Override
      public ObjectValue getData() {
        return data;
      }

      @Nullable
      @Override
      public FieldValue getField(FieldPath path) {
        return data.get(path);
      }
    };
  }

  /**
   * Creates a new Document from a Proto representation.
   *
   * <p>The Proto is only converted to an ObjectValue if the consumer calls `getData()`.
   */
  public static Document fromProto(
      RemoteSerializer serializer,
      com.google.firestore.v1.Document proto,
      DocumentState documentState) {
    DocumentKey key = serializer.decodeKey(proto.getName());
    SnapshotVersion version = serializer.decodeVersion(proto.getUpdateTime());

    hardAssert(
        !version.equals(SnapshotVersion.NONE), "Found a document Proto with no snapshot version");

    return new Document(key, version, documentState) {
      private ObjectValue memoizedData = null;

      @Override
      public com.google.firestore.v1.Document getProto() {
        return proto;
      }

      @Override
      public ObjectValue getData() {
        if (memoizedData != null) {
          return memoizedData;
        } else {
          memoizedData = serializer.decodeFields(proto.getFieldsMap());
          return memoizedData;
        }
      }

      @Nullable
      @Override
      public FieldValue getField(FieldPath path) {
        if (memoizedData != null) {
          return memoizedData.get(path);
        } else {
          // Instead of deserializing the full Document proto, we only deserialize the value at
          // the requested field path. This speeds up Query execution as query filters can discard
          // documents based on a single field.
          Value value = proto.getFieldsMap().get(path.getFirstSegment());
          for (int i = 1; value != null && i < path.length(); ++i) {
            if (value.getValueTypeCase() != Value.ValueTypeCase.MAP_VALUE) {
              return null;
            }
            value = value.getMapValue().getFieldsMap().get(path.getSegment(i));
          }
          return value != null ? serializer.decodeValue(value) : null;
        }
      }
    };
  }

  private final DocumentState documentState;

  private Document(DocumentKey key, SnapshotVersion version, DocumentState documentState) {
    super(key, version);
    this.documentState = documentState;
  }

  /**
   * Memoized serialized form of the document for optimization purposes (avoids repeated
   * serialization). Might be null.
   */
  public abstract @Nullable com.google.firestore.v1.Document getProto();

  public abstract ObjectValue getData();

  public abstract @Nullable FieldValue getField(FieldPath path);

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
