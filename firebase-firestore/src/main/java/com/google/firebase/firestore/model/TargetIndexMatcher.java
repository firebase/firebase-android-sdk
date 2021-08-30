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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A light query planner for Firestore.
 *
 * <p>This class matches a {@link FieldIndex} against a Firestore Query {@link Target}. It
 * determines whether a given index can be used to serve the specified target.
 *
 * <p>Unlike the backend, the SDK only maintains two different index kinds and does not distinguish
 * between ascending and descending indices. Instead of ordering query results by their index order,
 * the SDK re-orders all query results locally, which reduces the number of indices it needs to
 * maintain.
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
public class TargetIndexMatcher {
  // The collection ID (or collection group) of the query target.
  private final String collectionId;

  // The list of filters per field. A target can have duplicate filters for a field.
  private final Map<FieldPath, List<FieldFilter>> fieldFilterFields = new HashMap<>();

  // The set of orderBy fields in the query target.
  private final Set<FieldPath> orderByFields = new HashSet<>();

  public TargetIndexMatcher(Target target) {
    collectionId =
        target.getCollectionGroup() != null
            ? target.getCollectionGroup()
            : target.getPath().getLastSegment();

    for (Filter filter : target.getFilters()) {
      hardAssert(filter instanceof FieldFilter, "Only FieldFilters are supported");
      List<FieldFilter> currentFilters = fieldFilterFields.get(filter.getField());
      if (currentFilters == null) {
        currentFilters = new ArrayList<>();
        fieldFilterFields.put(filter.getField(), currentFilters);
      }
      currentFilters.add((FieldFilter) filter);
    }

    for (OrderBy orderBy : target.getOrderBy()) {
      orderByFields.add(orderBy.getField());
    }
  }

  /**
   * Returns whether the index can be used to serve the TargetIndexMatcher's target.
   *
   * @throws AssertionError if the index is for a different collection
   */
  public boolean servedByIndex(FieldIndex index) {
    hardAssert(index.getCollectionId().equals(collectionId), "Collection IDs do not match");
    for (int i = 0; i < index.segmentCount(); ++i) {
      if (!canUseSegment(index.getSegment(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean canUseSegment(FieldIndex.Segment segment) {
    List<FieldFilter> filters = fieldFilterFields.get(segment.getFieldPath());
    if (filters != null) {
      for (FieldFilter filter : filters) {
        switch (filter.getOperator()) {
          case ARRAY_CONTAINS:
          case ARRAY_CONTAINS_ANY:
            if (segment.getKind().equals(FieldIndex.Segment.Kind.CONTAINS)) {
              return true;
            }
            break;
          default:
            if (segment.getKind().equals(FieldIndex.Segment.Kind.ORDERED)) {
              return true;
            }
        }
      }
    }

    return orderByFields.contains(segment.getFieldPath())
        && segment.getKind().equals(FieldIndex.Segment.Kind.ORDERED);
  }
}
