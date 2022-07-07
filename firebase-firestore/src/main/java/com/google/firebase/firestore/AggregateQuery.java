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
import java.util.Objects;

public final class AggregateQuery {

  private final Query query;

  AggregateQuery(@NonNull Query query, @NonNull AggregateField aggregateField) {
    this.query = query;
    if (!(aggregateField instanceof AggregateField.CountAggregateField)) {
      throw new IllegalArgumentException("unsupported aggregateField: " + aggregateField);
    }
  }

  @NonNull
  public Query getQuery() {
    return query;
  }

  @NonNull
  public Task<AggregateQuerySnapshot> get(AggregateSource source) {
    TaskCompletionSource<AggregateQuerySnapshot> tcs = new TaskCompletionSource<>();
    query
        .firestore
        .getClient()
        .runCountQuery(query.query)
        .continueWith(
            Executors.DIRECT_EXECUTOR,
            (task) -> {
              if (task.isSuccessful()) {
                tcs.setResult(new AggregateQuerySnapshot(task.getResult()));
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
    if (o == null || getClass() != o.getClass()) return false;
    AggregateQuery that = (AggregateQuery) o;
    return query.equals(that.query);
  }

  @Override
  public int hashCode() {
    return Objects.hash(query);
  }

  @Override
  public String toString() {
    return "AggregateQuery{" + "query=" + query + '}';
  }
}
