// Copyright 2022 Google LLC
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

package com.google.firebase.firestore;

import android.app.Activity;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.Query.Direction;
import java.util.concurrent.Executor;

public class GroupByQuery {

  GroupByQuery() {}

  @NonNull
  public Query getQuery() {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public Task<GroupByQuerySnapshot> get() {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public Task<GroupByQuerySnapshot> get(@NonNull GroupBySource source) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public AggregateQuery.ListenConfig listen() {
    throw new RuntimeException("not implemented");
  }

  // Note: Specifying an empty list of aggregates, or not invoking this method at all, is equivalent
  // to an SQL "DISTINCT" operator.
  @NonNull
  public GroupByQuery aggregate(@NonNull AggregateField... fields) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupLimit(long maxGroups) {
    throw new RuntimeException("not implemented");
  }

  // Question: Do we want to support group-by "limitToLast" queries? In the Query class this is
  // implemented entirely client side by issuing the requested query with inverted order-by. We
  // would need to verify at runtime that the underlying query has the correct order-by clause and
  // possibly invert first/last aggregations to maintain their expected semantics.
  @NonNull
  public GroupByQuery groupLimitToLast(long maxGroups) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupStartAt(Object... fieldValues) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupStartAt(@NonNull GroupSnapshot snapshot) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupStartAfter(Object... fieldValues) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupStartAfter(@NonNull GroupSnapshot snapshot) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupEndAt(Object... fieldValues) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupEndAt(@NonNull GroupSnapshot snapshot) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupEndBefore(Object... fieldValues) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupEndBefore(@NonNull GroupSnapshot snapshot) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupOrderBy(@NonNull String groupByField) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupOrderBy(@NonNull FieldPath groupByField) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupOrderBy(@NonNull AggregateField aggregateField) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupOrderBy(@NonNull String groupByField, @NonNull Direction direction) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupOrderBy(@NonNull FieldPath groupByField, @NonNull Direction direction) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public GroupByQuery groupOrderBy(
      @NonNull AggregateField aggregateField, @NonNull Direction direction) {
    throw new RuntimeException("not implemented");
  }

  @Override
  public int hashCode() {
    throw new RuntimeException("not implemented");
  }

  @Override
  public boolean equals(Object obj) {
    throw new RuntimeException("not implemented");
  }

  public static final class ListenConfig {

    private ListenConfig() {}

    @NonNull
    public AggregateQuery.ListenConfig executeCallbacksOn(@NonNull Executor executor) {
      throw new RuntimeException("not implemented");
    }

    @NonNull
    public AggregateQuery.ListenConfig scopeTo(@NonNull Activity executor) {
      throw new RuntimeException("not implemented");
    }

    @NonNull
    public AggregateQuery.ListenConfig includeMetadataOnlyChanges(
        boolean includeMetadataOnlyChanges) {
      throw new RuntimeException("not implemented");
    }

    @NonNull
    public ListenerRegistration startDirectFromServer(
        @NonNull EventListener<AggregateQuerySnapshot> listener) {
      throw new RuntimeException("not implemented");
    }
  }
}
