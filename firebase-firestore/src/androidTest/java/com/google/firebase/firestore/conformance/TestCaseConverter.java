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

import com.google.apphosting.datastore.testing.DatastoreTestTrace;
import com.google.firebase.firestore.conformance.model.Collection;
import com.google.firebase.firestore.conformance.model.Order;
import com.google.firebase.firestore.conformance.model.Query;
import com.google.firebase.firestore.conformance.model.QueryFilter;
import com.google.firebase.firestore.conformance.model.Result;
import com.google.firebase.firestore.conformance.model.TestCase;
import com.google.firebase.firestore.conformance.model.Where;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firestore.v1.Cursor;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.RunQueryResponse;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class TestCaseConverter {
  // Note: Most methods in this class are copied from Google3. Some modifications were made to
  // support newer query operators.

  private final ActionFilter actionFilter = new ActionFilter();

  private List<Collection> convertDatabaseContents(DatastoreTestTrace.FirestoreV1Action action) {
    LinkedHashMap<String, List<Document>> collections = new LinkedHashMap<>();
    List<RunQueryResponse> responses = action.getDatabaseContentsBeforeAction().getResponseList();

    for (RunQueryResponse response : responses) {
      if (response.hasDocument()) {
        Document document = response.getDocument();
        DocumentKey documentKey = DocumentKey.fromName(document.getName());
        String collectionId = documentKey.getCollectionGroup();
        List<Document> documents =
            collections.computeIfAbsent(collectionId, name -> new ArrayList<>());
        documents.add(document);
      }
    }

    return collections.entrySet().stream()
        .map(
            entry ->
                Collection.Builder.builder()
                    .setName(entry.getKey())
                    .setDocuments(entry.getValue())
                    .build())
        .collect(Collectors.toList());
  }

  public List<TestCase> convertTestCases(DatastoreTestTrace.TestTrace trace) {
    List<TestCase> testCases = new ArrayList<>();

    for (DatastoreTestTrace.DatastoreAction action : trace.getActionList()) {
      if (actionFilter.test(action)) {
        TestCase.Builder builder = TestCase.Builder.builder();

        DatastoreTestTrace.FirestoreV1Action firestoreAction = action.getFirestoreV1Action();

        List<Collection> collections = convertDatabaseContents(firestoreAction);
        StructuredQuery queryProto =
            firestoreAction.getRunQuery().getRequest().getStructuredQuery();
        String parentPath =
            ResourcePath.fromString(firestoreAction.getRunQuery().getRequest().getParent())
                .popFirst(5)
                .toString();
        Query query = convertQuery(queryProto, parentPath);

        // Add query collection to contents if there are no pre-existing documents.
        if (collections.isEmpty()) {
          collections.add(Collection.Builder.builder().setName(query.getCollection()).build());
        }
        builder.setCollections(collections);
        builder.setQuery(query);

        // Convert results.
        builder.setException(firestoreAction.hasStatus());

        if (!firestoreAction.hasStatus()) {
          Result.Builder result = Result.Builder.builder();
          List<RunQueryResponse> responses = firestoreAction.getRunQuery().getResponseList();
          result.setDocuments(
              responses.stream()
                  .filter(r -> r.hasDocument())
                  .map(r -> r.getDocument())
                  .collect(Collectors.toList()));
          builder.setResult(result.build());
        }

        builder.setName(String.format("%s_%d", trace.getTraceId(), action.getActionId()));
        testCases.add(builder.build());
      }
    }

    return testCases;
  }

  private Query convertQuery(StructuredQuery query, String parentPath) {
    Query.Builder queryBuilder = Query.Builder.builder();

    StructuredQuery.CollectionSelector selector = query.getFrom(0);
    queryBuilder.setCollection(parentPath + "/" + selector.getCollectionId());

    List<QueryFilter> filters = new ArrayList<>();

    // The ordering is important: where, orderBy, and the limits. Otherwise, the client will
    // complain.
    convertQueryWhere(query, filters);

    filters.addAll(
        query.getOrderByList().stream()
            .map(
                order -> {
                  Order.Builder orderBuilder = Order.Builder.builder();
                  orderBuilder.setField(order.getField().getFieldPath());
                  orderBuilder.setDirection(order.getDirection());
                  return QueryFilter.Builder.builder().setOrder(orderBuilder.build()).build();
                })
            .collect(Collectors.toList()));

    convertQueryLimits(query, filters);
    queryBuilder.setFilters(filters);
    return queryBuilder.build();
  }

  private void convertQueryWhere(StructuredQuery query, List<QueryFilter> filters) {
    if (query.hasWhere()) {
      StructuredQuery.Filter where = query.getWhere();

      if (where.hasCompositeFilter()) {
        where
            .getCompositeFilter()
            .getFiltersList()
            .forEach(filter -> convertQueryFieldFilter(filter.getFieldFilter(), filters));
      } else if (where.hasFieldFilter()) {
        convertQueryFieldFilter(where.getFieldFilter(), filters);
      } else if (where.hasUnaryFilter()) {
        convertQueryUnaryFilter(where.getUnaryFilter(), filters);
      }
    }
  }

  private void convertQueryFieldFilter(
      StructuredQuery.FieldFilter filter, List<QueryFilter> filters) {
    QueryFilter.Builder filterBuilder = QueryFilter.Builder.builder();

    Where.Builder whereBuilder = Where.Builder.builder();
    whereBuilder.setField(filter.getField().getFieldPath());
    whereBuilder.setOp(filter.getOp());
    whereBuilder.setValue(filter.getValue());

    filterBuilder.setWhere(whereBuilder.build());
    filters.add(filterBuilder.build());
  }

  private void convertQueryUnaryFilter(
      StructuredQuery.UnaryFilter filter, List<QueryFilter> filters) {
    QueryFilter.Builder filterBuilder = QueryFilter.Builder.builder();

    Where.Builder whereBuilder = Where.Builder.builder();
    whereBuilder.setField(filter.getField().getFieldPath());

    switch (filter.getOp()) {
      case IS_NAN:
        whereBuilder.setOp(StructuredQuery.FieldFilter.Operator.EQUAL);
        whereBuilder.setValue(Value.newBuilder().setDoubleValue(Double.NaN).build());
        break;
      case IS_NULL:
        whereBuilder.setOp(StructuredQuery.FieldFilter.Operator.EQUAL);
        whereBuilder.setValue(Value.newBuilder().setNullValueValue(0).build());
        break;
      case IS_NOT_NAN:
        whereBuilder.setOp(StructuredQuery.FieldFilter.Operator.NOT_EQUAL);
        whereBuilder.setValue(Value.newBuilder().setDoubleValue(Double.NaN).build());
        break;
      case IS_NOT_NULL:
        whereBuilder.setOp(StructuredQuery.FieldFilter.Operator.NOT_EQUAL);
        whereBuilder.setValue(Value.newBuilder().setNullValueValue(0).build());
    }

    filterBuilder.setWhere(whereBuilder.build());
    filters.add(filterBuilder.build());
  }

  private void convertQueryLimits(StructuredQuery query, List<QueryFilter> filters) {
    if (query.hasStartAt()) {
      Cursor startAt = query.getStartAt();
      if (startAt.getBefore()) {
        startAt
            .getValuesList()
            .forEach(value -> filters.add(QueryFilter.Builder.builder().setStartAt(value).build()));
      } else {
        startAt
            .getValuesList()
            .forEach(
                value -> filters.add(QueryFilter.Builder.builder().setStartAfter(value).build()));
      }
    }

    if (query.hasEndAt()) {
      Cursor endAt = query.getEndAt();
      if (endAt.getBefore()) {
        endAt
            .getValuesList()
            .forEach(
                value -> filters.add(QueryFilter.Builder.builder().setEndBefore(value).build()));
      } else {
        endAt
            .getValuesList()
            .forEach(value -> filters.add(QueryFilter.Builder.builder().setEndAt(value).build()));
      }
    }

    if (query.hasLimit()) {
      filters.add(
          QueryFilter.Builder.builder().setLimit((long) query.getLimit().getValue()).build());
    }
  }
}
