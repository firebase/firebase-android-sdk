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

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import java.util.Objects;

/**
 * The results of executing an {@link AggregateQuery}.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class AggregateQuerySnapshot {

  private final long count;
  private final AggregateQuery query;

  AggregateQuerySnapshot(@NonNull AggregateQuery query, long count) {
    checkNotNull(query);
    this.query = query;
    this.count = count;
  }

  /** Returns the query that was executed to produce this result. */
  @NonNull
  public AggregateQuery getQuery() {
    return query;
  }

  /** Returns the number of documents in the result set of the query. */
  public long getCount() {
    return count;
  }

  /**
   * Compares this object with the given object for equality.
   *
   * <p>This object is considered "equal" to the other object if and only if all of the following
   * conditions are satisfied:
   *
   * <ol>
   *   <li>{@code object} is a non-null instance of {@link AggregateQuerySnapshot}.</li>
   *   <li>{@code object} has the same {@link AggregateQuery} as this object.</li>
   *   <li>{@code object} has the same results as this object.</li>
   * </ol>
   *
   * @param object The object to compare to this object for equality.
   * @return {@code true} if this object is "equal" to the given object, as defined above, or
   * {@code false} otherwise.
   */
  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof AggregateQuerySnapshot)) return false;
    AggregateQuerySnapshot other = (AggregateQuerySnapshot) object;
    return count == other.count && query.equals(other.query);
  }

  /**
   * Calculates and returns the hash code for this object.
   * @return the hash code for this object.
   */
  @Override
  public int hashCode() {
    return Objects.hash(count, query);
  }
}
