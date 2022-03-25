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
   * <p>An index is considered capable of serving the target when:
   *
   * <ul>
   *   <li>The target uses all index segments for its filters and orderBy clauses. The target can
   *       have additional filter and orderBy clauses, but not fewer.</b>
   *   <li>If an ArrayContains/ArrayContainsAnyfilter is used, the index must also have a
   *       corresponding {@link FieldIndex.Segment.Kind#CONTAINS} segment.
   *   <li>All directional index segments can be mapped to the target as a series of equality
   *       filters, a single inequality filter and a series of orderBy clauses.
   *   <li>The segments that represent the equality filters may appear out of order.
   *   <li>The optional segment for the inequality filter must appear after all equality segments.
   *   <li>The segments that represent that orderBy clause of the target must appear in order after
   *       all equality and inequality segments. Single orderBy clauses cannot be skipped, but a
   *       continuous orderBy suffix may be omitted.
   * </ul>
   *
   * @throws AssertionError if the index is for a different collection
   */
  public int servedByIndex(FieldIndex index) {
    hardAssert(index.getCollectionGroup().equals(collectionId), "Collection IDs do not match");

    // If there is an array element, find a matching filter.
    FieldIndex.Segment arraySegment = index.getArraySegment();
    if (arraySegment != null && !hasMatchingEqualityFilter(arraySegment)) {
      return -1;
    }

    Iterator<OrderBy> orderBys = this.orderBys.iterator();
    List<FieldIndex.Segment> segments = index.getDirectionalSegments();
    int segmentIndex = 0;

    // Process all equalities first. Equalities can appear out of order.
    for (; segmentIndex < segments.size(); ++segmentIndex) {
      // We attempt to greedily match all segments to equality filters. If a filter matches an
      // index segment, we can mark the segment as used. Since it is not possible to use the same
      // field path in both an equality and inequality/oderBy clause, we do not have to consider the
      // possibility that a matching equality segment should instead be used to map to an inequality
      // filter or orderBy clause.
      if (!hasMatchingEqualityFilter(segments.get(segmentIndex))) {
        // If we cannot find a matching filter, we need to verify whether the remaining segments map
        // to the target's inequality and its orderBy clauses.
        break;
      }
    }

    // If we already have processed all segments, all segments are used to serve the equality
    // filters and we do not need to map any segments to the target's inequality and orderBy
    // clauses.
    if (segmentIndex == segments.size()) {
      return segmentIndex;
    }

    // If there is an inequality filter, the next segment must match both the filter and the first
    // orderBy clause.
    if (inequalityFilter != null) {
      FieldIndex.Segment segment = segments.get(segmentIndex);
      if (!matchesFilter(inequalityFilter, segment) || !matchesOrderBy(orderBys.next(), segment)) {
        return -1;
      }
      ++segmentIndex;
    }

    // All remaining segments need to represent the prefix of the target's orderBy.
    for (; segmentIndex < segments.size(); ++segmentIndex) {
      FieldIndex.Segment segment = segments.get(segmentIndex);
      if (!orderBys.hasNext() || !matchesOrderBy(orderBys.next(), segment)) {
        return -1;
      }
    }

    return segmentIndex;
  }

  private boolean hasMatchingEqualityFilter(FieldIndex.Segment segment) {
    for (FieldFilter filter : equalityFilters) {
      if (matchesFilter(filter, segment)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesFilter(@Nullable FieldFilter filter, FieldIndex.Segment segment) {
    if (filter == null || !filter.getField().equals(segment.getFieldPath())) {
      return false;
    }
    boolean isArrayOperator =
        filter.getOperator().equals(FieldFilter.Operator.ARRAY_CONTAINS)
            || filter.getOperator().equals(FieldFilter.Operator.ARRAY_CONTAINS_ANY);
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
