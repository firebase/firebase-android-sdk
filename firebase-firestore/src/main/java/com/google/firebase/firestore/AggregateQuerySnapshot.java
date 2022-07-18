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

import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * A {@code AggregateQuerySnapshot} contains results of a {@link AggregateQuery}.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class AggregateQuerySnapshot {

  private final long count;

  AggregateQuerySnapshot(long count) {
    this.count = count;
  }

  /**
   * @return The result of a document count aggregation. Returns null if no count aggregation is
   *     available in the result.
   */
  @Nullable
  public Long getCount() {
    return count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof AggregateQuerySnapshot)) return false;
    AggregateQuerySnapshot that = (AggregateQuerySnapshot) o;
    return count == that.count;
  }

  @Override
  public int hashCode() {
    return Objects.hash(count);
  }

  @Override
  public String toString() {
    return "AggregateQuerySnapshot{" + "count=" + count + '}';
  }
}
