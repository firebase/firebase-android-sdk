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

import static com.google.firebase.firestore.testutil.TestUtil.assertDoesNotThrow;
import static com.google.firebase.firestore.testutil.TestUtil.expectError;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.firebase.firestore.core.Query;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class QueryPlannerTest {
  List<Query> queriesWithEqualities =
      Arrays.asList(
          query("collId").filter(filter("a", "==", "a")),
          query("collId").filter(filter("a", "in", Collections.singletonList("a"))));

  List<Query> queriesWithInequalities =
      Arrays.asList(
          query("collId").filter(filter("a", "<", "a")),
          query("collId").filter(filter("a", "<=", "a")),
          query("collId").filter(filter("a", ">=", "a")),
          query("collId").filter(filter("a", ">", "a")),
          query("collId").filter(filter("a", "!=", "a")),
          query("collId").filter(filter("a", "not-in", Collections.singletonList("a"))));

  List<Query> queriesWithArrayContains =
      Arrays.asList(
          query("collId").filter(filter("a", "array-contains", "a")),
          query("collId")
              .filter(filter("a", "array-contains-any", Collections.singletonList("a"))));

  @Test
  public void canUseMergeJoin() {
    Query q = query("collId").filter(filter("a", "==", 1)).filter(filter("b", "==", 2));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(q, "b", FieldIndex.Segment.Kind.ORDERED);

    q =
        query("collId")
            .filter(filter("a", "==", 1))
            .filter(filter("b", "==", 2))
            .orderBy(orderBy("__name__", "desc"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "__name__", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(
        q, "b", FieldIndex.Segment.Kind.ORDERED, "__name__", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void canUsePartialIndex() {
    Query q = query("collId").orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);

    q = query("collId").orderBy(orderBy("a")).orderBy(orderBy("b"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "b", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void equalitiesWithDefaultOrder() {
    for (Query query : queriesWithEqualities) {
      validateServesTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
      validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ORDERED);
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
    }
  }

  @Test
  public void equalitiesWithAscendingOrder() {
    Stream<Query> queriesWithEqualitiesAndDescendingOrder =
        queriesWithEqualities.stream().map(q -> q.orderBy(orderBy("a", "asc")));

    queriesWithEqualitiesAndDescendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void equalitiesWithDescendingOrder() {
    Stream<Query> queriesWithEqualitiesAndDescendingOrder =
        queriesWithEqualities.stream().map(q -> q.orderBy(orderBy("a", "desc")));

    queriesWithEqualitiesAndDescendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void inequalitiesWithDefaultOrder() {
    for (Query query : queriesWithInequalities) {
      validateServesTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
      validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ORDERED);
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
    }
  }

  @Test
  public void inequalitiesWithAscendingOrder() {
    Stream<Query> queriesWithInequalitiesAndDescendingOrder =
        queriesWithInequalities.stream().map(q -> q.orderBy(orderBy("a", "asc")));

    queriesWithInequalitiesAndDescendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void inequalitiesWithDescendingOrder() {
    Stream<Query> queriesWithInequalitiesAndDescendingOrder =
        queriesWithInequalities.stream().map(q -> q.orderBy(orderBy("a", "desc")));

    queriesWithInequalitiesAndDescendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ORDERED);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void inequalityUsesSingleFieldIndex() {
    Query q = query("collId").filter(filter("a", ">", 1)).filter(filter("a", "<", 10));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void inQueryUsesMergeJoin() {
    Query q =
        query("collId").filter(filter("a", "in", Arrays.asList(1, 2))).filter(filter("b", "==", 5));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(q, "b", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "b", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void validatesCollection() {
    {
      QueryPlanner queryPlanner = new QueryPlanner(query("collId").toTarget());
      FieldIndex fieldIndex = new FieldIndex("collId");
      assertDoesNotThrow(() -> queryPlanner.getMatchingPrefix(fieldIndex));
    }

    {
      QueryPlanner queryPlanner = new QueryPlanner(new Query(path(""), "collId").toTarget());
      FieldIndex fieldIndex = new FieldIndex("collId");
      assertDoesNotThrow(() -> queryPlanner.getMatchingPrefix(fieldIndex));
    }

    {
      QueryPlanner queryPlanner = new QueryPlanner(query("collId2").toTarget());
      FieldIndex fieldIndex = new FieldIndex("collId");
      expectError(
          () -> queryPlanner.getMatchingPrefix(fieldIndex),
          "INTERNAL ASSERTION FAILED: Collection IDs do not match");
    }
  }

  @Test
  public void withArrayContains() {
    for (Query query : queriesWithArrayContains) {
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.ORDERED);
      validateServesTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
    }
  }

  @Test
  public void withArrayContainsAndOrderBy() {
    Query queriesMultipleFilters =
        query("collId")
            .filter(filter("a", "array-contains", "a"))
            .filter(filter("a", ">", "b"))
            .orderBy(orderBy("a", "asc"));
    QueryPlanner queryPlanner = new QueryPlanner(queriesMultipleFilters.toTarget());

    FieldIndex matching =
        new FieldIndex("collId")
            .withAddedField(field("a"), FieldIndex.Segment.Kind.CONTAINS)
            .withAddedField(field("a"), FieldIndex.Segment.Kind.ORDERED);
    assertEquals(matching, queryPlanner.getMatchingPrefix(matching));
  }

  @Test
  public void withEqualityAndDescendingOrder() {
    Query q = query("collId").filter(filter("a", "==", 1)).orderBy(orderBy("__name__", "desc"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "__name__", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withOrderBy() {
    Query q = query("collId").orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);

    q = query("collId").orderBy(orderBy("a", "desc"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);

    q = query("collId").orderBy(orderBy("a")).orderBy(orderBy("__name__"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "__name__", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withNotEquals() {
    Query q = query("collId").filter(filter("a", "!=", 1));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);

    q = query("collId").filter(filter("a", "!=", 1)).orderBy(orderBy("a")).orderBy(orderBy("b"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "b", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withMultipleFilters() {
    Query queriesMultipleFilters =
        query("collId").filter(filter("a", "==", "a")).filter(filter("b", ">", "b"));
    QueryPlanner queryPlanner = new QueryPlanner(queriesMultipleFilters.toTarget());

    FieldIndex fieldIndex = new FieldIndex("collId");

    FieldIndex matching = fieldIndex.withAddedField(field("a"), FieldIndex.Segment.Kind.ORDERED);
    assertEquals(matching, queryPlanner.getMatchingPrefix(matching));

    matching = matching.withAddedField(field("b"), FieldIndex.Segment.Kind.ORDERED);
    assertEquals(matching, queryPlanner.getMatchingPrefix(matching));

    FieldIndex notMatching = fieldIndex.withAddedField(field("b"), FieldIndex.Segment.Kind.ORDERED);
    assertNotEquals(notMatching, queryPlanner.getMatchingPrefix(matching));
  }

  @Test
  public void withMultipleFiltersAndOrderBy() {
    Query queriesMultipleFilters =
        query("collId")
            .filter(filter("a1", "==", "a"))
            .filter(filter("a2", ">", "b"))
            .orderBy(orderBy("a2", "asc"));
    QueryPlanner queryPlanner = new QueryPlanner(queriesMultipleFilters.toTarget());

    FieldIndex index =
        new FieldIndex("collId")
            .withAddedField(field("a1"), FieldIndex.Segment.Kind.ORDERED)
            .withAddedField(field("a2"), FieldIndex.Segment.Kind.ORDERED);
    assertEquals(index, queryPlanner.getMatchingPrefix(index));
  }

  @Test
  public void withMultipleInequalities() {
    Query q =
        query("collId")
            .filter(filter("a", ">=", 1))
            .filter(filter("a", "==", 5))
            .filter(filter("a", "<=", 10));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withMultipleNotIn() {
    Query q =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .filter(filter("a", ">=", 2));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withMultipleOrderBys() {
    Query q =
        query("collId")
            .orderBy(orderBy("fff"))
            .orderBy(orderBy("bar", "desc"))
            .orderBy(orderBy("__name__"));
    validateServesTarget(
        q,
        "fff",
        FieldIndex.Segment.Kind.ORDERED,
        "bar",
        FieldIndex.Segment.Kind.ORDERED,
        "__name__",
        FieldIndex.Segment.Kind.ORDERED);

    q =
        query("collId")
            .orderBy(orderBy("foo"))
            .orderBy(orderBy("bar"))
            .orderBy(orderBy("__name__", "desc"));
    validateServesTarget(
        q,
        "foo",
        FieldIndex.Segment.Kind.ORDERED,
        "bar",
        FieldIndex.Segment.Kind.ORDERED,
        "__name__",
        FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withInAndNotIn() {
    Query q =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .filter(filter("b", "in", Arrays.asList(1, 2, 3)));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(q, "b", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(
        q, "b", FieldIndex.Segment.Kind.ORDERED, "a", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "b", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withEqualityAndDifferentOrderBy() {
    Query q =
        query("collId")
            .filter(filter("foo", "==", ""))
            .filter(filter("bar", "==", ""))
            .orderBy(orderBy("qux"));
    validateServesTarget(
        q,
        "foo",
        FieldIndex.Segment.Kind.ORDERED,
        "bar",
        FieldIndex.Segment.Kind.ORDERED,
        "qux",
        FieldIndex.Segment.Kind.ORDERED);

    q =
        query("collId")
            .filter(filter("aaa", "==", ""))
            .filter(filter("qqq", "==", ""))
            .filter(filter("ccc", "==", ""))
            .orderBy(orderBy("fff", "desc"))
            .orderBy(orderBy("bbb"));
    validateServesTarget(
        q,
        "aaa",
        FieldIndex.Segment.Kind.ORDERED,
        "qqq",
        FieldIndex.Segment.Kind.ORDERED,
        "ccc",
        FieldIndex.Segment.Kind.ORDERED,
        "fff",
        FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withEqualsAndNotIn() {
    Query q =
        query("collId")
            .filter(filter("a", "==", 1))
            .filter(filter("b", "not-in", Arrays.asList(1, 2, 3)));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "b", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withInAndOrderBy() {
    Query q =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ORDERED, "b", FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void withInAndOrderBySameField() {
    Query q =
        query("collId").filter(filter("a", "in", Arrays.asList(1, 2, 3))).orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
  }

  private void validateServesTarget(
      Query query, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    FieldIndex expectedIndex = getSegments(field, kind, fieldsAndKind);
    QueryPlanner queryPlanner = new QueryPlanner(query.toTarget());
    FieldIndex actualIndex = queryPlanner.getMatchingPrefix(expectedIndex);
    assertEquals(expectedIndex, actualIndex);
  }

  private void validateDoesNotServeTarget(
      Query query, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    FieldIndex expectedIndex = getSegments(field, kind, fieldsAndKind);
    QueryPlanner queryPlanner = new QueryPlanner(query.toTarget());
    FieldIndex actualIndex = queryPlanner.getMatchingPrefix(expectedIndex);
    assertEquals(0, actualIndex.segmentCount());
  }

  private FieldIndex getSegments(
      String field, FieldIndex.Segment.Kind kind, Object[] fieldAndKind) {
    FieldIndex index = new FieldIndex("collId").withAddedField(field(field), kind);
    for (int i = 0; i < fieldAndKind.length; i += 2) {
      index =
          index.withAddedField(
              field((String) fieldAndKind[i]), (FieldIndex.Segment.Kind) fieldAndKind[i + 1]);
    }
    return index;
  }
}
