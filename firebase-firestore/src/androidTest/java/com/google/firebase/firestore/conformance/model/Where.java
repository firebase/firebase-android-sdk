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

package com.google.firebase.firestore.conformance.model;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.Value;

/** A Firestore query filter. */
@AutoValue
public abstract class Where {

  /** Name of the field to filter on. */
  public abstract String getField();

  /** Field filter operator. */
  public abstract StructuredQuery.FieldFilter.Operator getOp();

  /** The value to compare to. */
  public abstract Value getValue();

  /** Builder for {@link Where}. */
  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public static Where.Builder builder() {
      return new AutoValue_Where.Builder();
    }

    @NonNull
    public abstract Where.Builder setField(@NonNull String field);

    @NonNull
    public abstract Where.Builder setOp(@NonNull StructuredQuery.FieldFilter.Operator op);

    @NonNull
    public abstract Where.Builder setValue(@NonNull Value value);

    @NonNull
    public abstract Where build();
  }
}
