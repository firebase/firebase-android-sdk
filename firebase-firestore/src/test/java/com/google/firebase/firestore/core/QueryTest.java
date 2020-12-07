// Copyright 2018 Google LLC
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

import static com.google.firebase.firestore.model.DocumentKey.KEY_FIELD_NAME;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.testEquality;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.testutil.ComparatorTester;
import com.google.firebase.firestore.testutil.TestUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests Query */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class QueryTest {
  @Test
  public void testMatchesBasedDocumentKey() {
    ResourcePath queryPath = ResourcePath.fromString("rooms/eros/messages/1");
    Document doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));
    Document doc3 = doc("rooms/other/messages/1", 0, map("text", "msg3"));

    Query query = Query.atPath(queryPath);
    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
    assertFalse(query.matches(doc3));
  }

  @Test
  public void testMatchesShallowAncestorQuery() {
    ResourcePath queryPath = ResourcePath.fromString("rooms/eros/messages");
    Document doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    Document doc1meta = doc("rooms/eros/messages/1/meta/1", 0, map("meta", "meta-value"));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));
    Document doc3 = doc("rooms/other/messages/1", 0, map("text", "msg3"));

    Query query = Query.atPath(queryPath);
    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc1meta));
    assertTrue(query.matches(doc2));
    assertFalse(query.matches(doc3));
  }

  @Test
  public void testEmptyFieldsAreAllowedForQueries() {
    ResourcePath queryPath = ResourcePath.fromString("rooms/eros/messages");
    Document doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    Document doc2 = doc("rooms/eros/messages/2", 0, map());

    Query query = Query.atPath(queryPath).filter(filter("text", "==", "msg1"));
    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
  }

  @Test
  public void testPrimitiveValueFilter() {
    Query query1 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", ">=", 2));
    Query query2 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "<=", 2));

    Document doc1 = doc("collection/1", 0, map("sort", 1));
    Document doc2 = doc("collection/2", 0, map("sort", 2));
    Document doc3 = doc("collection/3", 0, map("sort", 3));
    Document doc4 = doc("collection/4", 0, map("sort", false));
    Document doc5 = doc("collection/5", 0, map("sort", "string"));

    assertFalse(query1.matches(doc1));
    assertTrue(query1.matches(doc2));
    assertTrue(query1.matches(doc3));
    assertFalse(query1.matches(doc4));
    assertFalse(query1.matches(doc5));

    assertTrue(query2.matches(doc1));
    assertTrue(query2.matches(doc2));
    assertFalse(query2.matches(doc3));
    assertFalse(query2.matches(doc4));
    assertFalse(query2.matches(doc5));
  }

  @Test
  public void testArrayContainsFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("array", "array-contains", 42L));

    // not an array
    Document document = doc("collection/1", 0, map("array", 1));
    assertFalse(query.matches(document));

    // empty array
    document = doc("collection/1", 0, map("array", asList()));
    assertFalse(query.matches(document));

    // array without element (and make sure it doesn't match in a nested field or a different field)
    document =
        doc(
            "collection/1",
            0,
            map("array", asList(41L, "42", map("a", 42L, "b", asList(42L))), "different", 42L));
    assertFalse(query.matches(document));

    // array with element
    document = doc("collection/1", 0, map("array", asList(1L, "2", 42L, map("a", 1L))));
    assertTrue(query.matches(document));
  }

  @Test
  public void testArrayContainsFiltersWithObjectValues() {
    // Search for arrays containing the object { a: [42] }
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("array", "array-contains", map("a", asList(42))));

    // array without element
    Document document =
        doc(
            "collection/1",
            0,
            map(
                "array",
                asList(
                    map("a", 42L),
                    map("a", asList(42L, 43L)),
                    map("b", asList(42L)),
                    map("a", asList(42L), "b", 42L))));
    assertFalse(query.matches(document));

    // array with element
    document = doc("collection/1", 0, map("array", asList(1L, "2", 42L, map("a", asList(42L)))));
    assertTrue(query.matches(document));
  }

  @Test
  public void testInFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "in", asList(12345)));

    Document document = doc("collection/1", 0, map("zip", 12345));
    assertTrue(query.matches(document));

    // Value matches in array.
    document = doc("collection/1", 0, map("zip", asList(12345)));
    assertFalse(query.matches(document));

    // Non-type match.
    document = doc("collection/1", 0, map("zip", "12345"));
    assertFalse(query.matches(document));

    // Nested match.
    document = doc("collection/1", 0, map("zip", asList("12345", map("zip", 12345))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testInFiltersWithObjectValues() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "in", asList(map("a", asList(42)))));

    // Containing object in array.
    Document document = doc("collection/1", 0, map("zip", asList(map("a", asList(42)))));
    assertFalse(query.matches(document));

    // Containing object.
    document = doc("collection/1", 0, map("zip", map("a", asList(42))));
    assertTrue(query.matches(document));
  }

  @Test
  public void testNotInFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "not-in", asList(12345)));

    // No match.
    Document document = doc("collection/1", 0, map("zip", 23456));
    assertTrue(query.matches(document));

    // Value matches in array.
    document = doc("collection/1", 0, map("zip", asList(12345)));
    assertTrue(query.matches(document));

    // Non-type match.
    document = doc("collection/1", 0, map("zip", "12345"));
    assertTrue(query.matches(document));

    // Nested match.
    document = doc("collection/1", 0, map("zip", asList("12345", map("zip", 12345))));
    assertTrue(query.matches(document));

    // Null match.
    document = doc("collection/1", 0, map("zip", null));
    assertTrue(query.matches(document));

    // NaN match.
    document = doc("collection/1", 0, map("zip", Double.NaN));
    assertTrue(query.matches(document));
    document = doc("collection/1", 0, map("zip", Float.NaN));
    assertTrue(query.matches(document));

    // Direct match
    document = doc("collection/1", 0, map("zip", 12345));
    assertFalse(query.matches(document));

    // Direct match
    document = doc("collection/1", 0, map("chip", 23456));
    assertFalse(query.matches(document));
  }

  @Test
  public void testNotInFiltersWithObjectValues() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "not-in", asList(map("a", asList(42)))));

    // Containing object in array.
    Document document = doc("collection/1", 0, map("zip", asList(map("a", asList(42)))));
    assertTrue(query.matches(document));

    // Containing object.
    document = doc("collection/1", 0, map("zip", map("a", asList(42))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testArrayContainsAnyFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "array-contains-any", asList(12345)));

    Document document = doc("collection/1", 0, map("zip", asList(12345)));
    assertTrue(query.matches(document));

    // Value matches in non-array.
    document = doc("collection/1", 0, map("zip", 12345));
    assertFalse(query.matches(document));

    // Non-type match.
    document = doc("collection/1", 0, map("zip", asList("12345")));
    assertFalse(query.matches(document));

    // Nested match.
    document = doc("collection/1", 0, map("zip", asList("12345", map("zip", asList(12345)))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testArrayContainsAnyFiltersWithObjectValues() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "array-contains-any", asList(map("a", asList(42)))));

    // Containing object in array.
    Document document = doc("collection/1", 0, map("zip", asList(map("a", asList(42)))));
    assertTrue(query.matches(document));

    // Containing object.
    document = doc("collection/1", 0, map("zip", map("a", asList(42))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testNaNFilter() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("sort", "==", Double.NaN));
    Document doc1 = doc("collection/1", 0, map("sort", Double.NaN));
    Document doc2 = doc("collection/2", 0, map("sort", 2));
    Document doc3 = doc("collection/3", 0, map("sort", 3.1));
    Document doc4 = doc("collection/4", 0, map("sort", false));
    Document doc5 = doc("collection/5", 0, map("sort", "string"));
    Document doc6 = doc("collection/6", 0, map("sort", null));

    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
    assertFalse(query.matches(doc3));
    assertFalse(query.matches(doc4));
    assertFalse(query.matches(doc5));
    assertFalse(query.matches(doc6));

    query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("sort", "!=", Double.NaN));
    assertFalse(query.matches(doc1));
    assertTrue(query.matches(doc2));
    assertTrue(query.matches(doc3));
    assertTrue(query.matches(doc4));
    assertTrue(query.matches(doc5));
    assertTrue(query.matches(doc6));
  }

  @Test
  public void testNullFilter() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "==", null));
    Document doc1 = doc("collection/1", 0, map("sort", null));
    Document doc2 = doc("collection/2", 0, map("sort", 2));
    Document doc3 = doc("collection/3", 0, map("sort", 3.1));
    Document doc4 = doc("collection/4", 0, map("sort", false));
    Document doc5 = doc("collection/5", 0, map("sort", "string"));
    Document doc6 = doc("collection/6", 0, map("sort", Double.NaN));

    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
    assertFalse(query.matches(doc3));
    assertFalse(query.matches(doc4));
    assertFalse(query.matches(doc5));
    assertFalse(query.matches(doc6));

    query = Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "!=", null));
    assertFalse(query.matches(doc1));
    assertTrue(query.matches(doc2));
    assertTrue(query.matches(doc3));
    assertTrue(query.matches(doc4));
    assertTrue(query.matches(doc5));
    assertTrue(query.matches(doc6));
  }

  @Test
  public void testComplexObjectFilters() {
    Query query1 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "<=", 2));
    Query query2 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", ">=", 2));

    Document doc1 = doc("collection/1", 0, map("sort", 2));
    Document doc2 = doc("collection/2", 0, map("sort", asList()));
    Document doc3 = doc("collection/3", 0, map("sort", asList(1)));
    Document doc4 = doc("collection/4", 0, map("sort", map("foo", 2)));
    Document doc5 = doc("collection/5", 0, map("sort", map("foo", "bar")));
    Document doc6 = doc("collection/6", 0, map("sort", map()));
    Document doc7 = doc("collection/7", 0, map("sort", asList(3, 1)));

    assertTrue(query1.matches(doc1));
    assertFalse(query1.matches(doc2));
    assertFalse(query1.matches(doc3));
    assertFalse(query1.matches(doc4));
    assertFalse(query1.matches(doc5));
    assertFalse(query1.matches(doc6));
    assertFalse(query1.matches(doc7));

    assertTrue(query2.matches(doc1));
    assertFalse(query2.matches(doc2));
    assertFalse(query2.matches(doc3));
    assertFalse(query2.matches(doc4));
    assertFalse(query2.matches(doc5));
    assertFalse(query2.matches(doc6));
    assertFalse(query2.matches(doc7));
  }

  @Test
  public void testDoesNotRemoveComplexObjectsWithOrderBy() {
    Query query = Query.atPath(ResourcePath.fromString("collection")).orderBy(orderBy("sort"));

    Document doc1 = doc("collection/1", 0, map("sort", 2));
    Document doc2 = doc("collection/2", 0, map("sort", asList()));
    Document doc3 = doc("collection/3", 0, map("sort", asList(1)));
    Document doc4 = doc("collection/4", 0, map("sort", map("foo", 2)));
    Document doc5 = doc("collection/5", 0, map("sort", map("foo", "bar")));

    assertTrue(query.matches(doc1));
    assertTrue(query.matches(doc2));
    assertTrue(query.matches(doc3));
    assertTrue(query.matches(doc4));
    assertTrue(query.matches(doc5));
  }

  @Test
  public void testFiltersArrays() {
    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));
    Document doc1 = doc("collection/doc", 0, map("tags", asList("foo", 1, true)));
    List<Filter> matchingFilters = asList(filter("tags", "==", asList("foo", 1, true)));

    List<Filter> nonMatchingFilters =
        asList(
            filter("tags", "==", "foo"),
            filter("tags", "==", asList("foo", 1)),
            filter("tags", "==", asList("foo", true, 1)));

    for (Filter filter : matchingFilters) {
      assertTrue(baseQuery.filter(filter).matches(doc1));
    }

    for (Filter filter : nonMatchingFilters) {
      assertFalse(baseQuery.filter(filter).matches(doc1));
    }
  }

  @Test
  public void testFiltersObjects() {
    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));
    Document doc1 =
        doc(
            "collection/doc",
            0,
            map("tags", map("foo", "foo", "a", 0, "b", true, "c", Double.NaN)));
    List<Filter> matchingFilters =
        asList(
            filter("tags", "==", map("foo", "foo", "a", 0, "b", true, "c", Double.NaN)),
            filter("tags", "==", map("b", true, "a", 0, "foo", "foo", "c", Double.NaN)),
            filter("tags.foo", "==", "foo"));

    List<Filter> nonMatchingFilters =
        asList(
            filter("tags", "==", "foo"),
            filter("tags", "==", map("foo", "foo", "a", 0, "b", true)));

    for (Filter filter : matchingFilters) {
      assertTrue(baseQuery.filter(filter).matches(doc1));
    }

    for (Filter filter : nonMatchingFilters) {
      assertFalse(baseQuery.filter(filter).matches(doc1));
    }
  }

  @Test
  public void testSortsDocuments() {
    Query query = Query.atPath(ResourcePath.fromString("collection")).orderBy(orderBy("sort"));
    new ComparatorTester(query.comparator())
        .addEqualityGroup(doc("collection/1", 0, map("sort", null)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", false)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", true)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", 1)))
        .addEqualityGroup(doc("collection/2", 0, map("sort", 1))) // by key
        .addEqualityGroup(doc("collection/3", 0, map("sort", 1))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort", 1.9)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", 2)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", 2.1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "a")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "ab")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "b")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", ref("collection/id1"))))
        .testCompare();
  }

  @Test
  public void testSortsWithMultipleFields() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .orderBy(orderBy("sort1"))
            .orderBy(orderBy("sort2"));

    new ComparatorTester(query.comparator())
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 3)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 3)))
        .testCompare();
  }

  @Test
  public void testSortsDescending() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .orderBy(orderBy("sort1", "desc"))
            .orderBy(orderBy("sort2", "desc"));

    new ComparatorTester(query.comparator())
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 3)))
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 2, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 3)))
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 1, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 1)))
        .testCompare();
  }

  @Test
  public void testHashCode() {
    Query q1a =
        Query.atPath(ResourcePath.fromString("foo"))
            .filter(filter("i1", "<", 2))
            .filter(filter("i2", "==", 3));

    // TODO uncomment this when hashcode does not depend on filter order.
    /*
    Query q1b =
        Query.atPath(ResourcePath.fromString("foo"))
            .filter(filter("i2", "==", 3))
            .filter(filter("i1", "<", 2));
    */

    Query q2a = Query.atPath(ResourcePath.fromString("foo"));
    Query q2b = Query.atPath(ResourcePath.fromString("foo"));

    Query q3a = Query.atPath(ResourcePath.fromString("foo/bar"));
    Query q3b = Query.atPath(ResourcePath.fromString("foo/bar"));

    Query q4a =
        Query.atPath(ResourcePath.fromString("foo"))
            .orderBy(orderBy("foo"))
            .orderBy(orderBy("bar"));
    Query q4b =
        Query.atPath(ResourcePath.fromString("foo"))
            .orderBy(orderBy("foo"))
            .orderBy(orderBy("bar"));

    Query q5a =
        Query.atPath(ResourcePath.fromString("foo"))
            .orderBy(orderBy("bar"))
            .orderBy(orderBy("foo"));

    Query q6a =
        Query.atPath(ResourcePath.fromString("foo"))
            .filter(filter("bar", ">", 2))
            .orderBy(orderBy("bar"));

    Query q7a = Query.atPath(ResourcePath.fromString("foo")).limitToFirst(10);

    // TODO: Add test cases with{Lower,Upper}Bound once cursors are implemented.
    testEquality(
        asList(
            asList(q1a.hashCode()),
            asList(q2a.hashCode(), q2b.hashCode()),
            asList(q3a.hashCode(), q3b.hashCode()),
            asList(q4a.hashCode(), q4b.hashCode()),
            asList(q5a.hashCode()),
            asList(q6a.hashCode()),
            asList(q7a.hashCode())));
  }

  @Test
  public void testImplicitOrderBy() {
    Query baseQuery = Query.atPath(path("foo"));
    // Default is ascending
    assertEquals(asList(orderBy(KEY_FIELD_NAME, "asc")), baseQuery.getOrderBy());

    // Explicit key ordering is respected
    assertEquals(
        asList(orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery.orderBy(orderBy(KEY_FIELD_NAME, "asc")).getOrderBy());
    assertEquals(
        asList(orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery.orderBy(orderBy(KEY_FIELD_NAME, "desc")).getOrderBy());
    assertEquals(
        asList(orderBy("foo"), orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery.orderBy(orderBy("foo")).orderBy(orderBy(KEY_FIELD_NAME, "asc")).getOrderBy());
    assertEquals(
        asList(orderBy("foo"), orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery.orderBy(orderBy("foo")).orderBy(orderBy(KEY_FIELD_NAME, "desc")).getOrderBy());

    // Inequality filters add order bys
    assertEquals(
        asList(orderBy("foo"), orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery.filter(filter("foo", "<", 5)).getOrderBy());

    // Descending order by applies to implicit key ordering
    assertEquals(
        asList(orderBy("foo", "desc"), orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery.orderBy(orderBy("foo", "desc")).getOrderBy());
    assertEquals(
        asList(orderBy("foo", "asc"), orderBy("bar", "desc"), orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery.orderBy(orderBy("foo", "asc")).orderBy(orderBy("bar", "desc")).getOrderBy());
    assertEquals(
        asList(orderBy("foo", "desc"), orderBy("bar", "asc"), orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery.orderBy(orderBy("foo", "desc")).orderBy(orderBy("bar", "asc")).getOrderBy());
  }

  @Test
  public void testMatchesAllDocuments() {
    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));
    assertTrue(baseQuery.matchesAllDocuments());

    Query query = baseQuery.orderBy(orderBy("__name__"));
    assertTrue(query.matchesAllDocuments());

    query = baseQuery.orderBy(orderBy("foo"));
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.filter(filter("foo", "==", "bar"));
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.limitToFirst(1);
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.startAt(new Bound(Collections.emptyList(), true));
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.endAt(new Bound(Collections.emptyList(), true));
    assertFalse(query.matchesAllDocuments());
  }

  @Test
  public void testCanonicalIdsAreStable() {
    // This test aims to ensure that we do not break canonical IDs, as they are used as keys in
    // the TargetCache.

    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));

    assertCanonicalId(baseQuery, "collection|f:|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", ">", "a")), "collection|f:a>a|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "<=", new GeoPoint(90.0, -90.0))),
        "collection|f:a<=geo(90.0,-90.0)|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "<=", new Timestamp(60, 3000))),
        "collection|f:a<=time(60,3000)|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", ">=", Blob.fromBytes(new byte[] {1, 2, 3}))),
        "collection|f:a>=010203|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "==", Arrays.asList(1, 2, 3))),
        "collection|f:a==[1,2,3]|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "!=", Arrays.asList(1, 2, 3))),
        "collection|f:a!=[1,2,3]|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "==", Double.NaN)), "collection|f:a==NaN|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("__name__", "==", ref("collection/id"))),
        "collection|f:__name__==collection/id|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "==", map("a", "b", "inner", map("d", "c")))),
        "collection|f:a=={a:b,inner:{d:c}}|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "in", Arrays.asList(1, 2, 3))),
        "collection|f:ain[1,2,3]|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "not-in", Arrays.asList(1, 2, 3))),
        "collection|f:anot_in[1,2,3]|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "array-contains-any", Arrays.asList(1, 2, 3))),
        "collection|f:aarray_contains_any[1,2,3]|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "array-contains", "a")),
        "collection|f:aarray_containsa|ob:__name__asc");
    assertCanonicalId(baseQuery.orderBy(orderBy("a")), "collection|f:|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery
            .orderBy(orderBy("a"))
            .startAt(
                new Bound(
                    Arrays.asList(TestUtil.wrap("foo"), TestUtil.wrap(Arrays.asList(1, 2, 3))),
                    true)),
        "collection|f:|ob:aasc__name__asc|lb:b:foo,[1,2,3]");
    assertCanonicalId(
        baseQuery
            .orderBy(orderBy("a"))
            .endAt(
                new Bound(
                    Arrays.asList(TestUtil.wrap("foo"), TestUtil.wrap(Arrays.asList(1, 2, 3))),
                    false)),
        "collection|f:|ob:aasc__name__asc|ub:a:foo,[1,2,3]");
    assertCanonicalId(baseQuery.limitToFirst(5), "collection|f:|ob:__name__asc|l:5");
    assertCanonicalId(baseQuery.limitToLast(5), "collection|f:|ob:__name__desc|l:5");
  }

  private void assertCanonicalId(Query query, String expectedCanonicalId) {
    assertEquals(expectedCanonicalId, query.toTarget().getCanonicalId());
  }
}
