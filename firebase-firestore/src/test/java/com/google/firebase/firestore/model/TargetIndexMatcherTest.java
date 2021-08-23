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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
  public void cannotUseOverspecifiedIndex() {
    Query q = query("collId").orderBy(orderBy("a"));
    validateServesTarget(q, "a", FieldIndex.Segment.Kind.ORDERED);
    validateDoesNotServeTarget(
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
      TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query("collId").toTarget());
      FieldIndex fieldIndex = new FieldIndex("collId");
      assertDoesNotThrow(() -> targetIndexMatcher.servedByIndex(fieldIndex));
    }

    {
      TargetIndexMatcher targetIndexMatcher =
          new TargetIndexMatcher(new Query(path(""), "collId").toTarget());
      FieldIndex fieldIndex = new FieldIndex("collId");
      assertDoesNotThrow(() -> targetIndexMatcher.servedByIndex(fieldIndex));
    }

    {
      TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query("collId2").toTarget());
      FieldIndex fieldIndex = new FieldIndex("collId");
      expectError(
          () -> targetIndexMatcher.servedByIndex(fieldIndex),
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
    validateServesTarget(
        queriesMultipleFilters,
        "a",
        FieldIndex.Segment.Kind.CONTAINS,
        "a",
        FieldIndex.Segment.Kind.ORDERED);
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
    validateServesTarget(queriesMultipleFilters, "a", FieldIndex.Segment.Kind.ORDERED);
    validateServesTarget(
        queriesMultipleFilters,
        "a",
        FieldIndex.Segment.Kind.ORDERED,
        "b",
        FieldIndex.Segment.Kind.ORDERED);
  }

  @Test
  public void multipleFiltersRequireMatchingPrefix() {
    Query queriesMultipleFilters =
        query("collId").filter(filter("a", "==", "a")).filter(filter("b", ">", "b"));

    validateServesTarget(queriesMultipleFilters, "b", FieldIndex.Segment.Kind.ORDERED);
    validateDoesNotServeTarget(
        queriesMultipleFilters,
        "c",
        FieldIndex.Segment.Kind.ORDERED,
        "a",
        FieldIndex.Segment.Kind.ORDERED);
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
        FieldIndex.Segment.Kind.ORDERED,
        "a2",
        FieldIndex.Segment.Kind.ORDERED);
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
    // The field index can be used regardless of the order of segments since we only use the orderBy
    // clause to filter out documents that do not contain field values for the specified fields. The
    // ordering itself is done during View computation.
    validateServesTarget(
        q,
        "fff",
        FieldIndex.Segment.Kind.ORDERED,
        "__name__",
        FieldIndex.Segment.Kind.ORDERED,
        "bar",
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
    validateServesTarget(
        q,
        "foo",
        FieldIndex.Segment.Kind.ORDERED,
        "__name__",
        FieldIndex.Segment.Kind.ORDERED,
        "bar",
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
    FieldIndex expectedIndex = buildFieldIndex(field, kind, fieldsAndKind);
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query.toTarget());
    assertTrue(targetIndexMatcher.servedByIndex(expectedIndex));
  }

  private void validateDoesNotServeTarget(
      Query query, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    FieldIndex expectedIndex = buildFieldIndex(field, kind, fieldsAndKind);
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query.toTarget());
    assertFalse(targetIndexMatcher.servedByIndex(expectedIndex));
  }

  private FieldIndex buildFieldIndex(
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
