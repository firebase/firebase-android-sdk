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
import java.util.ArrayList;
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

  private @Nullable FieldFilter inequalityFilter;
  private final List<FieldFilter> equalityFilters;
  private final List<OrderBy> orderBys;

  public TargetIndexMatcher(Target target) {
    collectionId =
        target.getCollectionGroup() != null
            ? target.getCollectionGroup()
            : target.getPath().getLastSegment();
    orderBys = target.getOrderBy();
    inequalityFilter = null;
    equalityFilters = new ArrayList<>();

    for (Filter filter : target.getFilters()) {
      FieldFilter fieldFilter = (FieldFilter) filter;
      if (fieldFilter.isInequality()) {
        hardAssert(
            inequalityFilter == null || inequalityFilter.getField().equals(fieldFilter.getField()),
            "Only a single inequality is supported");
        inequalityFilter = fieldFilter;
      } else {
        equalityFilters.add(fieldFilter);
      }
    }
  }

  /**
   * Returns whether the index can be used to serve the TargetIndexMatcher's target.
   *
   * @throws AssertionError if the index is for a different collection
   */
  public boolean servedByIndex(FieldIndex index) {
    hardAssert(index.getCollectionGroup().equals(collectionId), "Collection IDs do not match");

    // If there is an array element, find a matching filter.
    FieldIndex.Segment arraySegment = index.getArraySegment();
    if (arraySegment != null && getMatchingFilter(equalityFilters, arraySegment) == null) {
      return false;
    }

    // Process all equalities first. Equalities can appear out of order.
    Iterator<OrderBy> orderBys = this.orderBys.iterator();
    List<FieldIndex.Segment> segments = index.getDirectionalSegments();
    int segmentIndex = 0;

    for (; segmentIndex < segments.size(); ++segmentIndex) {
      FieldFilter matchingFilter = getMatchingFilter(equalityFilters, segments.get(segmentIndex));
      if (matchingFilter != null) {
        equalityFilters.remove(matchingFilter);
      } else {
        break; // Try inequalities and orderBys
      }
    }

    if (segmentIndex == segments.size()) {
      return true;
    }

    // Process the optional inequality, which needs to have a matching orderBy.
    if (inequalityFilter != null) {
      FieldIndex.Segment segment = segments.get(segmentIndex);
      if (!matchesFilter(inequalityFilter, segment) || !matchesOrderBy(orderBys.next(), segment)) {
        return false;
      }
      ++segmentIndex;
    }

    // Process all remaining orderBys. OrderBys need to appear in order.
    for (; segmentIndex < segments.size(); ++segmentIndex) {
      FieldIndex.Segment segment = segments.get(segmentIndex);
      if (!orderBys.hasNext() || !matchesOrderBy(orderBys.next(), segment)) {
        return false;
      }
    }

    return true;
  }

  @Nullable
  private FieldFilter getMatchingFilter(List<FieldFilter> filters, FieldIndex.Segment segment) {
    for (FieldFilter filter : filters) {
      if (matchesFilter(filter, segment)) {
        return filter;
      }
    }
    return null;
  }

  private boolean matchesFilter(@Nullable FieldFilter filter, FieldIndex.Segment segment) {
    if (filter == null || !filter.getField().equals(segment.getFieldPath())) {
      return false;
    }
    boolean isArrayOperator =
        filter.getOperator().equals(Filter.Operator.ARRAY_CONTAINS)
            || filter.getOperator().equals(Filter.Operator.ARRAY_CONTAINS_ANY);
    return segment.getKind().equals(FieldIndex.Segment.Kind.CONTAINS) == isArrayOperator;
  }

  private boolean matchesOrderBy(OrderBy orderBy, FieldIndex.Segment segment) {
    if (!orderBy.getField().equals(segment.getFieldPath())) {
      return false;
    }
    return (segment.getKind().equals(FieldIndex.Segment.Kind.ASCENDING)
            && orderBy.getDirection().equals(OrderBy.Direction.ASCENDING))
        || (segment.getKind().equals(FieldIndex.Segment.Kind.DESCENDING)
            && orderBy.getDirection().equals(OrderBy.Direction.DESCENDING));
  }
}
