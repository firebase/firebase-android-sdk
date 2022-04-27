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

package com.google.firebase.firestore.conformance;

import static com.google.firestore.v1.StructuredQuery.FieldFilter.Operator.ARRAY_CONTAINS;

import com.google.apphosting.datastore.testing.DatastoreTestTrace;
import com.google.apphosting.datastore.testing.DatastoreTestTrace.FirestoreV1Action;
import com.google.apphosting.datastore.testing.DatastoreTestTrace.FirestoreV1Action.RunQuery;
import com.google.common.base.Splitter;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.RunQueryRequest.ConsistencySelectorCase;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.Value;
import java.util.List;
import java.util.function.Predicate;

/** Filters test traces for conformance testing. */
public final class ActionFilter implements Predicate<DatastoreTestTrace.DatastoreAction> {
  // Note: This code is copied from Google3.

  @Override
  public boolean test(DatastoreTestTrace.DatastoreAction action) {
    boolean ok = action.hasFirestoreV1Action();
    return ok && checkFirestoreAction(action.getFirestoreV1Action());
  }

  boolean checkFirestoreAction(FirestoreV1Action action) {
    boolean ok = action.hasRunQuery() && action.hasDatabaseContentsBeforeAction();

    if (ok) {
      boolean documentsOk =
          action.getDatabaseContentsBeforeAction().getResponseList().stream()
              .filter(r -> r.hasDocument())
              .map(r -> r.getDocument())
              .allMatch(this::checkDocument);
      return documentsOk && checkRunQuery(action.getRunQuery());
    }

    return false;
  }

  boolean checkRunQuery(RunQuery query) {
    Object transaction = ConsistencySelectorCase.TRANSACTION;
    boolean ok =
        !query.getRequest().getConsistencySelectorCase().equals(transaction)
            && !hasNameSpace(query.getRequest().getParent());
    return ok && checkStructuredQuery(query.getRequest().getStructuredQuery());
  }

  boolean checkStructuredQuery(StructuredQuery query) {
    boolean ok = !query.hasSelect() && query.getOffset() == 0 && query.getFromCount() == 1;
    boolean startAtOk = query.getStartAt().getValuesList().stream().allMatch(this::checkValue);
    boolean endAtOk = query.getEndAt().getValuesList().stream().allMatch(this::checkValue);

    if (ok && startAtOk && endAtOk) {
      StructuredQuery.CollectionSelector from = query.getFrom(0);
      boolean fromBad = from.getAllDescendants() || from.getCollectionId().isEmpty();
      return !fromBad && checkWhere(query.getWhere());
    }

    return false;
  }

  boolean checkWhere(StructuredQuery.Filter filter) {
    if (filter.hasFieldFilter()) {
      return checkWhereFieldFilter(filter.getFieldFilter());
    }

    if (filter.hasCompositeFilter()) {
      StructuredQuery.CompositeFilter composite = filter.getCompositeFilter();
      return composite.getFiltersList().stream()
          .allMatch(
              f -> {
                boolean ok = f.hasFieldFilter();
                return ok && checkWhereFieldFilter(f.getFieldFilter());
              });
    }

    return true;
  }

  boolean checkWhereFieldFilter(StructuredQuery.FieldFilter filter) {
    return checkValue(filter.getValue()) && !filter.getOp().equals(ARRAY_CONTAINS);
  }

  boolean checkDocument(Document d) {
    return !hasNameSpace(d.getName())
        && d.getFieldsMap().values().stream().allMatch(this::checkValue);
  }

  boolean checkValue(Value v) {
    switch (v.getValueTypeCase()) {
      case BOOLEAN_VALUE:
      case DOUBLE_VALUE:
      case GEO_POINT_VALUE:
      case INTEGER_VALUE:
      case NULL_VALUE:
      case STRING_VALUE:
        return true;
      default:
        return false;
    }
  }

  boolean hasNameSpace(String path) {
    List<String> pathComponents = Splitter.on('/').splitToList(path);
    return pathComponents.size() >= 5 && pathComponents.get(4).contains("@");
  }
}
