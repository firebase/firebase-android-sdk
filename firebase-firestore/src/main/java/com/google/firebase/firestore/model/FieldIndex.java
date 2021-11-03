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
public final class FieldIndex {

  /** An index component consisting of field path and index type. */
  @AutoValue
  public abstract static class Segment {
    /** The type of the index, e.g. for which type of query it can be used. */
    public enum Kind {
      /** Ordered index. Can be used for <, <=, ==, >=, >, !=, IN and NOT IN queries. */
      ASCENDING,
      /** Ordered index. Can be used for <, <=, ==, >=, >, !=, IN and NOT IN queries. */
      DESCENDING,
      /** Contains index. Can be used for ArrayContains and ArrayContainsAny */
      CONTAINS
    }

    /** The field path of the component. */
    public abstract FieldPath getFieldPath();

    /** The indexes sorting order. */
    public abstract Kind getKind();

    @Override
    public String toString() {
      return String.format("Segment{fieldPath=%s, kind=%s}", getFieldPath(), getKind());
    }
  }

  private final String collectionGroup;
  private final int indexId;
  private final List<Segment> segments;
  private final SnapshotVersion updateTime;

  public FieldIndex(String collectionGroup, int indexId) {
    this.collectionGroup = collectionGroup;
    this.segments = new ArrayList<>();
    this.indexId = indexId;
    this.updateTime = SnapshotVersion.NONE;
  }

  public FieldIndex(String collectionId) {
    this(collectionId, -1);
  }

  FieldIndex(
      String collectionGroup, int indexId, List<Segment> segments, SnapshotVersion updateTime) {
    this.collectionGroup = collectionGroup;
    this.segments = segments;
    this.indexId = indexId;
    this.updateTime = updateTime;
  }

  /** The collection ID this index applies to. */
  public String getCollectionGroup() {
    return collectionGroup;
  }

  /**
   * The index ID. Returns -1 if the index ID is not available (e.g. the index has not yet been
   * persisted).
   */
  public int getIndexId() {
    return indexId;
  }

  public Segment getSegment(int index) {
    return segments.get(index);
  }

  public int segmentCount() {
    return segments.size();
  }

  /**
   * Returns the latest read time version that has been indexed by Firestore for this field index.
   */
  public SnapshotVersion getUpdateTime() {
    return updateTime;
  }

  public List<Segment> getDirectionalSegments() {
    List<Segment> filteredSegments = new ArrayList<>();
    for (Segment segment : segments) {
      if (!segment.getKind().equals(Segment.Kind.CONTAINS)) {
        filteredSegments.add(segment);
      }
    }
    return filteredSegments;
  }

  public @Nullable Segment getArraySegment() {
    for (Segment segment : segments) {
      if (segment.getKind().equals(Segment.Kind.CONTAINS)) {
        // Firestore queries can only have a single ArrayContains/ArrayContainsAny statements.
        return segment;
      }
    }
    return null;
  }

  /** Returns a new field index with additional index segment. */
  public FieldIndex withAddedField(FieldPath fieldPath, Segment.Kind kind) {
    List<Segment> newSegments = new ArrayList<>(segments);
    newSegments.add(new AutoValue_FieldIndex_Segment(fieldPath, kind));
    return new FieldIndex(collectionGroup, indexId, newSegments, updateTime);
  }

  /** Returns a new field index with the updated version. */
  public FieldIndex withUpdateTime(SnapshotVersion updateTime) {
    return new FieldIndex(collectionGroup, indexId, segments, updateTime);
  }

  /** Returns a new field index with the provided index id. */
  public FieldIndex withIndexId(int indexId) {
    return new FieldIndex(collectionGroup, indexId, segments, updateTime);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FieldIndex fieldIndex = (FieldIndex) o;

    if (!segments.equals(fieldIndex.segments)) return false;
    if (!updateTime.equals(fieldIndex.updateTime)) return false;
    return collectionGroup.equals(fieldIndex.collectionGroup);
  }

  @Override
  public int hashCode() {
    int result = collectionGroup.hashCode();
    result = 31 * result + segments.hashCode();
    result = 31 * result + updateTime.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return String.format(
        "FieldIndex{collectionGroup='%s', segments=%s, updateTime=%s}",
        collectionGroup, segments, updateTime);
  }
}
