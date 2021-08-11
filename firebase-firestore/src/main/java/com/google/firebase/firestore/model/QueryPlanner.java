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

import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.OrderBy;
import com.google.firebase.firestore.core.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A QueryPlanner for Firestore.
 *
 * <p>This class matches a {@link FieldIndex} against a Firestore Query {@link Target}. It
 * determines whether any part of the index can be used against the target and supports partial
 * index application for all query types. Unlike the backend, the SDK does not reject a query if it
 * is only partially indexed, and can use any existing index definitions to prefilter a result set.
 *
 * <p>The SDK only maintains two different index kinds and does not distinguish between ascending
 * and descending indices. Instead of ordering query results by their index order, the SDK re-orders
 * all query results locally, which reduces the number of indices it needs to maintain.
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
 *             <td>a ORDERED, b ORDERED</td>
 *         </tr>
 *         <tr>
 *             <td>where("a", "==", "a").where("b", "==", "b")</td>
 *             <td>a ORDERED</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", "==", "a").where("b", "==", "b")</td>
 *             <td>b ORDERED</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", ">=", "a").orderBy("a").orderBy("b")</td>
 *             <td>a ORDERED, b ORDERED</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", ">=", "a").orderBy("a").orderBy("b")</td>
 *             <td>a ORDERED</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", "array-contains", "a").orderBy("b")</td>
 *             <td>a CONTAINS, b ORDERED</td>
 *        </tr>
 *        <tr>
 *             <td>where("a", "array-contains", "a").orderBy("b")</td>
 *             <td>a CONTAINS</td>
 *        </tr>
 *     </tbody>
 * </table>
 */
public class QueryPlanner {
  private final Target target;
  private List<List<Filter>> memoizedFilterPermutations;

  public QueryPlanner(Target target) {
    this.target = target;
  }

  /**
   * Returns the part of the provided `index` that can be used to prefilter results for the current
   * Query target. The returned index matches `index` if the full index definition should be used,
   * but it could also be a prefix of the provided index.
   *
   * @throws AssertionError if the index is for a different collection
   */
  public FieldIndex getMatchingPrefix(FieldIndex index) {
    String targetCollection =
        target.getCollectionGroup() != null
            ? target.getCollectionGroup()
            : target.getPath().getLastSegment();
    hardAssert(index.getCollectionId().equals(targetCollection), "Collection IDs do not match");

    // Queries without filters only use the orderBy() clause for filtering. This is better than
    // doing a collection-level scan as an orderBy constraint filters out documents that do not
    // contain the fields being ordered by.
    if (target.getFilters().isEmpty()) {
      return processOrderBy(index, new ArrayList<>());
    }

    // A query that only contains equality clauses can be served by a zig-zag merge join on the
    // backend. While the SDK can do something similar, for now we pick one of the equalities
    // and post-filter results in LocalDocumentsView.
    // TODO(indexing): Determine whether we should implement a zig-zag join
    if (canUseMergeJoin(target)) {
      return processMergeJoin(index);
    }

    return processFilterAndOrderBy(index);
  }

  /**
   * Returns the portion of the given `index` that matches both the current target's filters and its
   * orderBys.
   */
  private FieldIndex processFilterAndOrderBy(FieldIndex index) {
    List<FieldIndex.Segment> expectedSegments = new ArrayList<>();

    // Gather the required index segments for the target's filters. Inequality filters (<=, <, >,
    // >=, !=, not in) and equality filters (=, IN) can be served by an ORDERED index.
    // ARRAY_CONTAINS and ARRAY_CONTAINS_ANY require a CONTAINS segment.
    Set<FieldPath> processedFieldPaths = new HashSet<>();
    for (Filter filter : target.getFilters()) {
      // If a field is used in more than one filter (e.g. `where('a', '>', 1).where('a', '<', 3)`)
      // only add one indexing clause.
      if (processedFieldPaths.contains(filter.getField())) {
        continue;
      }
      processedFieldPaths.add(filter.getField());
      hardAssert(filter instanceof FieldFilter, "Only FieldFilters are supported");
      expectedSegments.add(convertToIndexSegment((FieldFilter) filter));
    }

    return processOrderBy(index, expectedSegments);
  }

  /**
   * Processes a query that can be served by a merge join. These queries can be served by partial
   * indices that serve any of the equalities that the target is filtering by.
   */
  private FieldIndex processMergeJoin(FieldIndex index) {
    FieldIndex bestMatch = new FieldIndex(index.getCollectionId());

    if (memoizedFilterPermutations == null) {
      // Build a set of filters that represent all possible combinations (all subsets in all
      // orders).
      // For a simple query with two equality clauses this means that we return four different
      // filter combinations: filter1, filter2, filter1+filter2 and filter2+filter1
      // Note that while this is potentially expensive, in reality the number of filters should be
      // low.
      memoizedFilterPermutations = getPowerSets(target.getFilters());
      memoizedFilterPermutations = getPermutations(memoizedFilterPermutations);
    }

    for (List<Filter> permutation : memoizedFilterPermutations) {
      List<FieldIndex.Segment> expectedSegments = new ArrayList<>();

      for (Filter filter : permutation) {
        hardAssert(filter instanceof FieldFilter, "Only FieldFilters are supported");
        expectedSegments.add(convertToIndexSegment((FieldFilter) filter));
      }

      FieldIndex currentMatch = processOrderBy(index, expectedSegments);
      // Update the index iff it matches more constraints than the previously found index
      if (currentMatch.segmentCount() > bestMatch.segmentCount()) {
        bestMatch = currentMatch;
      }
    }

    return bestMatch;
  }

  private FieldIndex.Segment convertToIndexSegment(FieldFilter filter) {
    if (filter.getOperator().equals(Filter.Operator.ARRAY_CONTAINS)
        || filter.getOperator().equals(Filter.Operator.ARRAY_CONTAINS_ANY)) {
      return new FieldIndex.Segment(filter.getField(), FieldIndex.Segment.Kind.CONTAINS);
    } else {
      return new FieldIndex.Segment(filter.getField(), FieldIndex.Segment.Kind.ORDERED);
    }
  }

  /** Returns all permutations of the provided subsets. */
  private List<List<Filter>> getPermutations(List<List<Filter>> subsets) {
    List<List<Filter>> permutations = new ArrayList<>();
    for (List<Filter> subset : subsets) {
      addPermutations(Collections.emptyList(), subset, permutations);
    }
    return permutations;
  }

  /** Adds all permutations of `remainder` to `result`. */
  private static void addPermutations(
      List<Filter> prefix, List<Filter> remainder, List<List<Filter>> result) {
    if (remainder.isEmpty()) {
      result.add(prefix);
    } else {
      for (int i = 0; i < remainder.size(); i++) {
        List<Filter> nextPrefix = new ArrayList<>(prefix);
        nextPrefix.add(remainder.get(i));

        ArrayList<Filter> nextRemainder = new ArrayList<>(remainder.subList(0, i));
        nextRemainder.addAll(remainder.subList(i + 1, remainder.size()));

        addPermutations(nextPrefix, nextRemainder, result);
      }
    }
  }

  /** Computes the power set of `filters`. */
  private List<List<Filter>> getPowerSets(List<Filter> filters) {
    List<List<Filter>> powerSet = new ArrayList<>();

    // We use bitwise arithmetic to compute the power set and count up from 1 to 2^n.
    // The first set contains one element since an empty set of filters is not an adequate index.
    int n = 1 << filters.size();
    for (int i = 1; i < n; ++i) {
      List<Filter> subsetFilters = new ArrayList<>();
      int currentElement = 1;
      for (int j = 0; j < n; j++) {
        if ((i & currentElement) > 0) {
          subsetFilters.add(filters.get(j));
        }
        currentElement <<= 1;
      }
      powerSet.add(subsetFilters);
    }
    return powerSet;
  }

  /**
   * Adds a segment for each orderBy constraint that has not already been part of a filter.
   *
   * <p>While the SDK re-orders all results, orderBys are still used to ensure that only documents
   * are returned that contain a value for each ordered by field.
   */
  private FieldIndex processOrderBy(FieldIndex index, List<FieldIndex.Segment> expectedSegments) {
    FieldIndex bestMatch = new FieldIndex(index.getCollectionId());
    for (OrderBy orderBy : target.getOrderBy()) {
      FieldIndex.Segment missingSegment =
          new FieldIndex.Segment(orderBy.getField(), FieldIndex.Segment.Kind.ORDERED);

      boolean found = false;
      for (FieldIndex.Segment segment : expectedSegments) {
        if (segment.equals(missingSegment)) {
          found = true;
          break;
        }
      }

      if (!found) {
        expectedSegments.add(missingSegment);
      }

      int matchingSegmentCount = validateSegments(index, expectedSegments);
      if (matchingSegmentCount > bestMatch.segmentCount()) {
        bestMatch = index.prefix(matchingSegmentCount);
      }
    }

    return bestMatch;
  }

  /**
   * Validated that the segments from the index match the constraints of the target.
   *
   * @return the number of segments that match from the index
   */
  private int validateSegments(FieldIndex index, List<FieldIndex.Segment> expectedSegments) {
    int segments = 0;

    Iterator<FieldIndex.Segment> expectedSegmentsIt = expectedSegments.iterator();
    for (FieldIndex.Segment existingSegment : index) {
      if (!expectedSegmentsIt.hasNext()) {
        break;
      }

      FieldIndex.Segment expectedSegment = expectedSegmentsIt.next();
      if (!existingSegment.equals(expectedSegment)) {
        break;
      }

      ++segments;
    }

    return segments;
  }

  /**
   * Determines whether a merge join can be used. Returns true if the query only contains equality
   * filters.
   */
  private boolean canUseMergeJoin(Target target) {
    for (Filter filter : target.getFilters()) {
      hardAssert(filter instanceof FieldFilter, "Only FieldFilters are supported");
      FieldFilter fieldFilter = (FieldFilter) filter;
      switch (fieldFilter.getOperator()) {
        case LESS_THAN:
        case LESS_THAN_OR_EQUAL:
        case GREATER_THAN:
        case GREATER_THAN_OR_EQUAL:
          return false;
      }
    }
    return true;
  }
}
