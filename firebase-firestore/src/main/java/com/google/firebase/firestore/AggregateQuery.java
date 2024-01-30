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
import androidx.annotation.RestrictTo;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Preconditions;
import java.util.List;
import java.util.Objects;

/**
 * A query that calculates aggregations over an underlying query.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class AggregateQuery {

  @NonNull private final Query query;

  @NonNull private final List<AggregateField> aggregateFieldList;

  AggregateQuery(@NonNull Query query, @NonNull List<AggregateField> aggregateFieldList) {
    this.query = query;
    this.aggregateFieldList = aggregateFieldList;
  }

  /** Returns the query whose aggregations will be calculated by this object. */
  @NonNull
  public Query getQuery() {
    return query;
  }

  /** Returns the AggregateFields included inside this object. */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  @NonNull
  public List<AggregateField> getAggregateFields() {
    return aggregateFieldList;
  }

  /**
   * Executes this query.
   *
   * @param source The source from which to acquire the aggregate results.
   * @return A {@link Task} that will be resolved with the results of the query.
   */
  @NonNull
  public Task<AggregateQuerySnapshot> get(@NonNull AggregateSource source) {
    Preconditions.checkNotNull(source, "AggregateSource must not be null");
    TaskCompletionSource<AggregateQuerySnapshot> tcs = new TaskCompletionSource<>();
    query
        .firestore
        .getClient()
        .runAggregateQuery(query.query, aggregateFieldList)
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

  /**
   * Compares this object with the given object for equality.
   *
   * <p>This object is considered "equal" to the other object if and only if all of the following
   * conditions are satisfied:
   *
   * <ol>
   *   <li>{@code object} is a non-null instance of {@link AggregateQuery}.
   *   <li>{@code object} performs the same aggregations as this {@link AggregateQuery}.
   *   <li>The underlying {@link Query} of {@code object} compares equal to that of this object.
   * </ol>
   *
   * @param object The object to compare to this object for equality.
   * @return {@code true} if this object is "equal" to the given object, as defined above, or {@code
   *     false} otherwise.
   */
  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof AggregateQuery)) return false;
    AggregateQuery other = (AggregateQuery) object;
    return query.equals(other.query) && aggregateFieldList.equals(other.aggregateFieldList);
  }

  /**
   * Calculates and returns the hash code for this object.
   *
   * @return the hash code for this object.
   */
  @Override
  public int hashCode() {
    return Objects.hash(query, aggregateFieldList);
  }
}
