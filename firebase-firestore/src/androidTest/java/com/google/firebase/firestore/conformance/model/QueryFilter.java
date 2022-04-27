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
import com.google.firestore.v1.Value;
import javax.annotation.Nullable;

@AutoValue
public abstract class QueryFilter {

  /** Filters. */
  @Nullable
  public abstract Where getWhere();

  /** Orders. */
  @Nullable
  public abstract Order getOrder();

  /** A starting point for the query result. */
  @Nullable
  public abstract Value getStartAt();

  /** A starting point for the query result. */
  @Nullable
  public abstract Value getStartAfter();

  /** An end point for the query result. */
  @Nullable
  public abstract Value getEndBefore();

  /** An end point for the query result. */
  @Nullable
  public abstract Value getEndAt();

  /** Max number of results to return. */
  @Nullable
  public abstract Long getLimit();

  /** Builder for {@link QueryFilter}. */
  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public static QueryFilter.Builder builder() {
      return new AutoValue_QueryFilter.Builder();
    }

    @NonNull
    public abstract QueryFilter.Builder setWhere(@NonNull Where where);

    @NonNull
    public abstract QueryFilter.Builder setOrder(@NonNull Order order);

    @NonNull
    public abstract QueryFilter.Builder setStartAt(@NonNull Value cursor);

    @NonNull
    public abstract QueryFilter.Builder setStartAfter(@NonNull Value cursor);

    @NonNull
    public abstract QueryFilter.Builder setEndBefore(@NonNull Value cursor);

    @NonNull
    public abstract QueryFilter.Builder setEndAt(@NonNull Value cursor);

    @NonNull
    public abstract QueryFilter.Builder setLimit(@NonNull Long limit);

    @NonNull
    public abstract QueryFilter build();
  }
}
