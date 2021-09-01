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

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An index definition for field indices in Firestore.
 *
 * <p>Every index is associated with a collection. The definition contains a list of fields and the
 * indexes kind (which can be {@link Segment.Kind#ORDERED} or {@link Segment.Kind#CONTAINS} for
 * ArrayContains/ArrayContainsAny queries.
 *
 * <p>Unlike the backend, the SDK does not differentiate between collection or collection
 * group-scoped indices. Every index can be used for both single collection and collection group
 * queries.
 */
public final class FieldIndex implements Iterable<FieldIndex.Segment> {

  /** An index component consisting of field path and index type. */
  @AutoValue
  public abstract static class Segment {
    /** The type of the index, e.g. for which type of query it can be used. */
    public enum Kind {
      /** Ascending index. Can be used for <, <=, ==, >=, >, !=, IN and NOT IN queries. */
      ORDERED,
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

  private final String collectionId;
  private final List<Segment> segments;

  public FieldIndex(String collectionId) {
    this.collectionId = collectionId;
    this.segments = new ArrayList<>();
  }

  FieldIndex(String collectionId, List<Segment> segments) {
    this.collectionId = collectionId;
    this.segments = segments;
  }

  /** The collection ID this index applies to. */
  public String getCollectionId() {
    return collectionId;
  }

  public Segment getSegment(int index) {
    return segments.get(index);
  }

  public int segmentCount() {
    return segments.size();
  }

  /**
   * Returns a new field index that only contains the first `size` segments.
   *
   * @throws IndexOutOfBoundsException if size > segmentCount
   */
  public FieldIndex prefix(int size) {
    return new FieldIndex(collectionId, segments.subList(0, size));
  }

  @NonNull
  @Override
  public Iterator<Segment> iterator() {
    return segments.iterator();
  }

  /** Returns a new field index with additional index segment. */
  public FieldIndex withAddedField(FieldPath fieldPath, Segment.Kind kind) {
    List<Segment> newSegments = new ArrayList<>(segments);
    newSegments.add(new AutoValue_FieldIndex_Segment(fieldPath, kind));
    return new FieldIndex(collectionId, newSegments);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FieldIndex fieldIndex = (FieldIndex) o;

    if (!segments.equals(fieldIndex.segments)) return false;
    return collectionId.equals(fieldIndex.collectionId);
  }

  @Override
  public int hashCode() {
    int result = collectionId.hashCode();
    result = 31 * result + segments.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return String.format("FieldIndex{collectionId='%s', segments=%s}", collectionId, segments);
  }
}
