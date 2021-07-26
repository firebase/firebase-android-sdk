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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An index definition for field indices in Firestore.
 *
 * <p>Every index is associated with a collection. The definition contains a list of fields and the
 * indexes sorting order (which can be {@link Segment.Kind#ASCENDING}, {@link
 * Segment.Kind#DESCENDING} or {@link Segment.Kind#CONTAINS} for ArrayContains/ArrayContainsAn
 * queries.
 *
 * <p>Unlike the backend, the SDK does not differentiate between collection or collection
 * group-scoped indices. Every index can be used for both single collection and collection group
 * queries.
 */
public class FieldIndex implements Iterable<FieldIndex.Segment> {

  /** An index component consisting of field path and index type. */
  public static class Segment {
    /** The type of the index, e.g. for which sorting order it can be used. */
    public enum Kind {
      /** Ascending index. Can be used for <, <=, ==, >=, > and IN with ascending ordering. */
      ASCENDING,
      /** Descending index. Can be used for <, <=, ==, >=, > and IN with descending ordering. */
      DESCENDING,
      /** Contains index. Can be used for ArrayContains and ArrayContainsAny */
      CONTAINS
    }

    private final FieldPath fieldPath;
    private final Kind kind;

    public Segment(FieldPath fieldPath, Kind kind) {
      this.fieldPath = fieldPath;
      this.kind = kind;
    }

    /** The field path of the component. */
    public FieldPath getFieldPath() {
      return fieldPath;
    }

    /** The indexes sorting order. */
    public Kind getKind() {
      return kind;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Segment segment = (Segment) o;
      if (!fieldPath.equals(segment.fieldPath)) return false;
      return kind == segment.kind;
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

  @NonNull
  @Override
  public Iterator<Segment> iterator() {
    return segments.iterator();
  }

  /** Returns a new field index with additional index segment. */
  public FieldIndex withComponent(FieldPath fieldPath, Segment.Kind kind) {
    List<Segment> newSegments = new ArrayList<>(segments);
    newSegments.add(new Segment(fieldPath, kind));
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
}
