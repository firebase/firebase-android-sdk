// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.core;

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.List;

/** A QueryMatcher executes a Query against a Firestore Document. */
public class QueryMatcher {
  private final String collectionGroup;
  private final ResourcePath path;
  private final List<Filter> filters;
  private final List<OrderBy> orderBy;
  private final Bound startAt;
  private final Bound endAt;

  /** Creates a new QueryMatcher based on the current state of the query. */
  static QueryMatcher fromQuery(Query query) {
    return new QueryMatcher(
        query.getCollectionGroup(),
        query.getPath(),
        query.getFilters(),
        query.getOrderBy(),
        query.getStartAt(),
        query.getEndAt());
  }

  private QueryMatcher(
      String collectionGroup,
      ResourcePath path,
      List<Filter> filters,
      List<OrderBy> orderBy,
      Bound startAt,
      Bound endAt) {
    this.collectionGroup = collectionGroup;
    this.path = path;
    this.filters = filters;
    this.orderBy = orderBy;
    this.startAt = startAt;
    this.endAt = endAt;
  }

  private boolean matchesPathAndCollectionGroup(Document doc) {
    ResourcePath docPath = doc.getKey().getPath();
    if (collectionGroup != null) {
      // NOTE: this.path is currently always empty since we don't expose Collection
      // Group queries rooted at a document path yet.
      return doc.getKey().hasCollectionId(collectionGroup) && path.isPrefixOf(docPath);
    } else if (DocumentKey.isDocumentKey(path)) {
      return path.equals(docPath);
    } else {
      return path.isPrefixOf(docPath) && path.length() == docPath.length() - 1;
    }
  }

  private boolean matchesFilters(Document doc) {
    for (Filter filter : filters) {
      if (!filter.matches(doc)) {
        return false;
      }
    }
    return true;
  }

  /** A document must have a value for every ordering clause in order to show up in the results. */
  private boolean matchesOrderBy(Document doc) {
    for (OrderBy order : orderBy) {
      // order by key always matches
      if (!order.getField().equals(FieldPath.KEY_PATH) && (doc.getField(order.field) == null)) {
        return false;
      }
    }
    return true;
  }

  /** Makes sure a document is within the bounds, if provided. */
  private boolean matchesBounds(Document doc) {
    if (startAt != null && !startAt.sortsBeforeDocument(orderBy, doc)) {
      return false;
    }
    if (endAt != null && endAt.sortsBeforeDocument(orderBy, doc)) {
      return false;
    }
    return true;
  }

  /** Returns true if the document matches the constraints of this query. */
  public boolean matches(Document doc) {
    return matchesPathAndCollectionGroup(doc)
        && matchesOrderBy(doc)
        && matchesFilters(doc)
        && matchesBounds(doc);
  }
}
