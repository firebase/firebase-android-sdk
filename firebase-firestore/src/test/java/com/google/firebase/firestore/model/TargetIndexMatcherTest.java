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

import static com.google.firebase.firestore.testutil.TestUtil.andFilter;
import static com.google.firebase.firestore.testutil.TestUtil.assertDoesNotThrow;
import static com.google.firebase.firestore.testutil.TestUtil.expectError;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.core.CompositeFilter;
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
    Stream<Query> queriesWithEqualitiesAndDescendingOrder =
        queriesWithEqualities.stream().map(q -> q.orderBy(orderBy("a", "asc")));

    queriesWithEqualitiesAndDescendingOrder.forEach(
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
    Stream<Query> queriesWithInequalitiesAndDescendingOrder =
        queriesWithInequalities.stream().map(q -> q.orderBy(orderBy("a", "asc")));

    queriesWithInequalitiesAndDescendingOrder.forEach(
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
      TargetIndexMatcher targetIndexMatcher =
          new TargetIndexMatcher(query("collId").toTarget(), null);
      FieldIndex fieldIndex = fieldIndex("collId");
      assertDoesNotThrow(() -> targetIndexMatcher.servedByIndex(fieldIndex));
    }

    {
      TargetIndexMatcher targetIndexMatcher =
          new TargetIndexMatcher(new Query(path(""), "collId").toTarget(), null);
      FieldIndex fieldIndex = fieldIndex("collId");
      assertDoesNotThrow(() -> targetIndexMatcher.servedByIndex(fieldIndex));
    }

    {
      TargetIndexMatcher targetIndexMatcher =
          new TargetIndexMatcher(query("collId2").toTarget(), null);
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
    // TODO(ehsann): This is an invalid query. Query validation code throws an exception for this
    // test.
    //    Query q =
    //        query("collId")
    //            .filter(filter("a", "not-in", Arrays.asList(1, 2, 3)))
    //            .filter(filter("b", "in", Arrays.asList(1, 2, 3)));
    //    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ASCENDING);
    //    validateServesTarget(q, "b", FieldIndex.Segment.Kind.ASCENDING);
    //    validateServesTarget(
    //        q, "b", FieldIndex.Segment.Kind.ASCENDING, "a", FieldIndex.Segment.Kind.ASCENDING);
    //    // If provided, equalities have to come first
    //    validateDoesNotServeTarget(
    //        q, "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);
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

  private void validateServesTarget(
      Query query, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    CompositeFilter filter = andFilter(query.getFilters());
    assertTrue(filter.isFlatAndFilter());
    FieldIndex expectedIndex = fieldIndex("collId", field, kind, fieldsAndKind);
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query.toTarget(), filter);
    assertTrue(targetIndexMatcher.servedByIndex(expectedIndex));
  }

  private void validateDoesNotServeTarget(
      Query query, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    CompositeFilter filter = andFilter(query.getFilters());
    assertTrue(filter.isFlatAndFilter());
    FieldIndex expectedIndex = fieldIndex("collId", field, kind, fieldsAndKind);
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query.toTarget(), filter);
    assertFalse(targetIndexMatcher.servedByIndex(expectedIndex));
  }
}
