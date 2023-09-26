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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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

  // The inequality filters of the target (if it exists).
  // Note: The sort on FieldFilters is not required. Using SortedSet here just to utilize
  // the custom comparator.
  private final SortedSet<FieldFilter> inequalityFilters;

  private final List<FieldFilter> equalityFilters;
  private final List<OrderBy> orderBys;

  public TargetIndexMatcher(Target target) {
    collectionId =
        target.getCollectionGroup() != null
            ? target.getCollectionGroup()
            : target.getPath().getLastSegment();
    orderBys = target.getOrderBy();
    inequalityFilters = new TreeSet<>((lhs, rhs) -> lhs.getField().compareTo(rhs.getField()));
    equalityFilters = new ArrayList<>();

    for (Filter filter : target.getFilters()) {
      FieldFilter fieldFilter = (FieldFilter) filter;
      if (fieldFilter.isInequality()) {
        inequalityFilters.add(fieldFilter);
      } else {
        equalityFilters.add(fieldFilter);
      }
    }
  }

  public boolean hasMultipleInequality() {
    return inequalityFilters.size() > 1;
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
  public boolean servedByIndex(FieldIndex index) {
    hardAssert(index.getCollectionGroup().equals(collectionId), "Collection IDs do not match");

    if (hasMultipleInequality()) {
      // Only single inequality is supported for now.
      // TODO(Add support for multiple inequality query): b/298441043
      return false;
    }

    // If there is an array element, find a matching filter.
    FieldIndex.Segment arraySegment = index.getArraySegment();
    if (arraySegment != null && !hasMatchingEqualityFilter(arraySegment)) {
      return false;
    }

    Iterator<OrderBy> orderBys = this.orderBys.iterator();
    List<FieldIndex.Segment> segments = index.getDirectionalSegments();
    int segmentIndex = 0;

    Set<String> equalitySegments = new HashSet<>();
    // Process all equalities first. Equalities can appear out of order.
    for (; segmentIndex < segments.size(); ++segmentIndex) {
      // We attempt to greedily match all segments to equality filters. If a filter matches an
      // index segment, we can mark the segment as used.
      if (hasMatchingEqualityFilter(segments.get(segmentIndex))) {
        equalitySegments.add(segments.get(segmentIndex).getFieldPath().canonicalString());
      } else {
        // If we cannot find a matching filter, we need to verify whether the remaining segments map
        // to the target's inequality and its orderBy clauses.
        break;
      }
    }

    // If we already have processed all segments, all segments are used to serve the equality
    // filters and we do not need to map any segments to the target's inequality and orderBy
    // clauses.
    if (segmentIndex == segments.size()) {
      return true;
    }

    if (inequalityFilters.size() > 0) {
      // Only a single inequality is currently supported. Get the only entry in the set.
      FieldFilter inequalityFilter = this.inequalityFilters.first();

      // If there is an inequality filter and the field was not in one of the equality filters
      // above, the next segment must match both the filter and the first orderBy clause.
      if (!equalitySegments.contains(inequalityFilter.getField().canonicalString())) {
        FieldIndex.Segment segment = segments.get(segmentIndex);
        if (!matchesFilter(inequalityFilter, segment)
            || !matchesOrderBy(orderBys.next(), segment)) {
          return false;
        }
      }

      ++segmentIndex;
    }

    // All remaining segments need to represent the prefix of the target's orderBy.
    for (; segmentIndex < segments.size(); ++segmentIndex) {
      FieldIndex.Segment segment = segments.get(segmentIndex);
      if (!orderBys.hasNext() || !matchesOrderBy(orderBys.next(), segment)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns a full matched field index for this target. Currently multiple inequality query is not
   * supported so function returns null.
   */
  @Nullable
  public FieldIndex buildTargetIndex() {
    if (hasMultipleInequality()) {
      return null;
    }

    // We want to make sure only one segment created for one field. For example, in case like
    // a == 3 and a > 2, Index: {a ASCENDING} will only be created once.
    Set<FieldPath> uniqueFields = new HashSet<>();
    List<FieldIndex.Segment> segments = new ArrayList<>();

    for (FieldFilter filter : equalityFilters) {
      if (filter.getField().isKeyField()) {
        continue;
      }
      boolean isArrayOperator =
          filter.getOperator().equals(FieldFilter.Operator.ARRAY_CONTAINS)
              || filter.getOperator().equals(FieldFilter.Operator.ARRAY_CONTAINS_ANY);
      if (isArrayOperator) {
        segments.add(
            FieldIndex.Segment.create(filter.getField(), FieldIndex.Segment.Kind.CONTAINS));
      } else {
        if (uniqueFields.contains(filter.getField())) {
          continue;
        }
        uniqueFields.add(filter.getField());
        segments.add(
            FieldIndex.Segment.create(filter.getField(), FieldIndex.Segment.Kind.ASCENDING));
      }
    }

    // Note: We do not explicitly check `inequalityFilter` but rather rely on the target defining an
    // appropriate `orderBys` to ensure that the required index segment is added. The query engine
    // would reject a query with an inequality filter that lacks the required order-by clause.
    for (OrderBy orderBy : orderBys) {
      // Stop adding more segments if we see a order-by on key. Typically this is the default
      // implicit order-by which is covered in the index_entry table as a separate column.
      // If it is not the default order-by, the generated index will be missing some segments
      // optimized for order-bys, which is probably fine.
      if (orderBy.getField().isKeyField()) {
        continue;
      }

      if (uniqueFields.contains(orderBy.getField())) {
        continue;
      }
      uniqueFields.add(orderBy.getField());

      segments.add(
          FieldIndex.Segment.create(
              orderBy.getField(),
              orderBy.getDirection() == OrderBy.Direction.ASCENDING
                  ? FieldIndex.Segment.Kind.ASCENDING
                  : FieldIndex.Segment.Kind.DESCENDING));
    }

    return FieldIndex.create(
        FieldIndex.UNKNOWN_ID, collectionId, segments, FieldIndex.INITIAL_STATE);
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
