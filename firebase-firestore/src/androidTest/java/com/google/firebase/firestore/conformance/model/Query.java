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
import java.util.Collections;
import java.util.List;

/** A query call stack to Firestore. */
@AutoValue
public abstract class Query {

  /** Name of the collection to query from. */
  public abstract String getCollection();

  /** Filters. */
  public abstract List<QueryFilter> getFilters();

  /** Builder for {@link Query}. */
  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public static Query.Builder builder() {
      return new AutoValue_Query.Builder().setFilters(Collections.emptyList());
    }

    @NonNull
    public abstract Query.Builder setCollection(@NonNull String collection);

    @NonNull
    public abstract Query.Builder setFilters(@NonNull List<QueryFilter> filters);

    @NonNull
    public abstract Query build();
  }
}
