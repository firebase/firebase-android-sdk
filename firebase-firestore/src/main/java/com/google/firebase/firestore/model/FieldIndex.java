// Copyright 2021 Google LLC
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
import com.google.auto.value.AutoValue;
import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * An index definition for field indices in Firestore.
 *
 * <p>Every index is associated with a collection. The definition contains a list of fields and
 * their indexkind (which can be {@link Segment.Kind#ASCENDING}, {@link Segment.Kind#DESCENDING} or
 * {@link Segment.Kind#CONTAINS}) for ArrayContains/ArrayContainsAny queries.
 *
 * <p>Unlike the backend, the SDK does not differentiate between collection or collection
 * group-scoped indices. Every index can be used for both single collection and collection group
 * queries.
 */
@AutoValue
public abstract class FieldIndex {

  /** An ID for an index that has not yet been added to persistence. */
  public static final int UNKNOWN_ID = -1;

  /** The initial sequence number for each index. Gets updated during index backfill. */
  public static final int INITIAL_SEQUENCE_NUMBER = 0;

  /** The state of an index that has not yet been backfilled. */
  public static IndexState INITIAL_STATE =
      IndexState.create(INITIAL_SEQUENCE_NUMBER, SnapshotVersion.NONE, DocumentKey.empty());

  /** Compares indexes by collection group and segments. Ignores update time and index ID. */
  public static final Comparator<FieldIndex> SEMANTIC_COMPARATOR =
      (left, right) -> {
        int cmp = left.getCollectionGroup().compareTo(right.getCollectionGroup());
        if (cmp != 0) return cmp;

        Iterator<Segment> leftIt = left.getSegments().iterator();
        Iterator<Segment> rightIt = right.getSegments().iterator();
        while (leftIt.hasNext() && rightIt.hasNext()) {
          cmp = leftIt.next().compareTo(rightIt.next());
          if (cmp != 0) return cmp;
        }
        return Boolean.compare(leftIt.hasNext(), rightIt.hasNext());
      };

  /** An index component consisting of field path and index type. */
  @AutoValue
  public abstract static class Segment implements Comparable<Segment> {
    /** The type of the index, e.g. for which type of query it can be used. */
    public enum Kind {
      /** Ordered index. Can be used for <, <=, ==, >=, >, !=, IN and NOT IN queries. */
      ASCENDING,
      /** Ordered index. Can be used for <, <=, ==, >=, >, !=, IN and NOT IN queries. */
      DESCENDING,
      /** Contains index. Can be used for ArrayContains and ArrayContainsAny */
      CONTAINS
    }

    public static Segment create(FieldPath fieldPath, Kind kind) {
      return new AutoValue_FieldIndex_Segment(fieldPath, kind);
    }

    /** The field path of the component. */
    public abstract FieldPath getFieldPath();

    /** The indexes sorting order. */
    public abstract Kind getKind();

    @Override
    public int compareTo(Segment other) {
      int cmp = getFieldPath().compareTo(other.getFieldPath());
      if (cmp != 0) return cmp;
      return getKind().compareTo(other.getKind());
    }
  }

  /** Stores the "high water mark" that indicates how updated the Index is for the current user. */
  @AutoValue
  public abstract static class IndexState {
    public static IndexState create(long sequenceNumber, IndexOffset offset) {
      return new AutoValue_FieldIndex_IndexState(sequenceNumber, offset);
    }

    public static IndexState create(
        long sequenceNumber, SnapshotVersion readTime, DocumentKey documentKey) {
      return create(sequenceNumber, IndexOffset.create(readTime, documentKey));
    }

    /**
     * Returns a number that indicates when the index was last updated (relative to other indexes).
     */
    public abstract long getSequenceNumber();

    /** Returns the latest indexed read time and document. */
    public abstract IndexOffset getOffset();
  }

  /** Stores the latest read time and document that were processed for an index. */
  @AutoValue
  public abstract static class IndexOffset implements Comparable<IndexOffset> {
    public static final IndexOffset NONE = create(SnapshotVersion.NONE, DocumentKey.empty());

    /**
     * Creates an offset that matches all documents with a read time higher than {@code readTime} or
     * with a key higher than {@code documentKey} for equal read times.
     */
    public static IndexOffset create(SnapshotVersion readTime, DocumentKey documentKey) {
      return new AutoValue_FieldIndex_IndexOffset(readTime, documentKey);
    }

    /**
     * Creates an offset that matches all documents with a read time higher than {@code readTime}.
     */
    public static IndexOffset create(SnapshotVersion readTime) {
      // We want to create an offset that matches all documents with a read time greater than
      // the provided read time. To do so, we technically need to create an offset for
      // `(readTime, MAX_DOCUMENT_KEY)`. While we could use Unicode codepoints to generate
      // MAX_DOCUMENT_KEY, it is much easier to use `(readTime + 1, DocumentKey.empty())` since
      // `> DocumentKey.empty()` matches all valid document IDs.
      long successorSeconds = readTime.getTimestamp().getSeconds();
      int successorNanos = readTime.getTimestamp().getNanoseconds() + 1;
      SnapshotVersion successor =
          new SnapshotVersion(
              successorNanos == 1e9
                  ? new Timestamp(successorSeconds + 1, 0)
                  : new Timestamp(successorSeconds, successorNanos));
      return new AutoValue_FieldIndex_IndexOffset(successor, DocumentKey.empty());
    }

    /** Creates a new offset based on the provided document. */
    public static IndexOffset fromDocument(Document document) {
      return new AutoValue_FieldIndex_IndexOffset(document.getReadTime(), document.getKey());
    }

    /**
     * Returns the latest read time version that has been indexed by Firestore for this field index.
     */
    public abstract SnapshotVersion getReadTime();

    /**
     * Returns the key of the last document that was indexed for this query. Returns {@link
     * DocumentKey#empty} if no document has been indexed.
     */
    public abstract DocumentKey getDocumentKey();

    public int compareTo(IndexOffset other) {
      int cmp = getReadTime().compareTo(other.getReadTime());
      if (cmp != 0) return cmp;
      return getDocumentKey().compareTo(other.getDocumentKey());
    }
  }

  public static FieldIndex create(
      int indexId, String collectionGroup, List<Segment> segments, IndexState indexState) {
    return new AutoValue_FieldIndex(indexId, collectionGroup, segments, indexState);
  }

  /**
   * The index ID. Returns -1 if the index ID is not available (e.g. the index has not yet been
   * persisted).
   */
  public abstract int getIndexId();

  /** The collection ID this index applies to. */
  public abstract String getCollectionGroup();

  /** Returns all field segments for this index. */
  public abstract List<Segment> getSegments();

  /** Returns how up-to-date the index is for the current user. */
  public abstract IndexState getIndexState();

  /** Returns all directional (ascending/descending) segments for this index. */
  public List<Segment> getDirectionalSegments() {
    List<Segment> filteredSegments = new ArrayList<>();
    for (Segment segment : getSegments()) {
      if (!segment.getKind().equals(Segment.Kind.CONTAINS)) {
        filteredSegments.add(segment);
      }
    }
    return filteredSegments;
  }

  /** Returns the ArrayContains/ArrayContainsAny segment for this index. */
  public @Nullable Segment getArraySegment() {
    for (Segment segment : getSegments()) {
      if (segment.getKind().equals(Segment.Kind.CONTAINS)) {
        // Firestore queries can only have a single ArrayContains/ArrayContainsAny statements.
        return segment;
      }
    }
    return null;
  }
}
