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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firestore.v1.Value;

/** A range of index field values over which a cursor should iterate. */
public class IndexRange {

  // If start and end are both null, any field value will be considered within range.
  @Nullable private final Value start;
  @Nullable private final Value end;
  private final FieldPath fieldPath;

  private IndexRange(Builder builder) {
    this.fieldPath = builder.fieldPath;
    this.start = builder.start;
    this.end = builder.end;
  }

  /** Returns the field path to use for the index lookup. */
  public FieldPath getFieldPath() {
    return fieldPath;
  }

  /** Returns the inclusive start position of the index lookup. */
  @Nullable
  public Value getStart() {
    return start;
  }

  /** Returns the inclusive end position of the index lookup. */
  @Nullable
  public Value getEnd() {
    return end;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for IndexRange. */
  public static class Builder {
    private FieldPath fieldPath;
    private Value start;
    private Value end;

    /** Specifies the field path for the index lookup. */
    public Builder setFieldPath(FieldPath fieldPath) {
      this.fieldPath = fieldPath;
      return this;
    }

    /** Specifies the inclusive start position for the index lookup. */
    public Builder setStart(Value start) {
      this.start = start;
      return this;
    }

    /** Specifies the inclusive end position of the index lookup. */
    public Builder setEnd(Value end) {
      this.end = end;
      return this;
    }

    public IndexRange build() {
      hardAssert(fieldPath != null, "Field path must be specified");
      return new IndexRange(this);
    }
  }
}
