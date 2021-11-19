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

  public static FieldIndex create(
      int indexId, String collectionGroup, List<Segment> segments, SnapshotVersion updateTime) {
    return new AutoValue_FieldIndex(indexId, collectionGroup, segments, updateTime);
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

  /** Returns when this index was last updated. */
  public abstract SnapshotVersion getUpdateTime();

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
