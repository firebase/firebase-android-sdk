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

package com.google.firebase.firestore.bundle;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;

/** A bundled query represents a query target and its limit. */
public class BundledQuery implements BundleElement {
  private final Target target;
  private final Query.LimitType limitType;

  public BundledQuery(Target target, Query.LimitType limitType) {
    this.target = target;
    this.limitType = limitType;
  }

  /**
   * Returns the target that represents the user-issued query. OrderBy constraints are not inverted
   * for limitToLast() queries.
   */
  public Target getTarget() {
    return target;
  }

  /** Returns the user provided limit type. */
  public Query.LimitType getLimitType() {
    return limitType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BundledQuery that = (BundledQuery) o;

    if (!target.equals(that.target)) return false;
    return limitType == that.limitType;
  }

  @Override
  public int hashCode() {
    int result = target.hashCode();
    result = 31 * result + limitType.hashCode();
    return result;
  }
}
