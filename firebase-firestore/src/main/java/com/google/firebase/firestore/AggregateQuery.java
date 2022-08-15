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

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Preconditions;

/**
 * A {@code AggregateQuery} computes some aggregation statistics from the result set of a base
 * {@link Query}.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class AggregateQuery {
  // The base query.
  private final Query query;

  AggregateQuery(@NonNull Query query, @NonNull AggregateField aggregateField) {
    this.query = query;
    if (!(aggregateField instanceof AggregateField.CountAggregateField)) {
      throw new IllegalArgumentException("unsupported aggregateField: " + aggregateField);
    }
  }

  /** Returns the base {@link Query} for this aggregate query. */
  @NonNull
  public Query getQuery() {
    return query;
  }

  /**
   * Executes the aggregate query and returns the results as a {@code AggregateQuerySnapshot}.
   *
   * @param source A value to configure the get behavior.
   * @return A Task that will be resolved with the results of the {@code AggregateQuery}.
   */
  @NonNull
  public Task<AggregateQuerySnapshot> get(@NonNull AggregateSource source) {
    Preconditions.checkNotNull(source, "AggregateSource must not be null");
    TaskCompletionSource<AggregateQuerySnapshot> tcs = new TaskCompletionSource<>();
    query
        .firestore
        .getClient()
        .runCountQuery(query.query)
        .continueWith(
            Executors.DIRECT_EXECUTOR,
            (task) -> {
              if (task.isSuccessful()) {
                tcs.setResult(new AggregateQuerySnapshot(this, task.getResult()));
              } else {
                tcs.setException(task.getException());
              }
              return null;
            });

    return tcs.getTask();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof AggregateQuery)) return false;
    AggregateQuery that = (AggregateQuery) o;
    return query.equals(that.query);
  }

  @Override
  public int hashCode() {
    return query.hashCode();
  }
}
