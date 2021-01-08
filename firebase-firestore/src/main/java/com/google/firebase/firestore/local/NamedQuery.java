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

package com.google.firebase.firestore.local;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.SnapshotVersion;

/** Represents a named query saved by the SDK in its local storage. */
/* package */ class NamedQuery {
  private final String name;
  private final Query query;
  private final SnapshotVersion readTime;

  public NamedQuery(String name, Query query, SnapshotVersion readTime) {
    this.name = name;
    this.query = query;
    this.readTime = readTime;
  }

  /** @return The name of the query. */
  public String getName() {
    return name;
  }

  /** @return The underlying query associated with the given name. */
  public Query getQuery() {
    return query;
  }

  /** @return The time at which the results for this query were read. */
  public SnapshotVersion getReadTime() {
    return readTime;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NamedQuery that = (NamedQuery) o;

    if (!name.equals(that.name)) return false;
    if (!query.equals(that.query)) return false;
    return readTime.equals(that.readTime);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + query.hashCode();
    result = 31 * result + readTime.hashCode();
    return result;
  }
}
