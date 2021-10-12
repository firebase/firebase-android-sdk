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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.OrderBy;
import com.google.firebase.firestore.core.Target;
import java.util.Iterator;
import java.util.List;

/**
 * A light query planner for Firestore.
 *
 * <p>This class matches a {@link FieldIndex} against a Firestore Query {@link Target}. It
 * determines whether a given index can be used to serve the specified target.
 *
 * <p>The following table showcases some possible index configurations:
 *
 * <table>
 *     <thead>
 *         <tr>
 *             <td>Query</td>
 *             <td>Index</td>
 *         </tr>
 *     </thead>
 *     <tbody>
 *         <tr>
 *             <td>where("a", "==", "a").where("b", "==", "b")</td>
 *             <td>a ASCENDING, b DESCENDING</td>
 *         </tr>
 *         <tr>
 *             <td>where("a", "==", "a").where("b", "==", "b")</td>
 *             <td>a ASCENDING</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", "==", "a").where("b", "==", "b")</td>
 *             <td>b DESCENDING</td>
 *        </tr>
 *        <tr>
 *            <td>where("a", ">=", "a").orderBy("a")</td>
 *            <td>a ASCENDING</td>
 *        </tr>
 *       <tr>
 *            <td>where("a", ">=", "a").orderBy("a", "descending")</td>
 *            <td>a DESCENDING</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", ">=", "a").orderBy("a").orderBy("b")</td>
 *             <td>a ASCENDING, b ASCENDING</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", ">=", "a").orderBy("a").orderBy("b")</td>
 *             <td>a ASCENDING</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", "array-contains", "a").orderBy("b")</td>
 *             <td>a CONTAINS, b ASCENDING</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", "array-contains", "a").orderBy("b")</td>
 *             <td>a CONTAINS</td>
 *        </tr>
 *     </tbody>
 * </table>
 */
public class TargetIndexMatcher {
  // The collection ID (or collection group) of the query target.
  private final String collectionId;

  private final List<Filter> filters;
  private final List<OrderBy> orderBys;

  public TargetIndexMatcher(Target target) {
    collectionId =
        target.getCollectionGroup() != null
            ? target.getCollectionGroup()
            : target.getPath().getLastSegment();
    filters = target.getFilters();
    orderBys = target.getOrderBy();
  }

  /**
   * Returns whether the index can be used to serve the TargetIndexMatcher's target.
   *
   * @throws AssertionError if the index is for a different collection
   */
  public boolean servedByIndex(FieldIndex index) {
    hardAssert(index.getCollectionGroup().equals(collectionId), "Collection IDs do not match");

    Iterator<Filter> filters = this.filters.iterator();
    Iterator<OrderBy> orderBys = this.orderBys.iterator();

    FieldFilter currentFilter = filters.hasNext() ? (FieldFilter) filters.next() : null;
    OrderBy currentOrderBy = orderBys.hasNext() ? orderBys.next() : null;

    // Validate that every segment of the index has a corresponding clause in the provided target.
    // While a target can have additional filters and orderBy constraints, it cannot have fewer.
    for (int i = 0; i < index.segmentCount(); ) {
      FieldIndex.Segment segment = index.getSegment(i);

      boolean consumedOrderBy = false;
      boolean consumedFilter = false;

      if (currentFilter != null && currentFilter.isInequality()) {
        // An inequality filter requires a matching ordering constraint.
        if (matchesFilter(currentFilter, segment) && matchesOrderBy(currentOrderBy, segment)) {
          consumedOrderBy = true;
          consumedFilter = true;
        }
      } else {
        consumedFilter = matchesFilter(currentFilter, segment);
        consumedOrderBy = matchesOrderBy(currentOrderBy, segment);
      }

      if (!consumedFilter && !consumedOrderBy) {
        // The backend can use merge joins to serve queries with multiple equalities. This means
        // that a query for "foo == 1 AND bar == 2" can skip "foo" and be served by "bar". We
        // implement the same behavior by allowing a query if at least one equality clause is served
        // by the index.
        if (currentFilter != null && currentFilter.getOperator().equals(Filter.Operator.EQUAL)) {
          currentFilter = filters.hasNext() ? (FieldFilter) filters.next() : null;
          continue; // We process the next filter, but stay on the current index segment
        } else {
          return false;
        }
      } else {
        if (consumedFilter) {
          currentFilter = filters.hasNext() ? (FieldFilter) filters.next() : null;
        }
        if (consumedOrderBy) {
          currentOrderBy = orderBys.hasNext() ? orderBys.next() : null;
        }
      }
      ++i;
    }

    return true;
  }

  private boolean matchesFilter(@Nullable FieldFilter filter, FieldIndex.Segment segment) {
    if (filter == null || !filter.getField().equals(segment.getFieldPath())) {
      return false;
    }
    boolean isArrayOoperator =
        filter.getOperator().equals(Filter.Operator.ARRAY_CONTAINS)
            || filter.getOperator().equals(Filter.Operator.ARRAY_CONTAINS_ANY);
    return segment.getKind().equals(FieldIndex.Segment.Kind.CONTAINS) == isArrayOoperator;
  }

  private boolean matchesOrderBy(@Nullable OrderBy orderBy, FieldIndex.Segment segment) {
    if (orderBy == null || !orderBy.getField().equals(segment.getFieldPath())) {
      return false;
    }
    return (segment.getKind().equals(FieldIndex.Segment.Kind.ASCENDING)
            && orderBy.getDirection().equals(OrderBy.Direction.ASCENDING))
        || (segment.getKind().equals(FieldIndex.Segment.Kind.DESCENDING)
            && orderBy.getDirection().equals(OrderBy.Direction.DESCENDING));
  }
}
