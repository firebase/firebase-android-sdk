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
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
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
public class TargetIndexMatcherTest {
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

  List<Query> queriesWithOrderBys =
      Arrays.asList(
          query("collId").orderBy(orderBy("a")),
          query("collId").orderBy(orderBy("a", "desc")),
          query("collId").orderBy(orderBy("a", "asc")),
          query("collId").orderBy(orderBy("a")).orderBy(orderBy("__name__")),
          query("collId").filter(filter("a", "array-contains", "a")).orderBy(orderBy("b")));

  @Test
  public void canUseMergeJoin() {
    Query q = query("collId").filter(filter("a", "==", 1)).filter(filter("b", "==", 2));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(q, "b", FieldIndex.Segment.Kind.ASCENDING);

    q =
        query("collId")
            .filter(filter("a", "==", 1))
            .filter(filter("b", "==", 2))
            .orderBy(orderBy("__name__", "desc"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "__name__", FieldIndex.Segment.Kind.DESCENDING);
    validateServesTarget(
        q, "b", FieldIndex.Segment.Kind.ASCENDING, "__name__", FieldIndex.Segment.Kind.DESCENDING);
  }

  @Test
  public void canUsePartialIndex() {
    Query q = query("collId").orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);

    q = query("collId").orderBy(orderBy("a")).orderBy(orderBy("b"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void cannotUsePartialIndexWithMissingArrayContains() {
    Query q = query("collId").filter(filter("a", "array-contains", "a")).orderBy(orderBy("b"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.CONTAINS, "b", FieldIndex.Segment.Kind.ASCENDING);

    q = query("collId").orderBy(orderBy("b"));
    validateDoesNotServeTarget(
        q, "a", FieldIndex.Segment.Kind.CONTAINS, "b", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void cannotUseOverspecifiedIndex() {
    Query q = query("collId").orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void equalitiesWithDefaultOrder() {
    for (Query query : queriesWithEqualities) {
      validateServesTarget(query, "a", FieldIndex.Segment.Kind.ASCENDING);
      validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ASCENDING);
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
    }
  }

  @Test
  public void equalitiesWithAscendingOrder() {
    Stream<Query> queriesWithEqualitiesAndAscendingOrder =
        queriesWithEqualities.stream().map(q -> q.orderBy(orderBy("a", "asc")));

    queriesWithEqualitiesAndAscendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.ASCENDING);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ASCENDING);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void equalitiesWithDescendingOrder() {
    Stream<Query> queriesWithEqualitiesAndDescendingOrder =
        queriesWithEqualities.stream().map(q -> q.orderBy(orderBy("a", "desc")));

    queriesWithEqualitiesAndDescendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.ASCENDING);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ASCENDING);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void inequalitiesWithDefaultOrder() {
    for (Query query : queriesWithInequalities) {
      validateServesTarget(query, "a", FieldIndex.Segment.Kind.ASCENDING);
      validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ASCENDING);
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
    }
  }

  @Test
  public void inequalitiesWithAscendingOrder() {
    Stream<Query> queriesWithInequalitiesAndAscendingOrder =
        queriesWithInequalities.stream().map(q -> q.orderBy(orderBy("a", "asc")));

    queriesWithInequalitiesAndAscendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.ASCENDING);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ASCENDING);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void inequalitiesWithDescendingOrder() {
    Stream<Query> queriesWithInequalitiesAndDescendingOrder =
        queriesWithInequalities.stream().map(q -> q.orderBy(orderBy("a", "desc")));

    queriesWithInequalitiesAndDescendingOrder.forEach(
        query -> {
          validateServesTarget(query, "a", FieldIndex.Segment.Kind.DESCENDING);
          validateDoesNotServeTarget(query, "b", FieldIndex.Segment.Kind.ASCENDING);
          validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
        });
  }

  @Test
  public void inequalityUsesSingleFieldIndex() {
    Query q = query("collId").filter(filter("a", ">", 1)).filter(filter("a", "<", 10));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void inQueryUsesMergeJoin() {
    Query q =
        query("collId").filter(filter("a", "in", Arrays.asList(1, 2))).filter(filter("b", "==", 5));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(q, "b", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void validatesCollection() {
    {
      TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query("collId").toTarget());
      FieldIndex fieldIndex = fieldIndex("collId");
      assertDoesNotThrow(() -> targetIndexMatcher.servedByIndex(fieldIndex));
    }

    {
      TargetIndexMatcher targetIndexMatcher =
          new TargetIndexMatcher(new Query(path(""), "collId").toTarget());
      FieldIndex fieldIndex = fieldIndex("collId");
      assertDoesNotThrow(() -> targetIndexMatcher.servedByIndex(fieldIndex));
    }

    {
      TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query("collId2").toTarget());
      FieldIndex fieldIndex = fieldIndex("collId");
      expectError(
          () -> targetIndexMatcher.servedByIndex(fieldIndex),
          "INTERNAL ASSERTION FAILED: Collection IDs do not match");
    }
  }

  @Test
  public void withArrayContains() {
    for (Query query : queriesWithArrayContains) {
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.ASCENDING);
      validateDoesNotServeTarget(query, "a", FieldIndex.Segment.Kind.ASCENDING);
      validateServesTarget(query, "a", FieldIndex.Segment.Kind.CONTAINS);
    }
  }

  @Test
  public void testArrayContainsIsIndependent() {
    Query query =
        query("collId").filter(filter("value", "array-contains", "foo")).orderBy(orderBy("value"));
    validateServesTarget(
        query,
        "value",
        FieldIndex.Segment.Kind.CONTAINS,
        "value",
        FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        query,
        "value",
        FieldIndex.Segment.Kind.ASCENDING,
        "value",
        FieldIndex.Segment.Kind.CONTAINS);
  }

  @Test
  public void withArrayContainsAndOrderBy() {
    Query queriesMultipleFilters =
        query("collId")
            .filter(filter("a", "array-contains", "a"))
            .filter(filter("a", ">", "b"))
            .orderBy(orderBy("a", "asc"));
    validateServesTarget(
        queriesMultipleFilters,
        "a",
        FieldIndex.Segment.Kind.CONTAINS,
        "a",
        FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withEqualityAndDescendingOrder() {
    Query q = query("collId").filter(filter("a", "==", 1)).orderBy(orderBy("__name__", "desc"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "__name__", FieldIndex.Segment.Kind.DESCENDING);
  }

  @Test
  public void withMultipleEqualities() {
    Query queriesMultipleFilters =
        query("collId").filter(filter("a1", "==", "a")).filter(filter("a2", "==", "b"));
    validateServesTarget(
        queriesMultipleFilters,
        "a1",
        FieldIndex.Segment.Kind.ASCENDING,
        "a2",
        FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        queriesMultipleFilters,
        "a2",
        FieldIndex.Segment.Kind.ASCENDING,
        "a1",
        FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(
        queriesMultipleFilters,
        "a1",
        FieldIndex.Segment.Kind.ASCENDING,
        "a2",
        FieldIndex.Segment.Kind.ASCENDING,
        "a3",
        FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withMultipleEqualitiesAndInequality() {
    Query queriesMultipleFilters =
        query("collId")
            .filter(filter("equality1", "==", "a"))
            .filter(filter("equality2", "==", "b"))
            .filter(filter("inequality", ">=", "c"));
    validateServesTarget(
        queriesMultipleFilters,
        "equality1",
        FieldIndex.Segment.Kind.ASCENDING,
        "equality2",
        FieldIndex.Segment.Kind.ASCENDING,
        "inequality",
        FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        queriesMultipleFilters,
        "equality2",
        FieldIndex.Segment.Kind.ASCENDING,
        "equality1",
        FieldIndex.Segment.Kind.ASCENDING,
        "inequality",
        FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(
        queriesMultipleFilters,
        "equality2",
        FieldIndex.Segment.Kind.ASCENDING,
        "inequality",
        FieldIndex.Segment.Kind.ASCENDING,
        "equality1",
        FieldIndex.Segment.Kind.ASCENDING);

    queriesMultipleFilters =
        query("collId")
            .filter(filter("equality1", "==", "a"))
            .filter(filter("inequality", ">=", "c"))
            .filter(filter("equality2", "==", "b"));
    validateServesTarget(
        queriesMultipleFilters,
        "equality1",
        FieldIndex.Segment.Kind.ASCENDING,
        "equality2",
        FieldIndex.Segment.Kind.ASCENDING,
        "inequality",
        FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        queriesMultipleFilters,
        "equality2",
        FieldIndex.Segment.Kind.ASCENDING,
        "equality1",
        FieldIndex.Segment.Kind.ASCENDING,
        "inequality",
        FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(
        queriesMultipleFilters,
        "equality1",
        FieldIndex.Segment.Kind.ASCENDING,
        "inequality",
        FieldIndex.Segment.Kind.ASCENDING,
        "equality2",
        FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withOrderBy() {
    Query q = query("collId").orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(q, "a", FieldIndex.Segment.Kind.DESCENDING);

    q = query("collId").orderBy(orderBy("a", "desc"));
    validateDoesNotServeTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.DESCENDING);

    q = query("collId").orderBy(orderBy("a")).orderBy(orderBy("__name__"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "__name__", FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "__name__", FieldIndex.Segment.Kind.DESCENDING);
  }

  @Test
  public void withNotEquals() {
    Query q = query("collId").filter(filter("a", "!=", 1));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);

    q = query("collId").filter(filter("a", "!=", 1)).orderBy(orderBy("a")).orderBy(orderBy("b"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withMultipleFilters() {
    Query queriesMultipleFilters =
        query("collId").filter(filter("a", "==", "a")).filter(filter("b", ">", "b"));
    validateServesTarget(queriesMultipleFilters, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        queriesMultipleFilters,
        "a",
        FieldIndex.Segment.Kind.ASCENDING,
        "b",
        FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void multipleFiltersRequireMatchingPrefix() {
    Query queriesMultipleFilters =
        query("collId").filter(filter("a", "==", "a")).filter(filter("b", ">", "b"));

    validateServesTarget(queriesMultipleFilters, "b", FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(
        queriesMultipleFilters,
        "c",
        FieldIndex.Segment.Kind.ASCENDING,
        "a",
        FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withMultipleFiltersAndOrderBy() {
    Query queriesMultipleFilters =
        query("collId")
            .filter(filter("a1", "==", "a"))
            .filter(filter("a2", ">", "b"))
            .orderBy(orderBy("a2", "asc"));
    validateServesTarget(
        queriesMultipleFilters,
        "a1",
        FieldIndex.Segment.Kind.ASCENDING,
        "a2",
        FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withMultipleInequalities() {
    Query q =
        query("collId")
            .filter(filter("a", ">=", 1))
            .filter(filter("a", "==", 5))
            .filter(filter("a", "<=", 10));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withMultipleNotIn() {
    Query q =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .filter(filter("a", ">=", 2));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
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
        FieldIndex.Segment.Kind.ASCENDING,
        "bar",
        FieldIndex.Segment.Kind.DESCENDING,
        "__name__",
        FieldIndex.Segment.Kind.ASCENDING);
    validateDoesNotServeTarget(
        q,
        "fff",
        FieldIndex.Segment.Kind.ASCENDING,
        "__name__",
        FieldIndex.Segment.Kind.ASCENDING,
        "bar",
        FieldIndex.Segment.Kind.DESCENDING);

    q =
        query("collId")
            .orderBy(orderBy("foo"))
            .orderBy(orderBy("bar"))
            .orderBy(orderBy("__name__", "desc"));
    validateServesTarget(
        q,
        "foo",
        FieldIndex.Segment.Kind.ASCENDING,
        "bar",
        FieldIndex.Segment.Kind.ASCENDING,
        "__name__",
        FieldIndex.Segment.Kind.DESCENDING);
    validateDoesNotServeTarget(
        q,
        "foo",
        FieldIndex.Segment.Kind.ASCENDING,
        "__name__",
        FieldIndex.Segment.Kind.DESCENDING,
        "bar",
        FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withInAndNotIn() {
    Query q =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .filter(filter("b", "in", Arrays.asList(1, 2, 3)));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(q, "b", FieldIndex.Segment.Kind.ASCENDING);
    validateServesTarget(
        q, "b", FieldIndex.Segment.Kind.ASCENDING, "a", FieldIndex.Segment.Kind.ASCENDING);
    // If provided, equalities have to come first
    validateDoesNotServeTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
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
        FieldIndex.Segment.Kind.ASCENDING,
        "bar",
        FieldIndex.Segment.Kind.ASCENDING,
        "qux",
        FieldIndex.Segment.Kind.ASCENDING);

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
        FieldIndex.Segment.Kind.ASCENDING,
        "qqq",
        FieldIndex.Segment.Kind.ASCENDING,
        "ccc",
        FieldIndex.Segment.Kind.ASCENDING,
        "fff",
        FieldIndex.Segment.Kind.DESCENDING);
  }

  @Test
  public void withEqualsAndNotIn() {
    Query q =
        query("collId")
            .filter(filter("a", "==", 1))
            .filter(filter("b", "not-in", Arrays.asList(1, 2, 3)));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withInAndOrderBy() {
    Query q =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"));
    validateServesTarget(
        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withInAndOrderBySameField() {
    Query q =
        query("collId").filter(filter("a", "in", Arrays.asList(1, 2, 3))).orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
  }

  @Test
  public void withEqualityAndInequalityOnTheSameField() {
    validateServesTarget(
        query("collId").filter(filter("a", ">=", 5)).filter(filter("a", "==", 0)),
        "a",
        FieldIndex.Segment.Kind.ASCENDING);

    validateServesTarget(
        query("collId")
            .filter(filter("a", ">=", 5))
            .filter(filter("a", "==", 0))
            .orderBy(orderBy("a")),
        "a",
        FieldIndex.Segment.Kind.ASCENDING);

    validateServesTarget(
        query("collId")
            .filter(filter("a", ">=", 5))
            .filter(filter("a", "==", 0))
            .orderBy(orderBy("a"))
            .orderBy(orderBy(DocumentKey.KEY_FIELD_NAME)),
        "a",
        FieldIndex.Segment.Kind.ASCENDING);

    validateServesTarget(
        query("collId")
            .filter(filter("a", ">=", 5))
            .filter(filter("a", "==", 0))
            .orderBy(orderBy("a"))
            .orderBy(orderBy(DocumentKey.KEY_FIELD_NAME, "desc")),
        "a",
        FieldIndex.Segment.Kind.ASCENDING);

    validateServesTarget(
        query("collId")
            .filter(filter("a", ">=", 5))
            .filter(filter("a", "==", 0))
            .orderBy(orderBy("a", "asc"))
            .orderBy(orderBy("b", "asc")),
        "a",
        FieldIndex.Segment.Kind.ASCENDING,
        "b",
        FieldIndex.Segment.Kind.ASCENDING);

    validateServesTarget(
        query("collId")
            .filter(filter("a", ">=", 5))
            .filter(filter("a", "==", 0))
            .orderBy(orderBy("a", "desc"))
            .orderBy(orderBy(DocumentKey.KEY_FIELD_NAME, "desc")),
        "a",
        FieldIndex.Segment.Kind.DESCENDING);
  }

  private void validateServesTarget(
      Query query, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    FieldIndex expectedIndex = fieldIndex("collId", field, kind, fieldsAndKind);
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query.toTarget());
    assertTrue(targetIndexMatcher.servedByIndex(expectedIndex));
  }

  private void validateDoesNotServeTarget(
      Query query, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    FieldIndex expectedIndex = fieldIndex("collId", field, kind, fieldsAndKind);
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query.toTarget());
    assertFalse(targetIndexMatcher.servedByIndex(expectedIndex));
  }

  @Test
  public void testBuildTargetIndexWithQueriesWithEqualities() {
    for (Query query : queriesWithEqualities) {
      validateBuildTargetIndexCreateFullMatchIndex(query);
    }
  }

  @Test
  public void testBuildTargetIndexWithQueriesWithInequalities() {
    for (Query query : queriesWithInequalities) {
      validateBuildTargetIndexCreateFullMatchIndex(query);
    }
  }

  @Test
  public void testBuildTargetIndexWithQueriesWithArrayContains() {
    for (Query query : queriesWithArrayContains) {
      validateBuildTargetIndexCreateFullMatchIndex(query);
    }
  }

  @Test
  public void testBuildTargetIndexWithQueriesWithOrderBys() {
    for (Query query : queriesWithOrderBys) {
      validateBuildTargetIndexCreateFullMatchIndex(query);
    }
  }

  @Test
  public void testBuildTargetIndexWithInequalityUsesSingleFieldIndex() {
    Query query = query("collId").filter(filter("a", ">", 1)).filter(filter("a", "<", 10));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithCollection() {
    Query query = query("collId");
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithArrayContainsAndOrderBy() {
    Query query =
        query("collId")
            .filter(filter("a", "array-contains", "a"))
            .filter(filter("a", ">", "b"))
            .orderBy(orderBy("a", "asc"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithEqualityAndDescendingOrder() {
    Query query = query("collId").filter(filter("a", "==", 1)).orderBy(orderBy("__name__", "desc"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithMultipleEqualities() {
    Query query = query("collId").filter(filter("a1", "==", "a")).filter(filter("a2", "==", "b"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithMultipleEqualitiesAndInequality() {
    Query query =
        query("collId")
            .filter(filter("equality1", "==", "a"))
            .filter(filter("equality2", "==", "b"))
            .filter(filter("inequality", ">=", "c"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
    query =
        query("collId")
            .filter(filter("equality1", "==", "a"))
            .filter(filter("inequality", ">=", "c"))
            .filter(filter("equality2", "==", "b"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithMultipleFilters() {
    Query query = query("collId").filter(filter("a", "==", "a")).filter(filter("b", ">", "b"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
    query =
        query("collId")
            .filter(filter("a1", "==", "a"))
            .filter(filter("a2", ">", "b"))
            .orderBy(orderBy("a2", "asc"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
    query =
        query("collId")
            .filter(filter("a", ">=", 1))
            .filter(filter("a", "==", 5))
            .filter(filter("a", "<=", 10));
    validateBuildTargetIndexCreateFullMatchIndex(query);
    query =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .filter(filter("a", ">=", 2));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithMultipleOrderBys() {
    Query query =
        query("collId")
            .orderBy(orderBy("fff"))
            .orderBy(orderBy("bar", "desc"))
            .orderBy(orderBy("__name__"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
    query =
        query("collId")
            .orderBy(orderBy("foo"))
            .orderBy(orderBy("bar"))
            .orderBy(orderBy("__name__", "desc"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithInAndNotIn() {
    Query query =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .filter(filter("b", "in", Arrays.asList(1, 2, 3)));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithEqualityAndDifferentOrderBy() {
    Query query =
        query("collId")
            .filter(filter("foo", "==", ""))
            .filter(filter("bar", "==", ""))
            .orderBy(orderBy("qux"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
    query =
        query("collId")
            .filter(filter("aaa", "==", ""))
            .filter(filter("qqq", "==", ""))
            .filter(filter("ccc", "==", ""))
            .orderBy(orderBy("fff", "desc"))
            .orderBy(orderBy("bbb"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithEqualsAndNotIn() {
    Query query =
        query("collId")
            .filter(filter("a", "==", 1))
            .filter(filter("b", "not-in", Arrays.asList(1, 2, 3)));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithInAndOrderBy() {
    Query query =
        query("collId")
            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexWithInAndOrderBySameField() {
    Query query =
        query("collId").filter(filter("a", "in", Arrays.asList(1, 2, 3))).orderBy(orderBy("a"));
    validateBuildTargetIndexCreateFullMatchIndex(query);
  }

  @Test
  public void testBuildTargetIndexReturnsNullForMultipleInequality() {
    Query query = query("collId").filter(filter("a", ">=", 1)).filter(filter("b", "<=", 10));
    Target target = query.toTarget();
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(target);
    assertTrue(targetIndexMatcher.hasMultipleInequality());
    FieldIndex actualIndex = targetIndexMatcher.buildTargetIndex();
    assertNull(actualIndex);
  }

  private void validateBuildTargetIndexCreateFullMatchIndex(Query query) {
    Target target = query.toTarget();
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(target);
    assertFalse(targetIndexMatcher.hasMultipleInequality());
    FieldIndex actualIndex = targetIndexMatcher.buildTargetIndex();
    assertNotNull(actualIndex);
    assertTrue(targetIndexMatcher.servedByIndex(actualIndex));
    // Check the index created is a FULL MATCH index
    assertTrue(actualIndex.getSegments().size() >= target.getSegmentCount());
  }
}
