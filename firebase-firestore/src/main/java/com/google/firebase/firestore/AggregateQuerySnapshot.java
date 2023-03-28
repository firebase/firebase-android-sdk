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
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.firestore.v1.Value;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * The results of executing an {@link AggregateQuery}.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class AggregateQuerySnapshot {

  @Nonnull private final AggregateQuery query;

  @Nonnull private final Map<String, Value> data;

  AggregateQuerySnapshot(@NonNull AggregateQuery query, @NonNull Map<String, Value> data) {
    checkNotNull(query);
    this.query = query;
    this.data = data;
  }

  /** Returns the query that was executed to produce this result. */
  @NonNull
  public AggregateQuery getQuery() {
    return query;
  }

  /** Returns the number of documents in the result set of the underlying query. */
  public long getCount() {
    return get(AggregateField.count());
  }

  /**
   * Returns the result of the given aggregation from the server without coercion of data types.
   * Throws java.lang.RuntimeException if the `aggregateField` was not requested when calling
   * `query.aggregate(...)`.
   *
   * @param aggregateField The aggregation for which the value is requested.
   * @return The result of the given aggregation.
   */
  @Nullable
  public Object get(@Nonnull AggregateField aggregateField) {
    if (!data.containsKey(aggregateField.getAlias())) {
      throw new IllegalArgumentException(
          "'"
              + aggregateField.getOperator()
              + "("
              + aggregateField.getFieldPath()
              + ")"
              + "' was not requested in the aggregation query.");
    }
    Value value = data.get(aggregateField.getAlias());
    if (value.hasNullValue()) {
      return null;
    } else if (value.hasDoubleValue()) {
      return value.getDoubleValue();
    } else if (value.hasIntegerValue()) {
      return value.getIntegerValue();
    } else {
      throw new IllegalStateException("Found aggregation result that is not an integer nor double");
    }
  }

  /**
   * Returns the number of documents in the result set of the underlying query.
   *
   * @param countAggregateField The count aggregation for which the value is requested.
   * @return The result of the given count aggregation.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public long get(@Nonnull AggregateField.CountAggregateField countAggregateField) {
    Object value = get((AggregateField) countAggregateField);
    if (value == null) {
      throw new IllegalArgumentException(
          "RunAggregationQueryResponse alias " + countAggregateField.getAlias() + " is null");
    } else if (!(value instanceof Long)) {
      throw new IllegalArgumentException(
          "RunAggregationQueryResponse alias "
              + countAggregateField.getAlias()
              + " has incorrect type: "
              + value.getClass().getName());
    }
    return (long) value;
  }

  /**
   * Returns the result of the given average aggregation. Since the result of an average aggregation
   * performed by the server is always a double, this convenience overload can be used in lieu of
   * the above `get` method. Throws java.lang.RuntimeException if the `aggregateField` was not
   * requested when calling `query.aggregate(...)`.
   *
   * @param averageAggregateField The average aggregation for which the value is requested.
   * @return The result of the given average aggregation.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  @Nullable
  public Double get(@Nonnull AggregateField.AverageAggregateField averageAggregateField) {
    return (Double) get((AggregateField) averageAggregateField);
  }

  /**
   * Returns the result of the given aggregation as a double. Coerces all numeric values and throws
   * a RuntimeException if the result of the aggregate is non-numeric. In the case of coercion of
   * long to double, uses java.lang.Long.doubleValue to perform the conversion, and may result in a
   * loss of precision.
   *
   * @param aggregateField The aggregation for which the value is requested.
   * @return The result of the given average aggregation as a double.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  @Nullable
  public Double getDouble(@Nonnull AggregateField aggregateField) {
    Number result = (Number) get(aggregateField);
    return result == null ? null : result.doubleValue();
  }

  /**
   * Returns the result of the given aggregation as a long. Coerces all numeric values and throws a
   * RuntimeException if the result of the aggregate is non-numeric. In case of coercion of double
   * to long, uses java.lang.Double.longValue to perform the conversion.
   *
   * @param aggregateField The aggregation for which the value is requested.
   * @return The result of the given average aggregation as a long.
   */
  @Nullable
  public Long getLong(@Nonnull AggregateField aggregateField) {
    Number result = (Number) get(aggregateField);
    return result == null ? null : result.longValue();
  }

  /**
   * Compares this object with the given object for equality.
   *
   * <p>This object is considered "equal" to the other object if and only if all of the following
   * conditions are satisfied:
   *
   * <ol>
   *   <li>{@code object} is a non-null instance of {@link AggregateQuerySnapshot}.
   *   <li>The {@link AggregateQuery} of {@code object} compares equal to that of this object.
   *   <li>{@code object} has the same results as this object.
   * </ol>
   *
   * @param object The object to compare to this object for equality.
   * @return {@code true} if this object is "equal" to the given object, as defined above, or {@code
   *     false} otherwise.
   */
  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof AggregateQuerySnapshot)) return false;
    AggregateQuerySnapshot other = (AggregateQuerySnapshot) object;
    return query.equals(other.query) && data.equals(other.data);
  }

  /**
   * Calculates and returns the hash code for this object.
   *
   * @return the hash code for this object.
   */
  @Override
  public int hashCode() {
    return Objects.hash(query, data);
  }
}
